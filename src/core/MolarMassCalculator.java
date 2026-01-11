package core;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MolarMassCalculator {
    private static Map<String, Double> elementMasses = new HashMap<>();
    private static Map<String, String> nameToSymbol = new HashMap<>();
    private static Set<String> validSymbols = new HashSet<>();
    private static final String REPORT_FILE = "composition_report.csv";

    public record MassCalculationResult(double totalMass, String breakdownText, Map<String, Integer> composition) {}


    public static MassCalculationResult calculateWithBreakdown(String formula) {
        loadElements();
        Map<String, Integer> composition = EquationBalancer.parseCompound(formula);
        if (composition.isEmpty()) {
            throw new IllegalArgumentException("Invalid or empty formula.");
        }

        double totalMass = 0.0;
        StringBuilder breakdown = new StringBuilder();
        breakdown.append(String.format("Breakdown for %s:\n", formula));

        for (Map.Entry<String, Integer> entry : composition.entrySet()) {
            String element = entry.getKey();
            int count = entry.getValue();
            double mass = elementMasses.getOrDefault(element, 0.0);
            if (mass == 0) throw new IllegalArgumentException("Unknown element in formula: " + element);

            double contribution = mass * count;
            totalMass += contribution;
            breakdown.append(String.format("  - %-4s: %d atoms × %7.3f u = %8.3f u\n", element, count, mass, contribution));
        }

        breakdown.append("--------------------------------------\n");
        breakdown.append(String.format("Total Molar Mass: %.3f g/mol", totalMass));

        return new MassCalculationResult(totalMass, breakdown.toString(), composition);
    }
    private static void tryOpenFile(File file) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                System.out.println("⚠️ Auto-open failed. Please open the file manually from the path above.");
            }
        } else {
            System.out.println("⚠️ Auto-open is not supported on this system. Please open the file manually.");
        }
    }

    public static JFreeChart createMassBarChart(String formula, Map<String, Integer> composition) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (String el : composition.keySet()) {
            int count = composition.get(el);
            double mass = elementMasses.getOrDefault(el, 0.0);
            dataset.addValue(mass * count, "Mass (g)", el);
        }
        return ChartFactory.createBarChart("Mass Contribution of " + formula, "Element", "Mass (g)", dataset);
    }

    public static JFreeChart createMassPieChart(String formula, Map<String, Integer> composition) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        for (String el : composition.keySet()) {
            int count = composition.get(el);
            double mass = elementMasses.getOrDefault(el, 0.0);
            dataset.setValue(el, mass * count);
        }
        return ChartFactory.createPieChart("Mass Contribution of " + formula, dataset, true, true, false);
    }


    public static void saveChartAsPNG(JFreeChart chart, File file) {
        try {
            ChartUtils.saveChartAsPNG(file, chart, 800, 600);
            System.out.println("✅ Chart saved to: " + file.getAbsolutePath());
            DesktopUtils.tryOpenFile(file); // <-- UPDATED to use the new utility class
        } catch (IOException e) {
            System.out.println("❌ Failed to save chart: " + e.getMessage());
        }
    }



    // Load element data from JSON
    public static void loadElements() {
        if (!elementMasses.isEmpty()) return;

        try {
            InputStream is = MolarMassCalculator.class.getClassLoader()
                    .getResourceAsStream("elements_full_named.json");
            if (is == null) {
                System.out.println("❌ CRITICAL ERROR: Could not find 'elements_full_named.json'. Please make sure it's in a 'resources' folder.");
                return;
            }

            JSONParser parser = new JSONParser();
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);

            JSONObject root = (JSONObject) parser.parse(reader);
            JSONArray elements = (JSONArray) root.get("elements");

            for (Object obj : elements) {
                JSONObject el = (JSONObject) obj;
                String symbol = (String) el.get("symbol");
                String name = (String) el.get("name");
                double mass = Double.parseDouble(el.get("atomic_mass").toString());

                elementMasses.put(symbol, mass);
                validSymbols.add(symbol);
                nameToSymbol.put(name.toLowerCase(), symbol);
            }

            reader.close();
            System.out.println("✅ Loaded elements into MolarMassCalculator.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Compute molar mass + step breakdown
    public static double computeMass(Map<String, Integer> composition, boolean verbose) {
        // This method remains for internal use by other modules
        loadElements();
        double total = 0.0;
        for (String el : composition.keySet()) {
            total += elementMasses.getOrDefault(el, 0.0) * composition.get(el);
        }
        return total;
    }

    // The rest of the file remains the same.
    // ...
    // Levenshtein distance for fuzzy matching
    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    // Suggest closest element (symbol or name)
    private static String suggestElement(String wrong) {
        String suggestion = null;
        int bestDist = Integer.MAX_VALUE;

        for (String sym : validSymbols) {
            int dist = levenshtein(wrong.toLowerCase(), sym.toLowerCase());
            if (dist < bestDist && dist <= 2) {
                bestDist = dist;
                suggestion = sym;
            }
        }

        for (String name : nameToSymbol.keySet()) {
            int dist = levenshtein(wrong.toLowerCase(), name);
            if (dist < bestDist && dist <= 3) {
                bestDist = dist;
                suggestion = nameToSymbol.get(name);
            }
        }
        return suggestion;
    }

    // Parse chemical formula (supports (), [], {})
    public static Map<String, Integer> parseFormula(String formula) {
        formula = formula.replaceAll("\\[", "(").replaceAll("\\]", ")")
                .replaceAll("\\{", "(").replaceAll("\\}", ")");
        Stack<Map<String, Integer>> stack = new Stack<>();
        stack.push(new HashMap<>());
        Matcher m = Pattern.compile("([A-Z][a-z]?|\\(|\\)|\\d+|[A-Z][a-z]?\\-\\d+|[a-zA-Z]+)").matcher(formula);

        while (m.find()) {
            String token = m.group();
            if (token.equals("(")) {
                stack.push(new HashMap<>());
            } else if (token.equals(")")) {
                Map<String, Integer> group = stack.pop();
                int mult = 1;
                if (m.find()) {
                    String next = m.group();
                    if (next.matches("\\d+")) {
                        mult = Integer.parseInt(next);
                    } else {
                        m.region(m.start(), m.regionEnd());
                    }
                }
                for (String k : group.keySet()) {
                    group.put(k, group.get(k) * mult);
                }
                Map<String, Integer> top = stack.peek();
                for (String k : group.keySet())
                    top.put(k, top.getOrDefault(k, 0) + group.get(k));
            } else if (token.matches("[A-Za-z]+(\\-\\d+)?")) {
                String element = token;
                int massShift = 0;
                if (element.contains("-")) {
                    String[] parts = element.split("-");
                    element = parts[0];
                    massShift = Integer.parseInt(parts[1]) - (int) Math.round(elementMasses.getOrDefault(element, 0.0));
                }

                if (nameToSymbol.containsKey(element.toLowerCase())) {
                    element = nameToSymbol.get(element.toLowerCase());
                }

                if (!elementMasses.containsKey(element)) {
                    String suggestion = suggestElement(element);
                    if (suggestion != null) {
                        System.out.println("Unknown element '" + element + "'. Did you mean: " + suggestion + "?");
                        element = suggestion;
                    } else {
                        throw new IllegalArgumentException("Unknown element: " + element);
                    }
                }

                int count = 1;
                if (m.find()) {
                    String next = m.group();
                    if (next.matches("\\d+")) {
                        count = Integer.parseInt(next);
                    } else {
                        m.region(m.start(), m.regionEnd());
                    }
                }
                Map<String, Integer> top = stack.peek();
                top.put(element, top.getOrDefault(element, 0) + count);
                if (massShift != 0) {
                    top.put(element + "_isoShift", massShift * count);
                }
            }
        }
        return stack.pop();
    }


    // === Helper: Log chart exports ===
    private static void logChartExport(String type, String formula, String path) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        boolean fileExists = new File(REPORT_FILE).exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            if (!fileExists) {
                pw.println("Timestamp,Type,Formula,Details");
            }
            pw.printf("%s,%s,%s,%s%n", ts, type, formula, path);
        } catch (IOException e) {
            System.out.println("❌ Failed to log chart export: " + e.getMessage());
        }
    }


    // Export result to unified CSV report
    public static void exportResult(String formula, double mass) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            if (new File(REPORT_FILE).length() == 0) {
                pw.println("Timestamp,Type,Formula,Details");
            }
            pw.printf("%s,Molar Mass,%s,%.3f g/mol%n", ts, formula, mass);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void startCLI(Scanner sc) {
        System.out.println("\n=== Molar Mass Calculator Module ===");
        while (true) {
            System.out.print("\nEnter formula (or 'exit', 'chart mass <formula>', 'chart pie <formula>'): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            try {
                if (input.startsWith("chart mass ")) {
                    String formula = input.substring(11).trim();
                    Map<String, Integer> comp = EquationBalancer.parseCompound(formula);
                    JFreeChart chart = createMassBarChart(formula, comp);
                    saveChartAsPNG(chart, new File(formula + "_mass_bar.png"));
                } else if (input.startsWith("chart pie ")) {
                    String formula = input.substring(10).trim();
                    Map<String, Integer> comp = EquationBalancer.parseCompound(formula);
                    JFreeChart chart = createMassPieChart(formula, comp);
                    saveChartAsPNG(chart, new File(formula + "_mass_pie.png"));
                } else {
                    MassCalculationResult result = calculateWithBreakdown(input);
                    System.out.println(result.breakdownText());
                    exportResult(input, result.totalMass());
                }
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }
        }
    }
}









