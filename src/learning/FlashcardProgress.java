package learning;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * FlashcardProgress: stores SRS levels, reviewed counts, and study time.
 * Extended with a simple static listener registry so GUIs can observe progress changes.
 */
public class FlashcardProgress {
    private String username;
    private JSONObject progressData;
    private static final String PROGRESS_DIR = "progress_data/";
    private String progressFile;

    private static final long[] SRS_INTERVALS_MINUTES = {
            1, 10, 60, 480, 1440, 4320, 10080, 20160, 43200
    };

    // --- Static listener registry (simple) ---
    // Observers receive a Double in range 0.0..1.0 representing mastery fraction.
    private static final List<Consumer<Double>> listeners = Collections.synchronizedList(new ArrayList<>());

    public static void addProgressListener(Consumer<Double> listener) {
        if (listener != null) listeners.add(listener);
    }

    public static void removeProgressListener(Consumer<Double> listener) {
        if (listener != null) listeners.remove(listener);
    }

    // Helper to notify listeners of computed mastery (mastered / totalKnown)
    // Helper to notify listeners of computed mastery (mastered / totalKnown)
    private void notifyListeners() {
        // compute counts for debug visibility
        int mastered = getMasteredCardsCount();
        int totalKnown = getTotalKnownCards();
        double pct = computeMasteryFraction();

        System.out.println("[FlashcardProgress] notifyListeners() -> " + pct + " (user=" + username + ") mastered=" + mastered + " totalKnown=" + totalKnown);

        // copy to avoid concurrent modifications while notifying
        List<Consumer<Double>> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<>(listeners);
        }
        System.out.println("[FlashcardProgress] listener count: " + snapshot.size()); // debug
        for (Consumer<Double> c : snapshot) {
            try {
                c.accept(pct);
                System.out.println("[FlashcardProgress] called one listener successfully");
            } catch (Throwable t) {
                System.err.println("FlashcardProgress listener error: " + t.getMessage());
            }
        }
    }



    public FlashcardProgress(String username) {
        this.username = username;
        this.progressFile = PROGRESS_DIR + username + "_flashcard_progress.json";
        loadProgress();
    }

    @SuppressWarnings("unchecked") // Suppresses warnings from the old json-simple library
    public void loadProgress() {
        try {
            Files.createDirectories(Paths.get(PROGRESS_DIR));
            File file = new File(progressFile);
            if (!file.exists()) {
                progressData = new JSONObject();
                progressData.put("username", username);
                progressData.put("totalCardsReviewed", 0L);
                progressData.put("cardStates", new JSONObject());
                progressData.put("total_study_time_ms", 0L);
                return;
            }
            progressData = (JSONObject) new JSONParser().parse(new FileReader(file));
        } catch (Exception e) {
            System.out.println("⚠️  Could not load flashcard progress, starting fresh. Error: " + e.getMessage());
            progressData = new JSONObject();
            progressData.put("username", username);
            progressData.put("totalCardsReviewed", 0L);
            progressData.put("cardStates", new JSONObject());
            progressData.put("total_study_time_ms", 0L);
        }
    }

    public void saveProgress() {
        try (FileWriter file = new FileWriter(progressFile)) {
            file.write(progressData.toJSONString());
            file.flush();
        } catch (Exception e) {
            System.out.println("❌ Could not save flashcard progress: " + e.getMessage());
        }
        // Notify listeners because underlying data persisted (progress changed)
        notifyListeners();
    }

    @SuppressWarnings("unchecked")
    public void incrementReviewedCount(int amount) {
        long currentCount = (Long) progressData.getOrDefault("totalCardsReviewed", 0L);
        progressData.put("totalCardsReviewed", currentCount + amount);
        // Persist & notify
        saveProgress();
    }

    public void displaySummary() {
        System.out.println("\n--- Your Flashcard Progress ---");
        long totalReviewed = (Long) progressData.getOrDefault("totalCardsReviewed", 0L);
        System.out.println("Total Cards Reviewed (Lifetime): " + totalReviewed);
        System.out.println("-----------------------------");
    }

    public JSONObject getCardState(String cardId) {
        JSONObject cardStates = (JSONObject) progressData.get("cardStates");
        return (JSONObject) cardStates.get(cardId);
    }

    @SuppressWarnings("unchecked")
    public void updateCardState(String cardId, int newLevel) {
        JSONObject cardStates = (JSONObject) progressData.get("cardStates");
        if (cardStates == null) {
            cardStates = new JSONObject();
            progressData.put("cardStates", cardStates);
        }
        JSONObject state = (JSONObject) cardStates.getOrDefault(cardId, new JSONObject());

        newLevel = Math.max(0, Math.min(newLevel, SRS_INTERVALS_MINUTES.length - 1));
        state.put("level", (long) newLevel);

        long intervalMillis = TimeUnit.MINUTES.toMillis(SRS_INTERVALS_MINUTES[newLevel]);
        long nextReviewTimestamp = System.currentTimeMillis() + intervalMillis;
        state.put("nextReviewTimestamp", nextReviewTimestamp);

        cardStates.put(cardId, state);

        // Persist & notify listeners (progress changed)
        saveProgress();
    }

    public Flashcards.Flashcard getNextCardToReview(List<Flashcards.Flashcard> allCards) {
        System.out.println("\nDEBUG: Searching for next card to review...");
        List<Flashcards.Flashcard> dueCards = new ArrayList<>();
        List<Flashcards.Flashcard> newCards = new ArrayList<>();
        long now = System.currentTimeMillis();
        int seenCards = 0;

        JSONObject cardStates = (JSONObject) progressData.get("cardStates");
        if (cardStates == null) cardStates = new JSONObject();

        for (Flashcards.Flashcard card : allCards) {
            if (cardStates.containsKey(card.id())) {
                seenCards++;
                JSONObject state = (JSONObject) cardStates.get(card.id());
                long nextReview = (Long) state.get("nextReviewTimestamp");
                if (nextReview <= now) {
                    dueCards.add(card);
                }
            } else {
                newCards.add(card);
            }
        }

        System.out.println("DEBUG: Total cards in deck: " + allCards.size());
        System.out.println("DEBUG: Cards with saved progress (seen before): " + seenCards);
        System.out.println("DEBUG: New (unseen) cards found: " + newCards.size());
        System.out.println("DEBUG: Cards due for review: " + dueCards.size());

        if (!dueCards.isEmpty()) {
            System.out.println("DEBUG: Prioritizing and returning a DUE card.");
            return dueCards.get(new Random().nextInt(dueCards.size()));
        }
        if (!newCards.isEmpty()) {
            System.out.println("DEBUG: No due cards. Returning a NEW card.");
            return newCards.get(new Random().nextInt(newCards.size()));
        }

        System.out.println("DEBUG: No due cards and no new cards. Returning NULL.");
        return null; // All cards reviewed and none are due
    }

    public int getAvailableCardCount(List<Flashcards.Flashcard> allCards) {
        int count = 0;
        long now = System.currentTimeMillis();

        for (Flashcards.Flashcard card : allCards) {
            JSONObject state = getCardState(card.id());
            if (state == null) {
                count++; // This is a new card.
            } else {
                long nextReview = (Long) state.get("nextReviewTimestamp");
                if (nextReview <= now) {
                    count++; // This card is due for review.
                }
            }
        }
        return count;
    }

    // --- Public getter methods for the GUI Dashboard ---
    public int getTotalReviewed() {
        return ((Long) progressData.getOrDefault("totalCardsReviewed", 0L)).intValue();
    }

    public int getMasteredCardsCount() {
        JSONObject cardStates = (JSONObject) progressData.get("cardStates");
        if (cardStates == null) return 0;
        int masteredCount = 0;
        for (Object key : cardStates.keySet()) {
            JSONObject state = (JSONObject) cardStates.get(key);
            long level = (long) state.getOrDefault("level", 0L);
            if (level >= 5) { // We'll define "mastered" as SRS level 5 or higher
                masteredCount++;
            }
        }
        return masteredCount;
    }

    public int getTotalKnownCards() {
        JSONObject cardStates = (JSONObject) progressData.get("cardStates");
        return (cardStates != null) ? cardStates.size() : 0;
    }

    // Compute mastery fraction used for notifications: mastered / totalKnown
    private double computeMasteryFraction() {
        int totalKnown = getTotalKnownCards();
        if (totalKnown <= 0) return 0.0;
        int mastered = getMasteredCardsCount();
        return Math.max(0.0, Math.min(1.0, ((double) mastered) / ((double) totalKnown)));
    }

    @SuppressWarnings("unchecked")
    public void addStudyTime(long milliseconds) {
        long currentTime = (Long) progressData.getOrDefault("total_study_time_ms", 0L);
        progressData.put("total_study_time_ms", currentTime + milliseconds);
        // Persist & notify
        saveProgress();
    }

    public long getTotalStudyTimeMillis() {
        return (Long) progressData.getOrDefault("total_study_time_ms", 0L);
    }
}
