package learning;

import core.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Quiz {

    // A record to hold all information about a single question
    public record QuizQuestion(String questionText, String correctAnswer, String category, String hint, String originalData) {}

    // A record to hold a single high score entry
    private record HighScore(String name, int score) implements Comparable<HighScore> {
        @Override
        public int compareTo(HighScore other) {
            return Integer.compare(other.score(), this.score());
        }
    }

    private static final Random random = new Random();
    private static final String REPORT_FILE = "quiz_report.csv";
    private static final String HIGH_SCORE_FILE = "high_scores.txt";
    private static final int MAX_HIGH_SCORES = 10;
    private static final int TIME_LIMIT_SECONDS = 40;

    // Enums are public so other packages can import them
    public enum Difficulty { EASY, MEDIUM, HARD }
    public enum Category { ALL, MOLAR_MASS, BALANCING, ORGANIC, PERIODIC_TABLE, REACTION_PREDICTION }

    // Entry point for CLI
    public static void startCLI(Scanner sc, String username) {
        System.out.println("\n=== Chemistry Quiz ===");
        QuizProgress progress = new QuizProgress(username); // load progress in constructor

        while (true) {
            progress.displaySummary();

            System.out.println("\nQuiz Menu:");
            System.out.println("  [1] Start New Quiz");
            System.out.println("  [2] View High Scores");
            System.out.println("  [3] Return to Main Menu");
            System.out.print("Select an option: ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1" -> runNewQuizSession(sc, progress, username);
                case "2" -> displayHighScores();
                case "3" -> { return; }
                default -> System.out.println("❌ Invalid choice.");
            }
        }
    }

    // Run one quiz session
    private static void runNewQuizSession(Scanner sc, QuizProgress progress, String username) {
        Difficulty difficulty = chooseDifficulty(sc);
        Category category = chooseCategory(sc);
        int numQuestions = chooseNumberOfQuestions(sc);
        boolean isTimedMode = chooseTimedMode(sc);

        int score = 0;
        long totalTimeTakenMillis = 0;

        for (int i = 0; i < numQuestions; i++) {
            System.out.println("\n--------------------");
            System.out.printf("Question %d of %d:%n", i + 1, numQuestions);
            QuizQuestion q = generateQuestion(difficulty, category);
            if (q == null) {
                System.out.println("Could not generate a question, skipping.");
                continue;
            }
            System.out.println(q.questionText());

            String userAnswer = "";
            long startTime = System.currentTimeMillis();

            if (isTimedMode) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(sc::nextLine);
                try {
                    System.out.printf("You have %d seconds... GO!%nYour answer: ", TIME_LIMIT_SECONDS);
                    userAnswer = future.get(TIME_LIMIT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    System.out.println("\n⏰ Time's up!");
                    userAnswer = "TIMEOUT";
                } catch (Exception e) {
                    future.cancel(true);
                    userAnswer = "ERROR";
                } finally {
                    executor.shutdownNow();
                }
            } else {
                while (true) {
                    System.out.print("Your answer (or type 'hint'): ");
                    userAnswer = sc.nextLine();
                    if (userAnswer.equalsIgnoreCase("hint")) {
                        System.out.println("💡 HINT: " + q.hint());
                    } else {
                        break;
                    }
                }
            }

            long endTime = System.currentTimeMillis();
            totalTimeTakenMillis += (endTime - startTime);

            if (userAnswer.equalsIgnoreCase("TIMEOUT") || userAnswer.equalsIgnoreCase("ERROR")) {
                System.out.println("❌ Incorrect. The correct answer was: " + q.correctAnswer());
            } else if (checkAnswer(userAnswer, q.correctAnswer(), q.category())) {
                System.out.println("✅ Correct!");
                score++;
                progress.updateStat("total_correct", 1);
            } else {
                if (q.category().equalsIgnoreCase("Balancing")) {
                    String feedback = getBalancingFeedback(q.originalData(), userAnswer);
                    System.out.println(feedback);
                }
                System.out.println("❌ Incorrect. The correct answer was: " + q.correctAnswer());
            }
            progress.updateStat("total_answered", 1);
        }

        double avgTime = (numQuestions > 0) ? (totalTimeTakenMillis / 1000.0) / numQuestions : 0;
        System.out.println("\n====================");
        System.out.println("Quiz Complete!");
        System.out.printf("Your final score is: %d / %d%n", score, numQuestions);
        System.out.printf("Average time per question: %.2f seconds%n", avgTime);

        int difficultyMultiplier = (difficulty == Difficulty.EASY) ? 1 : (difficulty == Difficulty.MEDIUM) ? 3 : 5;
        int finalScore = score * 10 * difficultyMultiplier;
        if (isTimedMode && score > 0) {
            finalScore += Math.max(0, (TIME_LIMIT_SECONDS * numQuestions) - (totalTimeTakenMillis / 1000));
        }
        System.out.printf("Your calculated score for this session is: %d points!%n", finalScore);

        List<HighScore> highScores = loadHighScores();
        if (highScores.size() < MAX_HIGH_SCORES || finalScore > highScores.get(highScores.size() - 1).score()) {
            System.out.print("🏆 New High Score! Enter your initials (max 3 characters): ");
            String name = sc.nextLine().trim();
            if (name.isEmpty()) name = "???";
            name = name.substring(0, Math.min(name.length(), 3)).toUpperCase();
            highScores.add(new HighScore(name, finalScore));
            Collections.sort(highScores);
            saveHighScores(highScores);
            displayHighScores();
        }

        exportQuizResult(difficulty, category, score, numQuestions);

        long sessionSeconds = totalTimeTakenMillis / 1000L;
        core.ProgressManager.updateQuizSession(username, score, numQuestions, sessionSeconds);

        progress.saveProgress();
    }

    // Load and save high scores
    private static List<HighScore> loadHighScores() {
        List<HighScore> scores = new ArrayList<>();
        try (Scanner fileScanner = new Scanner(new File(HIGH_SCORE_FILE))) {
            while (fileScanner.hasNextLine()) {
                String[] parts = fileScanner.nextLine().split(",");
                if (parts.length == 2) {
                    scores.add(new HighScore(parts[0], Integer.parseInt(parts[1])));
                }
            }
        } catch (FileNotFoundException e) {
            // File doesn't exist yet, ignore
        } catch (Exception e) {
            System.out.println("❌ Error reading high scores: " + e.getMessage());
        }
        Collections.sort(scores);
        return scores;
    }

    private static void saveHighScores(List<HighScore> scores) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(HIGH_SCORE_FILE))) {
            for (int i = 0; i < Math.min(scores.size(), MAX_HIGH_SCORES); i++) {
                HighScore hs = scores.get(i);
                writer.printf("%s,%d%n", hs.name(), hs.score());
            }
        } catch (IOException e) {
            System.out.println("❌ Could not save high scores: " + e.getMessage());
        }
    }

    private static void displayHighScores() {
        List<HighScore> scores = loadHighScores();
        System.out.println("\n--- 🏆 High Scores 🏆 ---");
        if (scores.isEmpty()) {
            System.out.println("No high scores yet. Be the first!");
        } else {
            System.out.printf("%-4s %-10s %s%n", "Rank", "Name", "Score");
            System.out.println("-------------------------");
            for (int i = 0; i < scores.size(); i++) {
                System.out.printf("#%-3d %-10s %d%n", i + 1, scores.get(i).name(), scores.get(i).score());
            }
        }
        System.out.println("-------------------------");
    }

    // Generate a new quiz question
    public static QuizQuestion generateQuestion(Difficulty difficulty, Category category) {
        List<Category> availableCategories = new ArrayList<>();
        if (category == Category.ALL) {
            availableCategories.add(Category.PERIODIC_TABLE);
            if (difficulty == Difficulty.EASY) availableCategories.add(Category.ORGANIC);
            if (difficulty == Difficulty.MEDIUM) availableCategories.add(Category.MOLAR_MASS);
            if (difficulty == Difficulty.HARD) availableCategories.addAll(List.of(Category.BALANCING, Category.REACTION_PREDICTION));
        } else {
            availableCategories.add(category);
        }
        if (availableCategories.isEmpty()) availableCategories.add(Category.PERIODIC_TABLE);

        Category chosenCategory = availableCategories.get(random.nextInt(availableCategories.size()));

        switch (chosenCategory) {
            case MOLAR_MASS: {
                String formula = difficulty == Difficulty.HARD ? "Ca(OH)2" : "H2O";
                double mass = MolarMassCalculator.computeMass(EquationBalancer.parseCompound(formula), false);
                return new QuizQuestion(
                        String.format("What is the molar mass of %s? (g/mol, 2 decimals)", formula),
                        String.format("%.2f", mass),
                        "Molar Mass",
                        "Sum the atomic masses of all atoms in the formula.",
                        formula
                );
            }
            case BALANCING: {
                Reaction reactionToBalance = ReactionDatabase.getRandomReaction();
                String unbalancedEq = reactionToBalance.equation();
                String balancedEq = EquationBalancer.balance(unbalancedEq, true);
                var coeffs = EquationBalancer.parseCoefficients(balancedEq);
                return new QuizQuestion(
                        String.format("What are the coefficients for: %s ? (e.g., 2,1,2)", unbalancedEq),
                        String.join(",", coeffs.values().stream().map(String::valueOf).collect(Collectors.toList())),
                        "Balancing",
                        "Ensure the number of atoms for each element is the same on both sides.",
                        unbalancedEq
                );
            }
            case ORGANIC: {
                Map<String, String> organicExamples = difficulty == Difficulty.HARD
                        ? Map.of("CH3CH(CH3)CH3", "2-Methylpropane")
                        : Map.of("CH3CH2OH", "Alcohol");
                List<String> keys = new ArrayList<>(organicExamples.keySet());
                String orgFormula = keys.get(random.nextInt(keys.size()));
                String question = difficulty == Difficulty.HARD ? "What is the IUPAC name of %s?" : "What is the functional group in %s?";
                return new QuizQuestion(
                        String.format(question, orgFormula),
                        organicExamples.get(orgFormula),
                        "Organic Chemistry",
                        difficulty == Difficulty.HARD ? "Find the longest carbon chain and name the branch." : "Look for groups like -OH, -COOH, etc.",
                        orgFormula
                );
            }
            case REACTION_PREDICTION: {
                Reaction reactionTemplate = null;
                int attempts = 0;
                while (reactionTemplate == null || !reactionTemplate.type().equals("Single Displacement")) {
                    reactionTemplate = ReactionDatabase.getRandomReaction();
                    if (attempts++ > 50) return generateQuestion(difficulty, Category.BALANCING);
                }
                String unbalancedReactants = reactionTemplate.equation().split("->")[0].trim();
                String fullyBalancedEq = EquationBalancer.balance(reactionTemplate.equation(), true);
                String productsSide = fullyBalancedEq.split("->")[1].trim();
                String[] products = Arrays.stream(productsSide.split("\\+"))
                        .map(s -> s.trim().replaceAll("^[0-9]+\\s*", ""))
                        .toArray(String[]::new);
                if (products.length != 2) return generateQuestion(difficulty, category);
                String hintProduct = products[0], answerProduct = products[1];
                if (random.nextBoolean()) {
                    hintProduct = products[1];
                    answerProduct = products[0];
                }
                return new QuizQuestion(
                        String.format("What is the missing product?  %s -> %s + ?", unbalancedReactants, hintProduct),
                        answerProduct,
                        "Reaction Prediction",
                        "This is a Single Displacement reaction. A lone element swaps with an element in the compound.",
                        reactionTemplate.equation()
                );
            }
            default: { // PERIODIC_TABLE
                PeriodicTable.Element pte = PeriodicTable.getRandomElement();
                return new QuizQuestion(
                        String.format("What is the name of the element with the symbol '%s'?", pte.symbol()),
                        pte.name(),
                        "Periodic Table",
                        "This element is in period " + pte.period() + ".",
                        pte.symbol()
                );
            }
        }
    }

    // Check user answer
    private static boolean checkAnswer(String userAnswer, String correctAnswer, String category) {
        userAnswer = userAnswer.trim();
        correctAnswer = correctAnswer.trim();
        if (category.equalsIgnoreCase("Molar Mass")) {
            try {
                return Math.abs(Double.parseDouble(userAnswer) - Double.parseDouble(correctAnswer)) < 0.02;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (category.equalsIgnoreCase("Balancing")) {
            return userAnswer.replaceAll("\\s", "").equals(correctAnswer.replaceAll("\\s", ""));
        } else {
            return userAnswer.equalsIgnoreCase(correctAnswer);
        }
    }

    // Balancing feedback
    private static String getBalancingFeedback(String unbalancedEq, String userAnswer) {
        try {
            List<Integer> userCoeffs = Arrays.stream(userAnswer.replaceAll("\\s", "").split(","))
                    .map(Integer::parseInt).collect(Collectors.toList());
            String[] sides = unbalancedEq.split("->");
            String[] reactantFormulas = sides[0].trim().split("\\+");
            String[] productFormulas = sides[1].trim().split("\\+");
            List<String> allFormulas = new ArrayList<>(Arrays.asList(reactantFormulas));
            allFormulas.addAll(Arrays.asList(productFormulas));
            if (userCoeffs.size() != allFormulas.size())
                return String.format("💡 Feedback: The equation has %d compounds, but you provided %d coefficients.", allFormulas.size(), userCoeffs.size());

            Map<String, Integer> reactantCounts = new HashMap<>(), productCounts = new HashMap<>();
            for (int i = 0; i < allFormulas.size(); i++) {
                Map<String, Integer> elementCounts = EquationBalancer.parseCompound(allFormulas.get(i).trim());
                Map<String, Integer> sideMap = (i < reactantFormulas.length) ? reactantCounts : productCounts;
                for (Map.Entry<String, Integer> entry : elementCounts.entrySet()) {
                    sideMap.put(entry.getKey(), sideMap.getOrDefault(entry.getKey(), 0) + entry.getValue() * userCoeffs.get(i));
                }
            }
            Set<String> allElements = new HashSet<>(reactantCounts.keySet());
            allElements.addAll(productCounts.keySet());
            for (String element : allElements) {
                int reactantCount = reactantCounts.getOrDefault(element, 0);
                int productCount = productCounts.getOrDefault(element, 0);
                if (reactantCount != productCount)
                    return String.format("💡 Feedback: Check your count for the element '%s'. Reactant side has %d, but product side has %d.", element, reactantCount, productCount);
            }
            return "💡 Feedback: The atoms seem to balance, but the ratio might not be the simplest possible.";
        } catch (Exception e) {
            return "💡 Feedback: Your answer couldn't be analyzed. Ensure coefficients are numbers separated by commas (e.g., 2,1,2).";
        }
    }

    // Save quiz results to CSV
    private static void exportQuizResult(Difficulty difficulty, Category category, int score, int total) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        double accuracy = total > 0 ? ((double) score / total) * 100.0 : 0;
        String details = String.format("Difficulty: %s, Category: %s, Score: %d/%d (%.2f%%)", difficulty, category, score, total, accuracy);

        try {
            boolean fileExists = new File(REPORT_FILE).exists();
            try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
                if (!fileExists) {
                    pw.println("Timestamp,Type,Details");
                }
                pw.printf("%s,Quiz Session,\"%s\"%n", ts, details);
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to write quiz result to CSV: " + e.getMessage());
        }
    }

    // Menu helpers
    private static Difficulty chooseDifficulty(Scanner sc) {
        while (true) {
            System.out.print("Choose difficulty ([e]asy, [m]edium, [h]ard): ");
            String input = sc.nextLine().trim().toLowerCase();
            if (input.startsWith("e")) return Difficulty.EASY;
            if (input.startsWith("m")) return Difficulty.MEDIUM;
            if (input.startsWith("h")) return Difficulty.HARD;
            System.out.println("❌ Invalid choice.");
        }
    }

    private static Category chooseCategory(Scanner sc) {
        while (true) {
            System.out.print("Choose category ([a]ll, [m]olar mass, [b]alancing, [o]rganic, [p]eriodic table, [r]eaction prediction): ");
            String input = sc.nextLine().trim().toLowerCase();
            if (input.startsWith("a")) return Category.ALL;
            if (input.startsWith("m")) return Category.MOLAR_MASS;
            if (input.startsWith("b")) return Category.BALANCING;
            if (input.startsWith("o")) return Category.ORGANIC;
            if (input.startsWith("p")) return Category.PERIODIC_TABLE;
            if (input.startsWith("r")) return Category.REACTION_PREDICTION;
            System.out.println("❌ Invalid choice.");
        }
    }

    private static int chooseNumberOfQuestions(Scanner sc) {
        while (true) {
            System.out.print("How many questions would you like to try? ");
            try {
                int num = Integer.parseInt(sc.nextLine().trim());
                if (num > 0) return num;
                System.out.println("❌ Please enter a positive number.");
            } catch (NumberFormatException e) {
                System.out.println("❌ Invalid input. Please enter a number.");
            }
        }
    }

    private static boolean chooseTimedMode(Scanner sc) {
        while (true) {
            System.out.print("Play in Timed Mode? (y/n): ");
            String input = sc.nextLine().trim().toLowerCase();
            if (input.startsWith("y")) return true;
            if (input.startsWith("n")) return false;
            System.out.println("❌ Invalid choice.");
        }
    }
}
