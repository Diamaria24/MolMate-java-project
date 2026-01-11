package core;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.apache.commons.text.similarity.LevenshteinDistance;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;




public class FormulaConverter {

    // A record to hold the result, a potential suggestion, and extra info for synonyms.
    public record ConvertResult(String resultText, String suggestion, boolean isSuccess, String extraInfo) {
    }

    // --- Singleton Pattern to load data only once ---
    private static FormulaConverter instance;

    public static FormulaConverter getInstance() {
        if (instance == null) {
            instance = new FormulaConverter("compounds_1000_extended.json");
        }
        return instance;
    }

    // --- Data Dictionaries ---
    private final Map<String, List<String>> formulaToNames = new HashMap<>();
    private final Map<String, String> nameToFormula = new HashMap<>();
    private final Map<String, JSONObject> formulaDetails = new HashMap<>();
    private final Map<String, List<String>> reverseSynonymMap = new HashMap<>();

    // Organic dictionary (basic compounds)
    private static final Map<String, String> organicDictionary = Map.ofEntries(
            Map.entry("methane", "CH4"),
            Map.entry("ethane", "C2H6"),
            Map.entry("propane", "C3H8"),
            Map.entry("butane", "C4H10"),
            Map.entry("ethanol", "C2H5OH"),
            Map.entry("methanol", "CH3OH"),
            Map.entry("acetic acid", "CH3COOH"),
            Map.entry("formic acid", "HCOOH"),
            Map.entry("benzene", "C6H6"),
            Map.entry("toluene", "C7H8")
    );

    // Synonym dictionary (common → standard names)
    private static final Map<String, String> synonymDictionary = Map.ofEntries(
            Map.entry("caustic soda", "sodium hydroxide"),
            Map.entry("quicklime", "calcium oxide"),
            Map.entry("slaked lime", "calcium hydroxide"),
            Map.entry("baking soda", "sodium bicarbonate"),
            Map.entry("washing soda", "sodium carbonate"),
            Map.entry("epsom salt", "magnesium sulfate"),
            Map.entry("bleach", "sodium hypochlorite"),
            Map.entry("vinegar", "acetic acid"),
            Map.entry("aqua fortis", "nitric acid"),
            Map.entry("oil of vitriol", "sulfuric acid"),
            Map.entry("laughing gas", "nitrous oxide"),
            Map.entry("marsh gas", "methane"),
            Map.entry("muriatic acid", "hydrochloric acid"),
            Map.entry("caustic potash", "potassium hydroxide"),
            Map.entry("plaster of paris", "calcium sulfate"),
            Map.entry("dry ice", "carbon dioxide")
    );


    private FormulaConverter(String jsonFileName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(jsonFileName)) {
            if (is == null) throw new RuntimeException("Resource not found: " + jsonFileName);

            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(new InputStreamReader(is, StandardCharsets.UTF_8));

            for (Object formulaKey : json.keySet()) {
                String formula = (String) formulaKey;
                JSONObject obj = (JSONObject) json.get(formula);
                JSONArray namesArray = (JSONArray) obj.get("names");
                List<String> names = new ArrayList<>();
                for (Object nameObj : namesArray) {
                    String name = (String) nameObj;
                    names.add(name);
                    nameToFormula.put(name.toLowerCase(), formula);
                }
                formulaToNames.put(formula, names);
                formulaDetails.put(formula, obj);
            }

            organicDictionary.forEach((name, formula) -> {
                nameToFormula.put(name.toLowerCase(), formula);
                formulaToNames.computeIfAbsent(formula, k -> new ArrayList<>()).add(capitalizeWords(name));
            });

            synonymDictionary.forEach((synonym, standardName) -> {
                String formula = nameToFormula.get(standardName.toLowerCase());
                if (formula != null) {
                    nameToFormula.put(synonym.toLowerCase(), formula);
                    reverseSynonymMap.computeIfAbsent(standardName.toLowerCase(), k -> new ArrayList<>()).add(synonym);
                }
            });

        } catch (Exception e) {
            System.out.println("❌ Failed to load compound JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public ConvertResult convert(String input) {
        input = input.trim();
        if (input.isEmpty()) return new ConvertResult("Please enter a name or formula.", null, false, null);

        if (Character.isUpperCase(input.charAt(0)) && input.matches(".*[0-9].*")) { // Formula mode
            if (formulaToNames.containsKey(input)) {
                return new ConvertResult(formatDetails(input), null, true, null);
            } else {
                String suggestion = suggestClosestFormula(input);
                return new ConvertResult("Formula not found.", suggestion, false, null);
            }
        } else { // Name mode
            String lower = input.toLowerCase();
            String standardizedName = synonymDictionary.getOrDefault(lower, lower);

            if (nameToFormula.containsKey(standardizedName)) {
                String formula = nameToFormula.get(standardizedName);
                String resultText = formatDetails(formula);
                String extraInfo = null;
                if (synonymDictionary.containsKey(lower)) {
                    extraInfo = "Input '" + input + "' is a common name for: " + capitalizeWords(standardizedName);
                }
                return new ConvertResult(resultText, null, true, extraInfo);
            } else {
                String suggestion = suggestClosestName(standardizedName);
                return new ConvertResult("Name not found.", capitalizeWords(suggestion), false, null);
            }
        }
    }

    private String formatDetails(String formula) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Formula: %s\nNames: %s", formula, String.join(", ", formulaToNames.get(formula))));

        if (formulaDetails.containsKey(formula)) {
            JSONObject obj = formulaDetails.get(formula);
            if (obj.containsKey("category")) sb.append("\nCategory: ").append(obj.get("category"));
            if (obj.containsKey("type")) sb.append("\nType: ").append(obj.get("type"));
        }

        String standardName = formulaToNames.get(formula).get(0).toLowerCase();
        if (reverseSynonymMap.containsKey(standardName)) {
            sb.append("\nAlso known as: ").append(
                    reverseSynonymMap.get(standardName).stream().map(this::capitalizeWords).collect(Collectors.joining(", "))
            );
        }
        return sb.toString();
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return "";
        return Arrays.stream(text.split(" "))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private String suggestClosestName(String input) {
        LevenshteinDistance ld = new LevenshteinDistance(3);
        String bestMatch = null;
        int minDistance = 4;
        for (String name : nameToFormula.keySet()) {
            int distance = ld.apply(input.toLowerCase(), name);
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = name;
            }
        }
        return bestMatch;
    }

    private String suggestClosestFormula(String input) {
        LevenshteinDistance ld = new LevenshteinDistance(2);
        String bestMatch = null;
        int minDistance = 3;
        for (String formula : formulaToNames.keySet()) {
            int distance = ld.apply(input, formula);
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = formula;
            }
        }
        return bestMatch;
    }


    // === Normalization of names ===
    private String normalizeName(String name) {
        String lower = name.toLowerCase();

        // Synonym replacement
        if (synonymDictionary.containsKey(lower)) {
            return synonymDictionary.get(lower).toLowerCase();
        }

        // British ↔ American spelling
        lower = lower.replace("sulphate", "sulfate");
        lower = lower.replace("sulphide", "sulfide");
        lower = lower.replace("aluminium", "aluminum");

        // Handle Roman numerals
        lower = lower.replace(" 1 ", " (i) ");
        lower = lower.replace(" 2 ", " (ii) ");
        lower = lower.replace(" 3 ", " (iii) ");
        lower = lower.replace(" 4 ", " (iv) ");
        lower = lower.replace(" 5 ", " (v) ");
        lower = lower.replace(" 6 ", " (vi) ");
        lower = lower.replace(" 7 ", " (vii) ");
        lower = lower.replace(" 8 ", " (viii) ");
        lower = lower.replace(" 9 ", " (ix) ");
        lower = lower.replace(" 10 ", " (x) ");

        return lower.trim();
    }

}

