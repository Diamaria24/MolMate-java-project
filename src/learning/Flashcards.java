package learning;
import core.*;
import core.PeriodicTable;
import org.json.simple.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class Flashcards {

    public record Flashcard(String id, String front, String back, String category, String explanation) {}
    private static final String REPORT_FILE = "flashcard_log.csv";

    public static class Deck {
        private final String name;
        private final List<Flashcard> cards;
        public Deck(String name) { this.name = name; this.cards = new ArrayList<>(); }
        public void addCard(Flashcard card) { this.cards.add(card); }
        public String getName() { return name; }
        public List<Flashcard> getCards() { return cards; }
    }

    public static class DeckManager {
        public static Deck getElementsDeck() {
            Deck deck = new Deck("Periodic Table Elements");
            PeriodicTable.loadElements();
            for (PeriodicTable.Element e : PeriodicTable.getAllElements()) {
                deck.addCard(new Flashcard("ELEM_NAME_" + e.symbol(), e.symbol(), e.name(), "Element Name", String.format("%s is element #%d.", e.name(), e.atomicNumber())));
                deck.addCard(new Flashcard("ELEM_SYMBOL_" + e.symbol(), e.name(), e.symbol(), "Element Symbol", String.format("The symbol for %s is %s.", e.name(), e.symbol())));
                deck.addCard(new Flashcard("ELEM_MASS_" + e.symbol(), e.name(), String.format("%.3f", e.atomicMass()), "Element Molar Mass", "Molar mass is the mass of one mole of a substance."));
            }
            return deck;
        }

        public static Deck getPolyatomicIonsDeck() {
            Deck deck = new Deck("Common Polyatomic Ions");
            Map<String, String> ions = new LinkedHashMap<>();
            ions.put("NH4", "Ammonium"); ions.put("OH", "Hydroxide"); ions.put("NO3", "Nitrate");
            ions.put("NO2", "Nitrite"); ions.put("CO3", "Carbonate"); ions.put("HCO3", "Bicarbonate");
            ions.put("SO4", "Sulfate"); ions.put("SO3", "Sulfite"); ions.put("PO4", "Phosphate");
            ions.put("ClO3", "Chlorate"); ions.put("ClO4", "Perchlorate"); ions.put("C2H3O2", "Acetate");
            ions.put("CN", "Cyanide"); ions.put("MnO4", "Permanganate"); ions.put("Cr2O7", "Dichromate");
            for (Map.Entry<String, String> entry : ions.entrySet()) {
                deck.addCard(new Flashcard("ION_FORMULA_" + entry.getKey(), entry.getValue(), entry.getKey(), "Ion Formula", String.format("The ion with the formula %s is named %s.", entry.getKey(), entry.getValue())));
                deck.addCard(new Flashcard("ION_NAME_" + entry.getKey(), entry.getKey(), entry.getValue(), "Ion Name", String.format("The ion named %s has the formula %s.", entry.getValue(), entry.getKey())));
            }
            return deck;
        }

        public static Deck getOrganicGroupsDeck() {
            Deck deck = new Deck("Organic Functional Groups");
            deck.addCard(new Flashcard("ORG_ALCOHOL", "CH3CH2OH", "Alcohol", "Functional Group", "The -OH group is characteristic of alcohols."));
            deck.addCard(new Flashcard("ORG_ACID", "CH3COOH", "Carboxylic Acid", "Functional Group", "The -COOH group is characteristic of carboxylic acids."));
            deck.addCard(new Flashcard("ORG_KETONE", "CH3COCH3", "Ketone", "Functional Group", "A C=O group not at the end of a chain indicates a ketone."));
            deck.addCard(new Flashcard("ORG_ALDEHYDE", "CH3CHO", "Aldehyde", "Functional Group", "A C=O group at the end of a carbon chain indicates an aldehyde."));
            deck.addCard(new Flashcard("ORG_ETHER", "CH3OCH3", "Ether", "Functional Group", "An oxygen atom bonded to two carbon atoms indicates an ether."));
            deck.addCard(new Flashcard("ORG_AMINE", "CH3NH2", "Amine", "Functional Group", "A nitrogen atom bonded to carbon and hydrogen atoms indicates an amine."));
            return deck;
        }

        public static Deck getAcidsAndBasesDeck() {
            Deck deck = new Deck("Common Acids & Bases");
            Map<String, String> compounds = new LinkedHashMap<>();
            compounds.put("HCl", "Hydrochloric Acid"); compounds.put("H2SO4", "Sulfuric Acid");
            compounds.put("HNO3", "Nitric Acid"); compounds.put("CH3COOH", "Acetic Acid");
            compounds.put("H2CO3", "Carbonic Acid"); compounds.put("H3PO4", "Phosphoric Acid");
            compounds.put("NaOH", "Sodium Hydroxide"); compounds.put("KOH", "Potassium Hydroxide");
            compounds.put("Ca(OH)2", "Calcium Hydroxide"); compounds.put("NH3", "Ammonia");
            for(Map.Entry<String, String> entry : compounds.entrySet()) {
                deck.addCard(new Flashcard("ACIDBASE_FORMULA_"+entry.getKey(), entry.getValue(), entry.getKey(), "Acid/Base Formula", String.format("The formula for %s.", entry.getValue())));
                deck.addCard(new Flashcard("ACIDBASE_NAME_"+entry.getKey(), entry.getKey(), entry.getValue(), "Acid/Base Name", String.format("The name for %s.", entry.getKey())));
            }
            return deck;
        }
    }

    public static void startCLI(Scanner sc, String username) {
        System.out.println("\n=== Flashcards Mode ===");
        FlashcardProgress progress = new FlashcardProgress(username);
        while (true) {
            progress.loadProgress();
            progress.displaySummary();
            System.out.println("\nPlease choose a deck to study:");
            System.out.println("  [1] Periodic Table Elements");
            System.out.println("  [2] Common Polyatomic Ions");
            System.out.println("  [3] Organic Functional Groups");
            System.out.println("  [4] Common Acids & Bases");
            System.out.println("  [5] Return to Main Menu");
            System.out.print("Select an option: ");
            String choice = sc.nextLine().trim();
            Deck currentDeck = null;
            switch (choice) {
                case "1" -> currentDeck = DeckManager.getElementsDeck();
                case "2" -> currentDeck = DeckManager.getPolyatomicIonsDeck();
                case "3" -> currentDeck = DeckManager.getOrganicGroupsDeck();
                case "4" -> currentDeck = DeckManager.getAcidsAndBasesDeck();
                case "5" -> { return; }
                default -> { System.out.println("❌ Invalid choice."); continue; }
            }
            if (currentDeck != null) {
                runStudySession(sc, currentDeck, progress, username);
                progress.saveProgress();
                System.out.println("✅ Progress saved!");
            }
        }
    }

    private static void runStudySession(Scanner sc, Deck deck, FlashcardProgress progress, String username) {
        long sessionStartMillis = System.currentTimeMillis();

        if (deck.getCards().isEmpty()) { System.out.println("\n⚠️ This deck has no cards to study yet."); return; }
        boolean reverseMode = chooseMode(sc, deck.getName());

        int availableCards = progress.getAvailableCardCount(deck.getCards());
        if (availableCards == 0) {
            System.out.println("\n🎉 No new or due cards in this deck for now. Great job!");
            offerQuickReview(sc, deck);
            return;
        }

        int numCardsToStudy;
        while (true) {
            System.out.printf("How many cards would you like to study in this session? (Max: %d available): ", availableCards);
            try {
                numCardsToStudy = Integer.parseInt(sc.nextLine().trim());
                if (numCardsToStudy > 0) {
                    numCardsToStudy = Math.min(numCardsToStudy, availableCards);
                    break;
                }
                System.out.println("❌ Please enter a positive number.");
            } catch (NumberFormatException e) { System.out.println("❌ Invalid input. Please enter a number."); }
        }

        int reviewedInSession = 0;
        for (int i = 0; i < numCardsToStudy; i++) {
            Flashcard card = progress.getNextCardToReview(deck.getCards());
            if (card == null) {
                System.out.println("\n🎉 You've finished all available cards for this session!");
                offerQuickReview(sc, deck);
                break;
            }

            String front = reverseMode ? card.back() : card.front();
            String back = reverseMode ? card.front() : card.back();
            System.out.printf("\n--- Card %d of %d ---\n", i + 1, numCardsToStudy);
            System.out.println("Front: " + front);
            System.out.print("Your answer: ");
            String userAnswer = sc.nextLine();

            String correctness;
            if (checkAnswer(userAnswer, back, card.category())) {
                System.out.println("\n✅ Correct!");
                correctness = "Correct";
            } else {
                System.out.println("\n❌ Not quite. The correct answer is: " + back);
                System.out.println("   Keep trying! " + card.explanation());
                correctness = "Incorrect";
            }
            reviewedInSession++;

            String ratingChoice = getRatingChoice(sc);
            if (ratingChoice.equals("e")) {
                exportStudyLog(username, deck.getName(), card, correctness, "Exited");
                break;
            }

            JSONObject state = progress.getCardState(card.id());
            int currentLevel = (state != null && state.get("level") != null) ? ((Long) state.get("level")).intValue() : 0;
            int newLevel = currentLevel;
            String ratingForLog = "Good";

            if (ratingChoice.equals("1")) { // Hard
                newLevel--;
                ratingForLog = "Hard";
            } else if (ratingChoice.equals("3")) { // Easy
                newLevel++;
                ratingForLog = "Easy";
            }

            progress.updateCardState(card.id(), newLevel);
            exportStudyLog(username, deck.getName(), card, correctness, ratingForLog);
        }

        if (reviewedInSession > 0) {
            System.out.printf("\nSession complete! You reviewed %d cards.\n", reviewedInSession);
            progress.incrementReviewedCount(reviewedInSession);
        }
        long sessionEndMillis = System.currentTimeMillis();
        long sessionSeconds = (sessionEndMillis - sessionStartMillis) / 1000L;

        core.ProgressManager.updateFlashcardSession(username, reviewedInSession, sessionSeconds);

    }

    /** --- NEW: Asks the user if they want to do a quick review --- */
    private static void offerQuickReview(Scanner sc, Deck deck) {
        while (true) {
            System.out.print("Would you like to do a quick review of all cards in this deck (this won't affect SRS progress)? (y/n): ");
            String choice = sc.nextLine().trim().toLowerCase();
            if (choice.startsWith("y")) {
                runQuickReview(sc, deck);
                break;
            } else if (choice.startsWith("n")) {
                break;
            } else {
                System.out.println("❌ Invalid choice.");
            }
        }
    }

    /** --- NEW: A simple study loop that shows all cards randomly without SRS --- */
    private static void runQuickReview(Scanner sc, Deck deck) {
        System.out.println("\n--- Quick Review Mode ---");
        boolean reverseMode = chooseMode(sc, deck.getName());

        List<Flashcard> allCards = new ArrayList<>(deck.getCards());
        Collections.shuffle(allCards);

        for (int i = 0; i < allCards.size(); i++) {
            Flashcard card = allCards.get(i);
            String front = reverseMode ? card.back() : card.front();
            String back = reverseMode ? card.front() : card.back();

            System.out.printf("\n--- Reviewing Card %d of %d ---\n", i + 1, allCards.size());
            System.out.println("Front: " + front);
            System.out.print("\n... Press Enter to reveal answer ...");
            sc.nextLine();
            System.out.println("\nBack: " + back);

            System.out.print("\nContinue reviewing? ([Y]es/[n]o to exit): ");
            String continueChoice = sc.nextLine().trim().toLowerCase();
            if (continueChoice.startsWith("n")) {
                break;
            }
        }
        System.out.println("\nQuick review finished!");
    }

    private static void exportStudyLog(String username, String deckName, Flashcard card, String correctness, String userRating) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        boolean fileExists = new File(REPORT_FILE).exists() && new File(REPORT_FILE).length() > 0;
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            if (!fileExists) {
                pw.println("Timestamp,Username,Deck,CardFront,CardBack,Correctness,UserRating");
            }
            pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    ts, username, deckName, card.front().replace("\"", "\"\""), card.back().replace("\"", "\"\""), correctness, userRating);
        } catch (Exception e) {
            System.out.println("❌ Failed to write to flashcard log: " + e.getMessage());
        }
    }

    private static boolean checkAnswer(String userAnswer, String correctAnswer, String category) {
        userAnswer = userAnswer.trim();
        correctAnswer = correctAnswer.trim();
        if (category.equals("Element Molar Mass")) {
            try { return Math.abs(Double.parseDouble(userAnswer) - Double.parseDouble(correctAnswer)) < 0.02; }
            catch (NumberFormatException e) { return false; }
        } else {
            return userAnswer.equalsIgnoreCase(correctAnswer);
        }
    }

    private static boolean chooseMode(Scanner sc, String deckName) {
        while (true) {
            System.out.printf("Study '%s' with [F]ront-to-Back or [B]ack-to-Front? ", deckName);
            String mode = sc.nextLine().trim().toLowerCase();
            if (mode.startsWith("f")) return false;
            if (mode.startsWith("b")) return true;
            System.out.println("❌ Invalid choice.");
        }
    }

    private static String getRatingChoice(Scanner sc) {
        while (true) {
            System.out.print("How did you do? [1] Hard, [2] Good, [3] Easy, or [e]xit session: ");
            String choice = sc.nextLine().trim().toLowerCase();
            if (List.of("1", "2", "3", "e").contains(choice)) {
                return choice;
            }
            System.out.println("❌ Invalid choice.");
        }
    }
}
