package ui.views;

import learning.FlashcardProgress;
import learning.Flashcards;
import learning.Flashcards.Flashcard;
import learning.Flashcards.Deck;
import learning.Flashcards.DeckManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * UI view for flashcards study mode.
 * - Shows decks (Periodic Table, Polyatomic Ions, Organic groups, Acids/Bases).
 * - Allows selecting number of cards to study.
 * - Presents one card at a time in a flip-card UI (click to flip).
 * - After user reveals/answers, use rating buttons to update SRS state and export a CSV row.
 */
public class FlashcardsView {

    private static final String REPORT_FILE = "flashcard_log.csv";

    // -- BACKWARDS COMPATIBILITY: global listener for code that can't change calls --
    private static volatile java.util.function.Consumer<Double> globalProgressListener = null;

    /**
     * Register a global listener to receive progress updates (value between 0.0 and 1.0).
     * Use this when callers cannot be changed to the new getView signature that takes a Consumer.
     */
    public static void registerProgressListener(java.util.function.Consumer<Double> listener) {
        globalProgressListener = listener;
    }

    /** Unregister the global progress listener. */
    public static void unregisterProgressListener() {
        globalProgressListener = null;
    }


    public static Node getView(String username, Runnable onFinish) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: white;");

        Label title = new Label("Flashcards");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#2F4F4F"));

        // Top: controls
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getChildren().add(title);

        // Deck choice
        ComboBox<String> deckChoice = new ComboBox<>();
        deckChoice.getItems().addAll(
                "Periodic Table Elements",
                "Common Polyatomic Ions",
                "Organic Functional Groups",
                "Common Acids & Bases"
        );
        deckChoice.getSelectionModel().selectFirst();

        // Number of cards
        TextField numField = new TextField();
        numField.setPromptText("Number to study (max available)");
        numField.setPrefWidth(160);

        Button startBtn = new Button("Start Study");
        startBtn.setStyle("-fx-background-color: linear-gradient(#b2fcb2, #70db70);");

        top.getChildren().addAll(new Label("Deck:"), deckChoice, new Label("Count:"), numField, startBtn);
        root.setTop(top);

        // Center: card area
        VBox center = new VBox(12);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(20));

        StackPane cardPane = new StackPane();
        cardPane.setPrefSize(520, 260);
        cardPane.setStyle("-fx-background-color: linear-gradient(#ffffff, #f8f8f8); -fx-border-radius: 10; -fx-background-radius: 10;");
        cardPane.setEffect(new DropShadow(8, Color.rgb(0,0,0,0.12)));

        Label cardText = new Label("No cards loaded.");
        cardText.setWrapText(true);
        cardText.setFont(Font.font("Arial", 20));
        cardText.setMaxWidth(480);
        cardText.setAlignment(Pos.CENTER);

        cardPane.getChildren().add(cardText);

        Label status = new Label("Choose a deck and press Start Study.");
        status.setFont(Font.font(13));

        center.getChildren().addAll(cardPane, status);
        root.setCenter(center);

        // Bottom: rating buttons and next controls
        HBox bottom = new HBox(10);
        bottom.setPadding(new Insets(8));
        bottom.setAlignment(Pos.CENTER);

        Button hardBtn = new Button("Hard");
        Button goodBtn = new Button("Good");
        Button easyBtn = new Button("Easy");
        Button stopBtn = new Button("Stop Session");

        hardBtn.setDisable(true);
        goodBtn.setDisable(true);
        easyBtn.setDisable(true);
        stopBtn.setDisable(true);

        bottom.getChildren().addAll(new Label("Rate:"), hardBtn, goodBtn, easyBtn, stopBtn);
        root.setBottom(bottom);

        // --- wiring up logic ---
        FlashcardProgress progress = new FlashcardProgress(username);

        // session state containers (use atomic refs so lambdas can mutate)
        final AtomicReference<List<Flashcard>> sessionRef = new AtomicReference<>(Collections.emptyList());
        final AtomicInteger sessionIndex = new AtomicInteger(0);

        // small utilities
        final Runnable clearCard = () -> {
            Platform.runLater(() -> cardText.setText("No cards loaded."));
        };

        final Consumer<Flashcard> showFront = (Flashcard card) -> {
            String front = (card == null) ? "No card" : card.front();
            Platform.runLater(() -> cardText.setText(front));
        };

        final Consumer<Flashcard> showBack = (Flashcard card) -> {
            String back = (card == null) ? "No card" : card.back();
            Platform.runLater(() -> cardText.setText(back));
        };


        // flip on click: left click toggles between front/back
        cardPane.addEventHandler(MouseEvent.MOUSE_CLICKED, (MouseEvent ev) -> {
            // only left click
            if (ev.getButton() != MouseButton.PRIMARY) return;

            List<Flashcard> session = sessionRef.get();
            if (session == null || session.isEmpty()) return;
            int idx = sessionIndex.get();
            if (idx < 0 || idx >= session.size()) return;
            Flashcard card = session.get(idx);
            // determine whether showing front or back by comparing text
            String current = cardText.getText();
            if (current != null && current.equals(card.front())) {
                showBack.accept(card);
                // enable rating buttons
                Platform.runLater(() -> {
                    hardBtn.setDisable(false);
                    goodBtn.setDisable(false);
                    easyBtn.setDisable(false);
                });
            } else {
                showFront.accept(card);
                Platform.runLater(() -> {
                    hardBtn.setDisable(true);
                    goodBtn.setDisable(true);
                    easyBtn.setDisable(true);
                });
            }
        });

        // Helper: assemble chosen deck
        final Supplier<Deck> deckSupplier = () -> {
            String sel = deckChoice.getValue();
            if (sel == null) sel = "";           // guard null

            switch (sel) {
                case "Periodic Table Elements":
                    return DeckManager.getElementsDeck();
                case "Common Polyatomic Ions":
                    return DeckManager.getPolyatomicIonsDeck();
                case "Organic Functional Groups":
                    return DeckManager.getOrganicGroupsDeck();
                case "Common Acids & Bases":
                    return DeckManager.getAcidsAndBasesDeck();
                default:
                    // fallback to elements deck if unknown
                    return DeckManager.getElementsDeck();
            }
        };

        // CSV export helper (public static-style local method)
        // Writes one row with timestamp, username, deck, front, back, rating
        final Object csvLock = new Object();
        Runnable saveProgressAndNotify = () -> {
            progress.saveProgress();
            if (onFinish != null) {
                Platform.runLater(onFinish);
            }
        };

        // implement CSV logging
        final Consumer<FlashcardLogRow> csvWriter = (FlashcardLogRow r) -> {
            synchronized (csvLock) {
                try {
                    boolean headerNeeded = !(new File(REPORT_FILE).exists() && new File(REPORT_FILE).length() > 0);
                    try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
                        if (headerNeeded) {
                            pw.println("Timestamp,Username,Deck,CardFront,CardBack,Rating");
                        }
                        pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                                r.ts, r.user, r.deckName,
                                r.front.replace("\"","\"\""),
                                r.back.replace("\"","\"\""),
                                r.rating
                        );
                    }
                } catch (Exception e) {
                    System.out.println("❌ Failed to write to flashcard log: " + e.getMessage());
                }
            }
        };

        // rating handler (shared logic for Hard/Good/Easy)
        class RatingHandler {
            private final String ratingLabel;
            RatingHandler(String label) { this.ratingLabel = label; }
            void handle() {
                List<Flashcard> session = sessionRef.get();
                if (session == null || session.isEmpty()) return;
                int idx = sessionIndex.get();
                if (idx < 0 || idx >= session.size()) return;
                Flashcard card = session.get(idx);

                // load progress state, update level (mirror CLI logic)
                progress.loadProgress();
                Optional<Integer> currentLevelOpt = Optional.ofNullable(progress.getCardState(card.id()))
                        .map(m -> ((Long) m.getOrDefault("level", 0L)).intValue());
                int currentLevel = currentLevelOpt.orElse(0);
                int newLevel = currentLevel;
                if ("Hard".equals(ratingLabel)) newLevel = currentLevel - 1;
                else if ("Easy".equals(ratingLabel)) newLevel = currentLevel + 1;
                else if ("Good".equals(ratingLabel)) newLevel = currentLevel;

                // clamp
                newLevel = Math.max(0, Math.min(newLevel, 8));

                // update SRS state
                progress.updateCardState(card.id(), newLevel);

                // export csv row
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                FlashcardLogRow row = new FlashcardLogRow(ts, username, deckSupplier.get().getName(), card.front(), card.back(), ratingLabel);
                csvWriter.accept(row);

                // advance to next card
                int next = idx + 1;
                if (next >= session.size()) {
                    // session finished
                    status.setText(String.format("Session complete! Reviewed %d card(s).", session.size()));
                    Platform.runLater(() -> {
                        hardBtn.setDisable(true);
                        goodBtn.setDisable(true);
                        easyBtn.setDisable(true);
                        stopBtn.setDisable(true);
                    });

                    if (next >= session.size()) {
                        // session finished
                        status.setText(String.format("Session complete! Reviewed %d card(s).", session.size()));
                        System.out.println("[FlashcardsView] session finished for user=" + username + ", reviewed=" + session.size());

                        progress.incrementReviewedCount(session.size());
                        saveProgressAndNotify.run();
                    }

                    // increment counters & save progress
                    progress.incrementReviewedCount(session.size());
                    saveProgressAndNotify.run();
                } else {
                    sessionIndex.set(next);
                    Flashcard nextCard = session.get(next);
                    showFront.accept(nextCard);
                    status.setText(String.format("Card %d of %d", next + 1, session.size()));
                    // disable rating until user flips
                    Platform.runLater(() -> {
                        hardBtn.setDisable(true);
                        goodBtn.setDisable(true);
                        easyBtn.setDisable(true);
                    });
                }
            }
        }

        // wire rating buttons
        hardBtn.setOnAction(e -> new RatingHandler("Hard").handle());
        goodBtn.setOnAction(e -> new RatingHandler("Good").handle());
        easyBtn.setOnAction(e -> new RatingHandler("Easy").handle());

        // stop session button
        stopBtn.setOnAction(e -> {
            sessionRef.set(Collections.emptyList());
            sessionIndex.set(0);
            clearCard.run();
            status.setText("Session stopped by user.");
            hardBtn.setDisable(true);
            goodBtn.setDisable(true);
            easyBtn.setDisable(true);
            stopBtn.setDisable(true);
            saveProgressAndNotify.run();
        });

        // Start study click: builds session list and shows first card
        startBtn.setOnAction(ev -> {
            Deck currentDeck = deckSupplier.get();
            if (currentDeck == null) {
                status.setText("Could not load deck.");
                return;
            }
            // determine number requested
            int requested = 0;
            String tf = numField.getText().trim();
            if (tf.isEmpty()) {
                requested = currentDeck.getCards().size();
            } else {
                try {
                    requested = Integer.parseInt(tf);
                } catch (NumberFormatException ex) {
                    status.setText("Enter a valid number.");
                    return;
                }
            }
            List<Flashcard> pool = new ArrayList<>(currentDeck.getCards());
            if (pool.isEmpty()) {
                status.setText("This deck has no cards.");
                return;
            }
            Collections.shuffle(pool);
            int take = Math.min(requested, pool.size());
            List<Flashcard> session = pool.subList(0, take);
            sessionRef.set(new ArrayList<>(session));
            sessionIndex.set(0);

            // show first card front
            Flashcard first = session.get(0);
            showFront.accept(first);
            status.setText(String.format("Card 1 of %d — Click the card to flip.", session.size()));

            // enable stop
            stopBtn.setDisable(false);
            hardBtn.setDisable(true);
            goodBtn.setDisable(true);
            easyBtn.setDisable(true);
        });

        return root;
    }

    // small data holder for CSV write
    private static class FlashcardLogRow {
        final String ts, user, deckName, front, back, rating;
        FlashcardLogRow(String ts, String user, String deckName, String front, String back, String rating) {
            this.ts = ts; this.user = user; this.deckName = deckName; this.front = front; this.back = back; this.rating = rating;
        }
    }
}
