package ui.views;

import core.*;
import learning.QuizProgress;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Full GUI Quiz view.
 * Usage: QuizView.getView(username, onFinishCallback)
 * - onFinishCallback is invoked (on JavaFX thread) when the quiz completes so the "My Learning" dashboard can refresh.
 */
public class QuizView {

    private static final String REPORT_FILE = "quiz_report.csv";
    private static final String HIGH_SCORE_FILE = "high_scores.txt";
    private static final int TIME_LIMIT_SECONDS = 40;

    // Public factory
    public static Node getView(String username, Runnable onFinish) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Chemistry Quiz");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        // LEFT: controls
        VBox left = new VBox(10);
        left.setPadding(new Insets(10));
        left.setPrefWidth(360);

        ChoiceBox<String> difficultyChoice = new ChoiceBox<>();
        difficultyChoice.getItems().addAll("EASY", "MEDIUM", "HARD");
        difficultyChoice.setValue("MEDIUM");

        ChoiceBox<String> categoryChoice = new ChoiceBox<>();
        categoryChoice.getItems().addAll("ALL", "MOLAR_MASS", "BALANCING", "ORGANIC", "PERIODIC_TABLE", "REACTION_PREDICTION");
        categoryChoice.setValue("ALL");

        Spinner<Integer> numQuestionsSpinner = new Spinner<>(1, 50, 5);
        CheckBox timedModeBox = new CheckBox("Timed mode (40s per question)");

        Button startBtn = new Button("Start Quiz");
        Button viewHighscoresBtn = new Button("View High Scores");
        Button openCsvBtn = new Button("Open CSV Log");

        left.getChildren().addAll(new Label("Difficulty:"), difficultyChoice,
                new Label("Category:"), categoryChoice,
                new Label("Number of questions:"), numQuestionsSpinner,
                timedModeBox,
                startBtn, viewHighscoresBtn, openCsvBtn);

        // CENTER: question area
        VBox center = new VBox(10);
        center.setPadding(new Insets(10));
        Label statusLabel = new Label("Ready. Press Start to begin a quiz.");
        statusLabel.setWrapText(true);

        TextArea questionArea = new TextArea();
        questionArea.setWrapText(true);
        questionArea.setEditable(false);
        questionArea.setPrefRowCount(4);

        TextField answerField = new TextField();
        answerField.setPromptText("Type your answer here");

        HBox controlRow = new HBox(8);
        Button hintBtn = new Button("Hint");
        Button submitBtn = new Button("Submit");
        Button nextBtn = new Button("Next");
        nextBtn.setDisable(true);
        controlRow.getChildren().addAll(hintBtn, submitBtn, nextBtn);

        Label timerLabel = new Label();
        timerLabel.setStyle("-fx-font-weight:bold;");
        Label scoreLabel = new Label("Score: 0 / 0");

        center.getChildren().addAll(statusLabel, questionArea, answerField, controlRow, timerLabel, scoreLabel);

        // BOTTOM: session log
        TextArea resultsArea = new TextArea();
        resultsArea.setEditable(false);
        resultsArea.setWrapText(true);
        resultsArea.setPrefRowCount(8);

        Button finishBtn = new Button("Finish Quiz");
        finishBtn.setDisable(true);
        Button saveCsvBtn = new Button("Save Session to CSV");

        HBox bottomRow = new HBox(8, finishBtn, saveCsvBtn);
        bottomRow.setPadding(new Insets(8));
        VBox bottomBox = new VBox(8, new Label("Session Log"), resultsArea, bottomRow);
        bottomBox.setPadding(new Insets(8));

        root.setLeft(left);
        root.setCenter(center);
        root.setBottom(bottomBox);

        // Internal state
        final List<Question> questions = new ArrayList<>();
        final int[] idx = { -1 };
        final int[] score = { 0 };
        final long[] totalTimeMillis = { 0 };
        final Timer[] countdownTimer = { null };
        final long[] currentQuestionStart = { 0 };
        final QuizProgress quizProgress = new QuizProgress(username);

        Runnable updateScoreLabel = () -> scoreLabel.setText(String.format("Score: %d / %d", score[0], Math.max(0, idx[0] + 1)));

        startBtn.setOnAction(e -> {
            startBtn.setDisable(true);
            resultsArea.clear();
            questions.clear();
            score[0] = 0;
            idx[0] = -1;
            totalTimeMillis[0] = 0;
            statusLabel.setText("Generating questions...");
            boolean timed = timedModeBox.isSelected();
            String difficulty = difficultyChoice.getValue();
            String category = categoryChoice.getValue();
            int n = numQuestionsSpinner.getValue();

            CompletableFuture.runAsync(() -> {
                // Generate questions (local generator replicating CLI logic)
                for (int i = 0; i < n; i++) {
                    Question q = generateQuestion(difficulty, category);
                    if (q == null) q = new Question("Could not generate question", "N/A", "GENERAL", "");
                    synchronized (questions) { questions.add(q); }
                }
            }).whenComplete((v, ex) -> Platform.runLater(() -> {
                startBtn.setDisable(false);
                if (questions.isEmpty()) {
                    statusLabel.setText("Failed to create questions.");
                    return;
                }
                idx[0] = 0;
                displayQuestion(questions.get(idx[0]), questionArea, answerField, timerLabel, currentQuestionStart, countdownTimer, timed, statusLabel);
                updateScoreLabel.run();
                statusLabel.setText(String.format("Question %d of %d", idx[0] + 1, questions.size()));
                submitBtn.setDisable(false);
                nextBtn.setDisable(true);
                finishBtn.setDisable(false);
            }));
        });

        submitBtn.setOnAction(e -> {
            if (idx[0] < 0 || idx[0] >= questions.size()) return;
            Question q = questions.get(idx[0]);
            long now = System.currentTimeMillis();
            totalTimeMillis[0] += (now - currentQuestionStart[0]);
            String answer = answerField.getText().trim();

            boolean correct = checkAnswer(answer, q.correctAnswer, q.category);
            if (correct) {
                resultsArea.appendText(String.format("✔ Q%d Correct: %s%n", idx[0] + 1, q.questionText));
                score[0]++; quizProgress.updateStat("total_correct", 1);
            } else {
                resultsArea.appendText(String.format("✖ Q%d Incorrect. Correct: %s%n", idx[0] + 1, q.correctAnswer));
                if ("Balancing".equalsIgnoreCase(q.category) && q.originalData != null && !q.originalData.isEmpty()) {
                    resultsArea.appendText(getBalancingFeedback(q.originalData, answer) + "\n");
                }
            }
            quizProgress.updateStat("total_answered", 1);
            updateScoreLabel.run();

            // stop timer if any
            if (countdownTimer[0] != null) {
                countdownTimer[0].cancel();
                countdownTimer[0] = null;
            }
            submitBtn.setDisable(true);
            nextBtn.setDisable(false);
        });

        nextBtn.setOnAction(e -> {
            if (idx[0] + 1 < questions.size()) {
                idx[0] += 1;
                displayQuestion(questions.get(idx[0]), questionArea, answerField, timerLabel, currentQuestionStart, countdownTimer, timedModeBox.isSelected(), statusLabel);
                statusLabel.setText(String.format("Question %d of %d", idx[0] + 1, questions.size()));
                submitBtn.setDisable(false);
                nextBtn.setDisable(true);
            } else {
                statusLabel.setText("All questions shown. Click Finish.");
                submitBtn.setDisable(true);
                nextBtn.setDisable(true);
            }
        });

        hintBtn.setOnAction(e -> {
            if (idx[0] >= 0 && idx[0] < questions.size()) {
                Question q = questions.get(idx[0]);
                showInfoDialog(root.getScene().getWindow(), "Hint", q.hint);
            }
        });

        finishBtn.setOnAction(e -> {
            finishBtn.setDisable(true);
            if (countdownTimer[0] != null) {
                countdownTimer[0].cancel();
                countdownTimer[0] = null;
            }
            int totalQ = questions.size();
            int earned = score[0];
            String diff = difficultyChoice.getValue();
            int multiplier = "EASY".equals(diff) ? 1 : "MEDIUM".equals(diff) ? 3 : 5;
            int points = earned * 10 * multiplier;
            long sessionSeconds = totalTimeMillis[0] / 1000L;
            if (timedModeBox.isSelected() && earned > 0) {
                points += Math.max(0, (TIME_LIMIT_SECONDS * totalQ) - (int) sessionSeconds);
            }

            List<HighScore> hs = loadHighScores();
            boolean newHigh = hs.size() < 10 || hs.isEmpty() || points > hs.get(hs.size() - 1).score();
            if (newHigh) {
                TextInputDialog d = new TextInputDialog();
                d.setTitle("New High Score!");
                d.setHeaderText("You scored " + points + " points!");
                d.setContentText("Enter 1-3 initials:");
                Optional<String> res = d.showAndWait();
                String initials = res.map(s -> s.trim().toUpperCase()).orElse("???");
                if (initials.isEmpty()) initials = "???";
                initials = initials.substring(0, Math.min(3, initials.length()));
                hs.add(new HighScore(initials, points));
                Collections.sort(hs);
                saveHighScores(hs);
            }

            exportQuizResult(diff, categoryChoice.getValue(), earned, totalQ);

            try { core.ProgressManager.updateQuizSession(username, earned, totalQ, sessionSeconds); } catch (Throwable ignored) { }

            quizProgress.addStudyTime(totalTimeMillis[0]);
            quizProgress.saveProgress();

            String summary = String.format("Quiz finished: %d/%d. Points: %d. Session time: %d s\nLog appended to %s",
                    earned, totalQ, points, sessionSeconds, REPORT_FILE);
            resultsArea.appendText("\n" + summary + "\n");
            showInfoDialog(root.getScene().getWindow(), "Quiz Summary", summary);
            statusLabel.setText("Quiz finished. Start a new quiz to try again.");
            startBtn.setDisable(false);

            // Refresh main learning dashboard via callback (onFinish)
            if (onFinish != null) {
                try { onFinish.run(); } catch (Throwable ignored) { }
            }
        });

        saveCsvBtn.setOnAction(e -> showInfoDialog(root.getScene().getWindow(), "CSV", "Quiz session entries are appended to: " + REPORT_FILE));

        viewHighscoresBtn.setOnAction(e -> showInfoDialog(root.getScene().getWindow(), "High Scores", highScoresAsString(loadHighScores())));

        openCsvBtn.setOnAction(e -> {
            try {
                File f = new File(REPORT_FILE);
                if (!f.exists()) { showInfoDialog(root.getScene().getWindow(), "CSV", "No CSV file found yet."); return; }
                Desktop.getDesktop().open(f);
            } catch (Exception ex) { showInfoDialog(root.getScene().getWindow(), "Open CSV", "Could not open CSV: " + ex.getMessage()); }
        });

        return root;
    }

    // ------------------- Helper methods -------------------

    private static void displayQuestion(Question q, TextArea questionArea, TextField answerField,
                                        Label timerLabel, long[] questionStart, Timer[] countdownTimer,
                                        boolean timed, Label statusLabel) {
        questionArea.setText(q.questionText);
        answerField.clear();
        timerLabel.setText("");
        questionStart[0] = System.currentTimeMillis();

        // Timed mode: simple Timer to update label and auto-timeout
        if (countdownTimer[0] != null) {
            countdownTimer[0].cancel();
            countdownTimer[0] = null;
        }
        if (timed) {
            Timer t = new Timer(true);
            countdownTimer[0] = t;
            final int[] remaining = { TIME_LIMIT_SECONDS };
            t.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> timerLabel.setText("Time left: " + remaining[0] + " s"));
                    remaining[0]--;
                    if (remaining[0] < 0) {
                        t.cancel();
                        Platform.runLater(() -> {
                            // simulate timeout - disable submit, allow next
                            statusLabel.setText("Time's up for this question.");
                        });
                    }
                }
            }, 0, 1000);
        }
    }

    // Generate a question (similar to CLI's generator). This is internal to the full GUI view.
    private static Question generateQuestion(String difficulty, String category) {
        try {
            switch (category) {
                case "MOLAR_MASS": {
                    String formula = "H2O";
                    if ("HARD".equals(difficulty)) formula = "Ca(OH)2";
                    double mass = MolarMassCalculator.computeMass(EquationBalancer.parseCompound(formula), false);
                    return new Question(String.format("What is the molar mass of %s? (g/mol, 2 decimals)", formula),
                            String.format("%.2f", mass), "Molar Mass", "Sum the atomic masses of atoms", formula);
                }
                case "BALANCING": {
                    core.Reaction r = ReactionDatabase.getRandomReaction();
                    String unbalancedEq = r.equation();
                    String balanced = EquationBalancer.balance(unbalancedEq, true);
                    Map<String, Integer> coeffs = EquationBalancer.parseCoefficients(balanced);
                    String answer = String.join(",", coeffs.values().stream().map(String::valueOf).toList());
                    return new Question(String.format("What are the coefficients for: %s ? (e.g., 2,1,2)", unbalancedEq),
                            answer, "Balancing", "Ensure atoms same count both sides", unbalancedEq);
                }
                case "ORGANIC": {
                    String formula = "CH3CH2OH";
                    String answer = "Alcohol";
                    return new Question(String.format("What is the functional group in %s?", formula),
                            answer, "Organic", "Look for groups like -OH, -COOH", formula);
                }
                case "REACTION_PREDICTION": {
                    core.Reaction r = ReactionDatabase.getRandomReaction();
                    String eq = r.equation();
                    String balanced = EquationBalancer.balance(eq, true);
                    String[] parts = balanced.split("->");
                    if (parts.length < 2) return null;
                    String[] products = parts[1].split("\\+");
                    if (products.length < 2) return null;
                    String p0 = products[0].trim().replaceAll("^[0-9]+\\s*", "");
                    String p1 = products[1].trim().replaceAll("^[0-9]+\\s*", "");
                    return new Question(String.format("What is the missing product? %s -> %s + ?", parts[0].trim(), p0),
                            p1, "Reaction Prediction", "Single displacement style", eq);
                }
                default: { // PERIODIC_TABLE or ALL fallback
                    PeriodicTable.Element el = PeriodicTable.getRandomElement();
                    return new Question(String.format("What is the name of the element with the symbol '%s'?", el.symbol()),
                            el.name(), "Periodic Table", "Check the periodic table", el.symbol());
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean checkAnswer(String userAnswer, String correctAnswer, String category) {
        if (userAnswer == null) return false;
        userAnswer = userAnswer.trim();
        correctAnswer = correctAnswer.trim();
        if (category.equalsIgnoreCase("Molar Mass")) {
            try { return Math.abs(Double.parseDouble(userAnswer) - Double.parseDouble(correctAnswer)) < 0.02; }
            catch (NumberFormatException ex) { return false; }
        } else if (category.equalsIgnoreCase("Balancing")) {
            return userAnswer.replaceAll("\\s", "").equals(correctAnswer.replaceAll("\\s", ""));
        } else {
            return userAnswer.equalsIgnoreCase(correctAnswer);
        }
    }

    private static String getBalancingFeedback(String unbalancedEq, String userAnswer) {
        try {
            List<Integer> userCoeffs = Arrays.stream(userAnswer.replaceAll("\\s", "").split(",")).map(Integer::parseInt).toList();
            String[] sides = unbalancedEq.split("->");
            String[] reactantFormulas = sides[0].trim().split("\\+");
            String[] productFormulas = sides[1].trim().split("\\+");
            List<String> allFormulas = new ArrayList<>(Arrays.asList(reactantFormulas));
            allFormulas.addAll(Arrays.asList(productFormulas));
            if (userCoeffs.size() != allFormulas.size()) return String.format("💡 Feedback: The equation has %d compounds, but you provided %d coefficients.", allFormulas.size(), userCoeffs.size());
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
                if (reactantCount != productCount) return String.format("💡 Feedback: Check your count for the element '%s'. Reactant side has %d, but product side has %d.", element, reactantCount, productCount);
            }
            return "💡 Feedback: The atoms seem to balance, but the ratio might not be simplest.";
        } catch (Exception e) {
            return "💡 Feedback: Could not analyze your coefficients. Use numbers separated by commas (e.g., 2,1,2).";
        }
    }

    private static void exportQuizResult(String difficulty, String category, int score, int total) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        double accuracy = total > 0 ? ((double) score / total) * 100.0 : 0;
        String details = String.format("Difficulty: %s, Category: %s, Score: %d/%d (%.2f%%)", difficulty, category, score, total, accuracy);
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            boolean fileExists = new File(REPORT_FILE).exists() && new File(REPORT_FILE).length() > 0;
            if (!fileExists) { pw.println("Timestamp,Type,Details"); }
            pw.printf("%s,Quiz Session,\"%s\"%n", ts, details);
        } catch (Exception e) {
            System.err.println("Failed to write quiz result to CSV: " + e.getMessage());
        }
    }

    // High score helpers
    private static List<HighScore> loadHighScores() {
        List<HighScore> scores = new ArrayList<>();
        try (Scanner fileScanner = new Scanner(new File(HIGH_SCORE_FILE))) {
            while (fileScanner.hasNextLine()) {
                String[] parts = fileScanner.nextLine().split(",");
                if (parts.length == 2) scores.add(new HighScore(parts[0], Integer.parseInt(parts[1])));
            }
        } catch (FileNotFoundException e) { /* ignore */ }
        Collections.sort(scores);
        return scores;
    }

    private static void saveHighScores(List<HighScore> scores) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(HIGH_SCORE_FILE))) {
            for (int i = 0; i < Math.min(scores.size(), 10); i++) {
                HighScore hs = scores.get(i);
                writer.printf("%s,%d%n", hs.name(), hs.score());
            }
        } catch (IOException e) { System.err.println("Could not save high scores: " + e.getMessage()); }
    }

    private static String highScoresAsString(List<HighScore> list) {
        if (list.isEmpty()) return "No high scores yet.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(String.format("#%d: %s — %d%n", i + 1, list.get(i).name(), list.get(i).score()));
        }
        return sb.toString();
    }

    // Simple dialogs
    private static void showInfoDialog(Window owner, String title, String text) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.initOwner(owner);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(text);
            a.showAndWait();
        });
    }

    // Small internal classes/records
    private static class Question {
        String questionText;
        String correctAnswer;
        String category;
        String hint;
        String originalData;
        Question(String q, String a, String c, String h) { this(q,a,c,h,""); }
        Question(String q, String a, String c, String h, String orig) { questionText=q; correctAnswer=a; category=c; hint=h; originalData=orig; }
    }

    private static record HighScore(String name, int score) implements Comparable<HighScore> {
        @Override public int compareTo(HighScore other) { return Integer.compare(other.score(), this.score()); }
    }
}

