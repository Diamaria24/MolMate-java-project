package core;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class PubChemService {

    private static final String API_BASE_URL = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/";
    // --- UPDATED: Expanded list of properties to fetch ---
    private static final String API_PROPERTIES_LIST = "MolecularFormula,MolecularWeight,IUPACName,CanonicalSMILES,Charge,HBondDonorCount,HBondAcceptorCount,Complexity";
    private static final String API_PROPERTIES_URL_PART = "/property/" + API_PROPERTIES_LIST + "/JSON";

    /**
     * Fetches a rich set of data for a compound name from the PubChem API.
     * @return A Map where keys are property names and values are the fetched data.
     */
    public static Map<String, Object> fetchCompoundData(String compoundName) {
        HttpClient client = HttpClient.newHttpClient();
        JSONParser parser = new JSONParser();

        try {
            String encodedName = URLEncoder.encode(compoundName, StandardCharsets.UTF_8);
            String url = API_BASE_URL + encodedName + API_PROPERTIES_URL_PART;
            System.out.println("... Contacting PubChem API...");

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("❌ PubChem API Error: Received HTTP status code " + response.statusCode());
                return null;
            }

            JSONObject root = (JSONObject) parser.parse(response.body());
            JSONObject propertyTable = (JSONObject) root.get("PropertyTable");
            JSONArray properties = (JSONArray) propertyTable.get("Properties");

            if (properties == null || properties.isEmpty()) {
                System.out.println("❌ Compound '" + compoundName + "' not found on PubChem.");
                return null;
            }

            JSONObject compoundProps = (JSONObject) properties.get(0);
            // Use a LinkedHashMap to preserve the order of properties for display
            Map<String, Object> dataMap = new LinkedHashMap<>();
            for (Object key : compoundProps.keySet()) {
                dataMap.put(key.toString(), compoundProps.get(key));
            }
            return dataMap;

        } catch (Exception e) {
            System.out.println("❌ An error occurred during API call: " + e.getMessage());
            return null;
        }
    }
}