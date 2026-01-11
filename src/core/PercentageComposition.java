package core;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import java.awt.Desktop;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.category.DefaultCategoryDataset;

public class PercentageComposition {

    public record CompositionResult(double totalMass, Map<String, Double> elementMasses, String breakdownText) {}

    /**
     * --- NEW: A GUI-friendly method that calculates and returns the data ---
     * @param formula The chemical formula to analyze.
     * @return A CompositionResult object containing all calculated data.
     */


    private static final String REPORT_FILE = "composition_report.csv";


    public static CompositionResult analyzeComposition(String formula) {
        PeriodicTable.loadElements();
        Map<String, Double> elementMasses = new HashMap<>();
        Map<String, Integer> composition = EquationBalancer.parseCompound(formula);
        if (composition.isEmpty()) {
            throw new IllegalArgumentException("Invalid or empty formula.");
        }
        double totalMass = MolarMassCalculator.computeMass(composition, false);
        if (totalMass == 0) {
            throw new IllegalArgumentException("Could not calculate molar mass.");
        }
        for (Map.Entry<String, Integer> entry : composition.entrySet()) {
            elementMasses.put(entry.getKey(), PeriodicTable.getMolarMass(entry.getKey()) * entry.getValue());
        }
        StringBuilder breakdown = new StringBuilder();
        breakdown.append(String.format("Composition of %s (Total Mass: %.3f g/mol)\n", formula, totalMass));
        breakdown.append("--------------------------------------------------\n");
        for (Map.Entry<String, Double> entry : elementMasses.entrySet()) {
            double percentage = (entry.getValue() / totalMass) * 100.0;
            breakdown.append(String.format("  - %-10s: %.2f%%\n", entry.getKey(), percentage));
        }
        return new CompositionResult(totalMass, elementMasses, breakdown.toString());
    }

    public static void appendToMainReport(String formula, CompositionResult result) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        List<String> detailsList = new ArrayList<>();
        for (Map.Entry<String, Double> entry : result.elementMasses().entrySet()) {
            double percentage = (entry.getValue() / result.totalMass()) * 100.0;
            detailsList.add(String.format("%s:%.2f%%", entry.getKey(), percentage));
        }
        String details = String.join("; ", detailsList);

        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) { // 'true' for append
            if (new File(REPORT_FILE).length() == 0) {
                pw.println("Timestamp,Type,Formula,Details");
            }
            pw.printf("%s,Percentage Composition,\"%s\",\"%s\"%n", ts, formula, details);
        } catch (IOException e) {
            System.out.println("❌ Error saving to report file: " + e.getMessage());
        }
    }

    // === Expand common organic chemistry groups ===
    private static String expandOrganicGroups(String formula) {
        Map<String, String> groups = new LinkedHashMap<>();
        groups.put("Me", "CH3");      // Methyl
        groups.put("Et", "C2H5");     // Ethyl
        groups.put("Ph", "C6H5");     // Phenyl
        groups.put("COOH", "CO2H");   // Carboxyl
        groups.put("OH", "O1H1");     // Hydroxyl
        groups.put("NH2", "N1H2");    // Amine

        for (String key : groups.keySet()) {
            formula = formula.replace(key, groups.get(key));
        }
        return formula;
    }

    // === Main calculation ===
    // === Main calculation ===
    public static void calculate(String formula) {
        try {
            CompositionResult result = analyzeComposition(formula);
            System.out.println("\n" + result.breakdownText());

            // --- THIS IS THE PART THAT SAVES TO THE MAIN REPORT FILE ---
            List<String> csvDetails = new ArrayList<>();
            for (Map.Entry<String, Double> entry : result.elementMasses().entrySet()) {
                double percentage = (entry.getValue() / result.totalMass()) * 100.0;
                csvDetails.add(String.format("%s:%.2f%%", entry.getKey(), percentage));
            }
            appendToCSV(formula, String.join("; ", csvDetails));

        } catch (Exception e) {
            System.out.println("❌ Could not calculate composition: " + e.getMessage());
        }
    }

    private static void appendToCSV(String formula, String details) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            if (new File(REPORT_FILE).length() == 0) {
                pw.println("Timestamp,Type,Formula,Details");
            }
            pw.printf("%s,Percentage Composition,%s,\"%s\"%n", ts, formula, details);
        } catch (IOException e) {
            System.out.println("❌ Error saving to report file: " + e.getMessage());
        }
    }

    // === Parser ===
    private static double parseFormula(String formula, Map<String, Double> elementMasses) {
        formula = expandOrganicGroups(formula);
        double totalMass = 0.0;
        String regex = "(\\([A-Za-z0-9]+\\)\\d*|\\[[A-Za-z0-9]+\\]\\d*|\\{[A-Za-z0-9]+\\}\\d*|[A-Z][a-z]?\\d*)";
        Matcher matcher = Pattern.compile(regex).matcher(formula);

        while (matcher.find()) {
            String group = matcher.group(1);

            if (group.startsWith("(") || group.startsWith("[") || group.startsWith("{")) {
                char open = group.charAt(0);
                char close = (open == '(') ? ')' : (open == '[' ? ']' : '}');
                int closingIndex = group.indexOf(close);
                String inside = group.substring(1, closingIndex);
                String multiplierStr = group.substring(closingIndex + 1);
                int multiplier = multiplierStr.isEmpty() ? 1 : Integer.parseInt(multiplierStr);
                totalMass += parseFormula(inside, elementMasses) * multiplier;
            } else {
                Matcher elementMatcher = Pattern.compile("([A-Z][a-z]?)(\\d*)").matcher(group);
                if (elementMatcher.find()) {
                    String element = elementMatcher.group(1);
                    int count = elementMatcher.group(2).isEmpty() ? 1 : Integer.parseInt(elementMatcher.group(2));

                    double mass = PeriodicTable.getMolarMass(element);
                    if (mass == 0.0) {
                        String suggestion = PeriodicTable.getClosestMatch(element);
                        if (suggestion != null) {
                            System.out.println("⚠️ Unknown element: " + element + " → Did you mean: " + suggestion + " ?");
                        } else {
                            System.out.println("⚠️ Unknown element: " + element);
                        }
                    }

                    double elementTotalMass = mass * count;
                    totalMass += elementTotalMass;
                    elementMasses.put(element, elementMasses.getOrDefault(element, 0.0) + elementTotalMass);
                }
            }
        }
        return totalMass;
    }

    // === Append unified CSV ===
    private static void appendToCSV(String timestamp, String type, String formula, String details) {
        boolean fileExists = new File(REPORT_FILE).exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            if (!fileExists) {
                pw.println("Timestamp,Type,Formula,Details");
            }
            pw.printf("%s,%s,%s,%s%n", timestamp, type, formula, details);
            System.out.println("📂 Results added to " + REPORT_FILE);
        } catch (IOException e) {
            System.out.println("❌ Error saving file: " + e.getMessage());
        }
    }

    // === Export single compound CSV (detailed breakdown) ===
    public static void exportCSV(String formula, String outputFile, double totalMass, Map<String, Double> elementMasses) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputFile))) {
            pw.println("Formula:," + formula);
            pw.printf("Total molar mass:,%.3f g/mol%n", totalMass);
            pw.println("\nElement,Mass Contribution (g),Percentage");
            for (String el : elementMasses.keySet()) {
                double mass = elementMasses.get(el);
                double percentage = (mass / totalMass) * 100;
                pw.printf("%s,%.3f,%.2f%%%n", el, mass, percentage);
            }
            System.out.println("✅ Exported composition of " + formula + " to " + outputFile);
            DesktopUtils.tryOpenFile(new File(outputFile));
        } catch (IOException e) {
            System.out.println("❌ Export failed: " + e.getMessage());
        }
    }

    public static JFreeChart createPieChart(String formula, double totalMass, Map<String, Double> elementMasses) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        for (String el : elementMasses.keySet()) {
            dataset.setValue(el, (elementMasses.get(el) / totalMass) * 100);
        }
        return ChartFactory.createPieChart("Composition of " + formula, dataset, true, true, false);
    }

    public static JFreeChart createBarChart(String formula, double totalMass, Map<String, Double> elementMasses) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (String el : elementMasses.keySet()) {
            dataset.addValue((elementMasses.get(el) / totalMass) * 100, "Percentage", el);
        }
        return ChartFactory.createBarChart("Composition of " + formula, "Element", "Percentage (%)", dataset);
    }

    public static JFreeChart createStackedBarChart(String formula, double totalMass, Map<String, Double> elementMasses) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (String el : elementMasses.keySet()) {
            double mass = elementMasses.get(el);
            double percentage = (mass / totalMass) * 100;
            dataset.addValue(mass, "Mass (g)", el);
            dataset.addValue(percentage, "Percentage (%)", el);
        }
        return ChartFactory.createStackedBarChart("Composition of " + formula, "Element", "Value", dataset);
    }




    // === Export Pie Chart ===
    public static void exportPieChart(String formula, String outputFile) {
        PeriodicTable.loadElements();
        Map<String, Double> elementMasses = new HashMap<>();
        double totalMass = parseFormula(formula, elementMasses);

        DefaultPieDataset dataset = new DefaultPieDataset();
        for (String el : elementMasses.keySet()) {
            double percentage = (elementMasses.get(el) / totalMass) * 100;
            dataset.setValue(el, percentage);
        }

        JFreeChart chart = ChartFactory.createPieChart(
                "Composition of " + formula,
                dataset,
                true, true, false);

        try {
            ChartUtils.saveChartAsPNG(new File(outputFile), chart, 600, 400);
            System.out.println("✅ Pie chart saved to " + outputFile);
            openFile(outputFile);

        } catch (IOException e) {
            System.out.println("❌ Pie chart export failed: " + e.getMessage());
        }
    }

    // === Export Bar Chart ===
    public static void exportBarChart(String formula, String outputFile) {
        PeriodicTable.loadElements();
        Map<String, Double> elementMasses = new HashMap<>();
        double totalMass = parseFormula(formula, elementMasses);

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (String el : elementMasses.keySet()) {
            double percentage = (elementMasses.get(el) / totalMass) * 100;
            dataset.addValue(percentage, "Percentage", el);
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Composition of " + formula,
                "Element", "Percentage (%)",
                dataset);

        try {
            ChartUtils.saveChartAsPNG(new File(outputFile), chart, 600, 400);
            System.out.println("✅ Bar chart saved to " + outputFile);
            openFile(outputFile);

        } catch (IOException e) {
            System.out.println("❌ Bar chart export failed: " + e.getMessage());
        }
    }

    // === Export Stacked Bar Chart ===
    public static void exportStackedBarChart(String formula, String outputFile) {
        PeriodicTable.loadElements();
        Map<String, Double> elementMasses = new HashMap<>();
        double totalMass = parseFormula(formula, elementMasses);

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (String el : elementMasses.keySet()) {
            double mass = elementMasses.get(el);
            double percentage = (mass / totalMass) * 100;
            dataset.addValue(mass, "Mass (g)", el);
            dataset.addValue(percentage, "Percentage (%)", el);
        }

        JFreeChart chart = ChartFactory.createStackedBarChart(
                "Composition of " + formula,
                "Element", "Value",
                dataset);

        try {
            ChartUtils.saveChartAsPNG(new File(outputFile), chart, 800, 500);
            System.out.println("✅ Stacked bar chart saved to " + outputFile);
            openFile(outputFile);

        } catch (IOException e) {
            System.out.println("❌ Stacked chart export failed: " + e.getMessage());
        }
    }

    // === Auto-open file ===
    private static void openFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not auto-open file: " + e.getMessage());
        }
    }
    // === Helper for safe filenames ===
    public static String safeFileName(String formula) {
        return formula.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

}




