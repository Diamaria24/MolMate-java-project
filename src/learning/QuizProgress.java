package learning;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class QuizProgress {
    private String username;
    private String progressFile;
    private Properties stats = new Properties();

    // The constructor now takes a username to create a user-specific file path.
    public QuizProgress(String username) {
        this.username = username;
        this.progressFile = "progress_data/" + username + "_quiz_progress.properties";
        loadProgress();
    }

    private void loadProgress() {
        try {
            Files.createDirectories(Paths.get("progress_data"));
            stats.load(new FileReader(progressFile));
        } catch (Exception e) {
            // File not existing is normal for a new user.
        }
    }

    public void saveProgress() {
        try (FileWriter writer = new FileWriter(progressFile)) {
            stats.store(writer, "User Quiz Progress for " + username);
        } catch (Exception e) {
            System.out.println("❌ Could not save quiz progress: " + e.getMessage());
        }
    }

    public void updateStat(String key, int valueToAdd) {
        int currentVal = Integer.parseInt(stats.getProperty(key, "0"));
        stats.setProperty(key, String.valueOf(currentVal + valueToAdd));
    }

    public void displaySummary() {
        // The summary is now personalized with the username.
        System.out.println("\n--- " + username + "'s Quiz Progress ---");
        int totalAnswered = Integer.parseInt(stats.getProperty("total_answered", "0"));
        int totalCorrect = Integer.parseInt(stats.getProperty("total_correct", "0"));
        if (totalAnswered == 0) {
            System.out.println("No stats yet. Good luck!");
            return;
        }
        double accuracy = ((double) totalCorrect / totalAnswered) * 100.0;
        System.out.printf("Total Questions Answered: %d\n", totalAnswered);
        System.out.printf("Overall Accuracy: %.2f%%\n", accuracy);
        System.out.println("---------------------------------");
    }

    public int getTotalAnswered() {
        return Integer.parseInt(stats.getProperty("total_answered", "0"));
    }
    public int getTotalCorrect() {
        return Integer.parseInt(stats.getProperty("total_correct", "0"));
    }
    public double getAccuracy() {
        int answered = getTotalAnswered();
        if (answered == 0) return 0.0;
        return ((double) getTotalCorrect() / answered);
    }

    // In file: learning/QuizProgress.java

    public void addStudyTime(long milliseconds) {
        long currentTime = Long.parseLong(stats.getProperty("total_study_time_ms", "0"));
        stats.setProperty("total_study_time_ms", String.valueOf(currentTime + milliseconds));
    }

    public long getTotalStudyTimeMillis() {
        return Long.parseLong(stats.getProperty("total_study_time_ms", "0"));
    }
}
