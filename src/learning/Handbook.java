package learning; // <-- THE FIX: Package has been changed to 'learning'

import core.ReactionAnalyzer; // You might need to import classes from core
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Handbook {

    // New data structures to perfectly match your new JSON structure
    public static record HandbookEntry(String id, String title, String summary, String body) {}
    public static record HandbookCategory(String id, String name, String description, List<HandbookEntry> entries) {}

    private static final List<HandbookCategory> categories = new ArrayList<>();
    private static boolean isLoaded = false;

    public static void loadData() {
        if (isLoaded) return;
        JSONParser parser = new JSONParser();
        try (InputStream is = Handbook.class.getClassLoader().getResourceAsStream("handbook.json")) {
            if (is == null) {
                System.out.println("❌ CRITICAL ERROR: Could not find 'handbook.json' in resources.");
                return;
            }

            JSONObject root = (JSONObject) parser.parse(new InputStreamReader(is, StandardCharsets.UTF_8));
            JSONArray categoriesArray = (JSONArray) root.get("categories");

            for (Object catObj : categoriesArray) {
                JSONObject catJson = (JSONObject) catObj;
                List<HandbookEntry> entries = new ArrayList<>();
                JSONArray entriesArray = (JSONArray) catJson.get("entries");

                for(Object entryObj : entriesArray) {
                    JSONObject entryJson = (JSONObject) entryObj;
                    entries.add(new HandbookEntry(
                            (String) entryJson.get("id"),
                            (String) entryJson.get("title"),
                            (String) entryJson.get("summary"),
                            (String) entryJson.get("body")
                    ));
                }
                categories.add(new HandbookCategory(
                        (String) catJson.get("id"),
                        (String) catJson.get("name"),
                        (String) catJson.get("description"),
                        entries
                ));
            }
            isLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<HandbookCategory> getCategories() {
        if (!isLoaded) loadData();
        return categories;
    }

    public static void displayEntry(HandbookEntry entry) {
        System.out.println("\n========================================");
        System.out.println("### " + entry.title() + " ###");
        System.out.println("========================================");
        System.out.println("Summary: " + entry.summary());
        System.out.println("\n" + entry.body());
        System.out.println("========================================");
    }
}
