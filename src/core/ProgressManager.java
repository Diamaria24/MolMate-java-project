package core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Lightweight per-user progress store.
 * Data stored under ~/.molmate_progress/<username>.props
 *
 * Keys:
 *  quiz.total_answered
 *  quiz.total_correct
 *  quiz.total_quiz_time_seconds
 *  quiz.quiz_sessions
 *  quiz.last_session_time_seconds
 *
 *  flash.total_reviewed
 *  flash.total_flash_time_seconds
 *  flash.flash_sessions
 *  flash.last_session_time_seconds
 *
 *  study.<yyyy-MM-dd> = minutesStudiedOnDay
 *
 *  affirmations = newline-separated affirmations
 */
public class ProgressManager {
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.home"), ".molmate_progress");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    static {
        try {
            if (!Files.exists(BASE_DIR)) Files.createDirectories(BASE_DIR);
        } catch (IOException e) {
            System.err.println("Failed to create progress dir: " + e.getMessage());
        }
    }

    private static Path userFile(String username) {
        String safe = username.replaceAll("[^A-Za-z0-9_.-]", "_");
        return BASE_DIR.resolve(safe + ".props");
    }

    private static Properties loadProps(String username) {
        Properties p = new Properties();
        Path f = userFile(username);
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("Failed to load progress for " + username + ": " + e.getMessage());
            }
        }
        return p;
    }

    private static void saveProps(String username, Properties p) {
        Path f = userFile(username);
        try (OutputStream out = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "MolMate progress for " + username);
        } catch (IOException e) {
            System.err.println("Failed to save progress for " + username + ": " + e.getMessage());
        }
    }

    // --- Quiz updates ---
    public static synchronized void updateQuizSession(String username, int correctCount, int answeredCount, long sessionSeconds) {
        Properties p = loadProps(username);
        long totalAnswered = Long.parseLong(p.getProperty("quiz.total_answered", "0"));
        long totalCorrect = Long.parseLong(p.getProperty("quiz.total_correct", "0"));
        long totalTime = Long.parseLong(p.getProperty("quiz.total_quiz_time_seconds", "0"));
        long sessions = Long.parseLong(p.getProperty("quiz.quiz_sessions", "0"));

        totalAnswered += answeredCount;
        totalCorrect += correctCount;
        totalTime += sessionSeconds;
        sessions++;

        p.setProperty("quiz.total_answered", Long.toString(totalAnswered));
        p.setProperty("quiz.total_correct", Long.toString(totalCorrect));
        p.setProperty("quiz.total_quiz_time_seconds", Long.toString(totalTime));
        p.setProperty("quiz.quiz_sessions", Long.toString(sessions));
        p.setProperty("quiz.last_session_time_seconds", Long.toString(sessionSeconds));
        saveProps(username, p);
    }

    // --- Flashcards updates ---
    public static synchronized void updateFlashcardSession(String username, int reviewedCount, long sessionSeconds) {
        Properties p = loadProps(username);
        long totalReviewed = Long.parseLong(p.getProperty("flash.total_reviewed", "0"));
        long totalTime = Long.parseLong(p.getProperty("flash.total_flash_time_seconds", "0"));
        long sessions = Long.parseLong(p.getProperty("flash.flash_sessions", "0"));

        totalReviewed += reviewedCount;
        totalTime += sessionSeconds;
        sessions++;

        p.setProperty("flash.total_reviewed", Long.toString(totalReviewed));
        p.setProperty("flash.total_flash_time_seconds", Long.toString(totalTime));
        p.setProperty("flash.flash_sessions", Long.toString(sessions));
        p.setProperty("flash.last_session_time_seconds", Long.toString(sessionSeconds));
        saveProps(username, p);
    }

    // --- Study time logging (per-day minutes) ---
    public static synchronized void addStudyMinutes(String username, int minutes) {
        if (minutes <= 0) return;
        Properties p = loadProps(username);
        String key = "study." + LocalDate.now().format(DATE_FMT);
        int cur = Integer.parseInt(p.getProperty(key, "0"));
        cur += minutes;
        p.setProperty(key, Integer.toString(cur));
        saveProps(username, p);
    }

    // returns total minutes studied today
    public static int getTodayStudyMinutes(String username) {
        Properties p = loadProps(username);
        String key = "study." + LocalDate.now().format(DATE_FMT);
        return Integer.parseInt(p.getProperty(key, "0"));
    }

    // returns average study minutes per day over last N days (including days with 0)
    public static double getAverageStudyMinutes(String username, int days) {
        Properties p = loadProps(username);
        LocalDate now = LocalDate.now();
        int sum = 0;
        for (int i = 0; i < days; i++) {
            String key = "study." + now.minusDays(i).format(DATE_FMT);
            sum += Integer.parseInt(p.getProperty(key, "0"));
        }
        return ((double) sum) / days;
    }

    // --- Read-only getters for GUI ---
    public static synchronized Map<String, Object> loadProgressSnapshot(String username) {
        Properties p = loadProps(username);
        Map<String, Object> m = new HashMap<>();
        // quiz
        long quizAnswered = Long.parseLong(p.getProperty("quiz.total_answered", "0"));
        long quizCorrect = Long.parseLong(p.getProperty("quiz.total_correct", "0"));
        long quizTime = Long.parseLong(p.getProperty("quiz.total_quiz_time_seconds", "0"));
        long quizSessions = Long.parseLong(p.getProperty("quiz.quiz_sessions", "0"));
        long quizLast = Long.parseLong(p.getProperty("quiz.last_session_time_seconds", "0"));

        m.put("quiz.total_answered", quizAnswered);
        m.put("quiz.total_correct", quizCorrect);
        m.put("quiz.total_quiz_time_seconds", quizTime);
        m.put("quiz.quiz_sessions", quizSessions);
        m.put("quiz.last_session_time_seconds", quizLast);

        // flash
        long flashReviewed = Long.parseLong(p.getProperty("flash.total_reviewed", "0"));
        long flashTime = Long.parseLong(p.getProperty("flash.total_flash_time_seconds", "0"));
        long flashSessions = Long.parseLong(p.getProperty("flash.flash_sessions", "0"));
        long flashLast = Long.parseLong(p.getProperty("flash.last_session_time_seconds", "0"));

        m.put("flash.total_reviewed", flashReviewed);
        m.put("flash.total_flash_time_seconds", flashTime);
        m.put("flash.flash_sessions", flashSessions);
        m.put("flash.last_session_time_seconds", flashLast);

        // study
        int today = Integer.parseInt(p.getProperty("study." + LocalDate.now().format(DATE_FMT), "0"));
        m.put("study.today_minutes", today);
        m.put("study.avg7", getAverageStudyMinutes(username, 7));
        m.put("study.avg30", getAverageStudyMinutes(username, 30));

        // affirmations
        String aff = p.getProperty("affirmations", "");
        List<String> affirmations = new ArrayList<>();
        if (!aff.isBlank()) {
            String[] lines = aff.split("\\\\n"); // stored with escaped newline
            for (String l : lines) if (!l.isBlank()) affirmations.add(l);
        }
        m.put("affirmations", affirmations);

        return m;
    }

    public static synchronized void addAffirmation(String username, String affirmation) {
        if (affirmation == null || affirmation.isBlank()) return;
        Properties p = loadProps(username);
        String existing = p.getProperty("affirmations", "");
        String escaped = existing.isEmpty() ? escapeNewline(affirmation) : existing + "\\n" + escapeNewline(affirmation);
        p.setProperty("affirmations", escaped);
        saveProps(username, p);
    }

    private static String escapeNewline(String s) {
        return s.replace("\n", " ").replace("\r", " ");
    }
}

