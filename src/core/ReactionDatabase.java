package core;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Random;


public class ReactionDatabase {

    private static final List<Reaction> reactions = new ArrayList<>();
    private static boolean isLoaded = false;

    /**
     * Loads the reactions from the reactions.json file into memory.
     * This method is safe to call multiple times; it will only load the data once.
     */
    public static void loadDatabase() {
        if (isLoaded) return;

        InputStream is = null;
        try {
            is = ReactionDatabase.class.getClassLoader().getResourceAsStream("reactions.json");

            // This check will now reliably run and print an error if the file isn't found.
            if (is == null) {
                System.out.println("❌ CRITICAL ERROR: Could not find 'reactions.json'. Please make sure it's in the 'resources' folder.");
                return;
            }

            JSONParser parser = new JSONParser();
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            JSONObject root = (JSONObject) parser.parse(reader);
            JSONArray reactionsArray = (JSONArray) root.get("reactions");

            for (Object obj : reactionsArray) {
                JSONObject reactionJson = (JSONObject) obj;
                Reaction reaction = new Reaction(
                        (String) reactionJson.get("name"),
                        (String) reactionJson.get("equation"),
                        (String) reactionJson.get("type"),
                        (String) reactionJson.get("description")
                );
                reactions.add(reaction);
            }

            System.out.println("✅ Reaction Database loaded with " + reactions.size() + " entries.");
            isLoaded = true;

        } catch (Exception e) {
            System.out.println("❌ Error loading reaction database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Ensure the input stream is closed
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Searches the database for reactions containing a specific compound.
     * @param compoundFormula The chemical formula to search for (e.g., "H2O").
     * @return A list of matching Reaction objects.
     */
    public static List<Reaction> findReactionsByCompound(String compoundFormula) {
        if (!isLoaded) loadDatabase();
        String searchFormula = compoundFormula.trim();
        return reactions.stream()
                .filter(r -> r.equation().contains(searchFormula))
                .collect(Collectors.toList());
    }

    /**
     * Searches the database for reactions whose name contains the search query.
     * @param nameQuery The text to search for in the reaction name.
     * @return A list of matching Reaction objects.
     */
    public static List<Reaction> findReactionsByName(String nameQuery) {
        if (!isLoaded) loadDatabase();
        String queryLower = nameQuery.trim().toLowerCase();
        return reactions.stream()
                .filter(r -> r.name().toLowerCase().contains(queryLower))
                .collect(Collectors.toList());
    }

    public static Reaction getRandomReaction() {
        if (!isLoaded || reactions.isEmpty()) {
            loadDatabase(); // Ensure data is loaded
        }
        if (reactions.isEmpty()) {
            // Return a default fallback reaction if the database is empty
            return new Reaction("Fallback Reaction", "H2 + O2 -> H2O", "Synthesis", "A fallback reaction.");
        }
        return reactions.get(new Random().nextInt(reactions.size()));
    }
}