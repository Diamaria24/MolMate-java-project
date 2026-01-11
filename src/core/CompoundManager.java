package core;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class CompoundManager {
    private static JSONObject localCompoundData;
    private static boolean isLoaded = false;
    // NOTE: This path assumes your 'resources' folder is at the top level of your project.
    private static final String COMPOUND_FILE_PATH = "resources/compounds_1000_extended.json";

    private static void loadLocalData() {
        if (isLoaded) return;
        JSONParser parser = new JSONParser();
        try (InputStream is = new FileInputStream(COMPOUND_FILE_PATH)) {
            localCompoundData = (JSONObject) parser.parse(new InputStreamReader(is, StandardCharsets.UTF_8));
            isLoaded = true;
        } catch (Exception e) {
            System.out.println("❌ Error loading local compound database: " + e.getMessage());
            localCompoundData = new JSONObject(); // Initialize to avoid errors
        }
    }

    @SuppressWarnings("unchecked")
    private static void saveNewCompoundToJson(Map<String, Object> data) {
        if (localCompoundData == null) return;

        String formula = data.get("MolecularFormula").toString();
        String name = data.get("IUPACName").toString();
        Object chargeObj = data.get("Charge");
        long charge = (chargeObj instanceof Long) ? (long) chargeObj : 0;

        JSONObject newCompoundDetails = new JSONObject();
        JSONArray namesArray = new JSONArray();
        namesArray.add(name);

        newCompoundDetails.put("names", namesArray);
        newCompoundDetails.put("charge", charge);
        newCompoundDetails.put("type", "compound");
        newCompoundDetails.put("category", "api_added");

        localCompoundData.put(formula, newCompoundDetails);

        try (FileWriter file = new FileWriter(COMPOUND_FILE_PATH)) {
            file.write(localCompoundData.toJSONString());
            file.flush();
            System.out.println("✅ Saved new compound '" + name + "' to local database!");
        } catch (IOException e) {
            System.out.println("❌ Failed to save new compound to local database: " + e.getMessage());
        }
    }

    public static Map<String, Object> getCompoundData(String name) {
        loadLocalData();

        for (Object key : localCompoundData.keySet()) {
            JSONObject compound = (JSONObject) localCompoundData.get(key);
            JSONArray names = (JSONArray) compound.get("names");
            if (names != null && names.stream().anyMatch(n -> n.toString().equalsIgnoreCase(name))) {
                System.out.println("ℹ️ Found '" + name + "' in local offline database.");
                Map<String, Object> localMap = new LinkedHashMap<>();
                localMap.put("Source", "Local Database");
                localMap.put("Formula", key.toString());
                localMap.put("Names", names);
                return localMap;
            }
        }

        System.out.println("⚠️ Could not find '" + name + "' locally. Checking online with PubChem...");
        Map<String, Object> onlineData = PubChemService.fetchCompoundData(name);

        if (onlineData != null) {
            System.out.println("✅ Successfully fetched data from PubChem API.");
            saveNewCompoundToJson(onlineData);
            return onlineData;
        }

        return null;
    }
}