package core;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class EmpiricalFormulaCalculator {

    private static final String REPORT_FILE = "composition_report.csv";

    // A record to hold both the final formula and the detailed breakdown
    public record CalculationResult(String finalFormula, String breakdownText) {}

    /**
     * Calculates the empirical formula and generates a step-by-step breakdown.
     */
    public static CalculationResult calculateWithBreakdown(Map<String, Double> composition) {
        if (composition == null || composition.isEmpty()) {
            throw new IllegalArgumentException("Composition map cannot be empty.");
        }

        StringBuilder breakdown = new StringBuilder();
        breakdown.append("--- Step-by-Step Calculation ---\n");

        // Step 1: Convert mass of each element to moles
        breakdown.append("\nStep 1: Convert mass of each element to moles\n");
        Map<String, Double> moles = new HashMap<>();
        for (String el : composition.keySet()) {
            double mass = composition.get(el);
            double molarMass = PeriodicTable.getMolarMass(el);
            if (molarMass == 0) throw new IllegalArgumentException("Unknown element: " + el);
            double moleValue = mass / molarMass;
            moles.put(el, moleValue);
            breakdown.append(String.format("  - %-2s: %.2f g / %.3f g/mol = %.4f moles\n", el, mass, molarMass, moleValue));
        }

        // Step 2: Divide all mole values by the smallest value
        breakdown.append("\nStep 2: Divide all mole values by the smallest value\n");
        double minMole = Collections.min(moles.values());
        breakdown.append(String.format("  (Smallest mole value is %.4f)\n", minMole));
        Map<String, Double> rawRatios = new HashMap<>();
        boolean needsMultiplying = false;

        for (String el : moles.keySet()) {
            double ratio = moles.get(el) / minMole;
            rawRatios.put(el, ratio);
            breakdown.append(String.format("  - %-2s: %.4f / %.4f = %.2f\n", el, moles.get(el), minMole, ratio));
            if (Math.abs(ratio - Math.round(ratio)) > 0.4 && Math.abs(ratio - Math.round(ratio)) < 0.6) {
                needsMultiplying = true;
            }
        }

        // Step 3 (Conditional): Multiply to get whole numbers
        if (needsMultiplying) {
            breakdown.append("\nStep 3: Multiply all ratios by 2 to get whole numbers\n");
            for (String el : rawRatios.keySet()) {
                double oldRatio = rawRatios.get(el);
                double newRatio = oldRatio * 2;
                rawRatios.put(el, newRatio);
                breakdown.append(String.format("  - %-2s: %.2f * 2 = %.2f\n", el, oldRatio, newRatio));
            }
        }

        // Step 4: Build the final formula string
        StringBuilder formula = new StringBuilder();
        List<String> sortedElements = new ArrayList<>(rawRatios.keySet());
        sortedElements.sort((a,b) -> {
            if (a.equals("C")) return -1; if(b.equals("C")) return 1;
            if (a.equals("H")) return -1; if(b.equals("H")) return 1;
            return a.compareTo(b);
        });

        for (String el : sortedElements) {
            int finalRatio = (int) Math.round(rawRatios.get(el));
            formula.append(el);
            if (finalRatio > 1) {
                formula.append(finalRatio);
            }
        }

        breakdown.append("\n----------------------------------\n");
        breakdown.append("Final Formula: ").append(formula.toString());

        return new CalculationResult(formula.toString(), breakdown.toString());
    }

    public static void exportResultToCSV(String formula, Map<String, Double> composition) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        StringBuilder details = new StringBuilder();
        for(Map.Entry<String, Double> entry : composition.entrySet()) {
            details.append(String.format("%s %.2f%%; ", entry.getKey(), entry.getValue()));
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            if (new File(REPORT_FILE).length() == 0) pw.println("Timestamp,Type,Formula,Details");
            pw.printf("%s,Empirical Formula,%s,\"%s\"%n", ts, formula, details.toString());

            // --- THE FIX: Added confirmation message ---
            System.out.println("📂 Result added to " + REPORT_FILE);

        } catch (Exception e) {
            System.out.println("❌ Failed to write CSV: " + e.getMessage());
        }
    }

    public static void startCLI(Scanner sc) {
        System.out.println("\n=== Empirical Formula Calculator ===");
        System.out.println("Enter elements with masses/percentages one by one (e.g., C 40.0).");
        System.out.println("Type 'done' to calculate, or 'exit' to return.");
        Map<String, Double> composition = new LinkedHashMap<>();
        while (true) {
            System.out.print("Element and mass/percent (or 'done'/'exit'): ");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("exit")) return;
            if (line.equalsIgnoreCase("done")) break;
            String[] parts = line.split("\\s+");
            if (parts.length == 2) {
                try {
                    composition.put(parts[0], Double.parseDouble(parts[1]));
                    System.out.println("  -> Added " + parts[0] + ": " + parts[1]);
                } catch (NumberFormatException e) {
                    System.out.println("⚠️ Invalid number format.");
                }
            } else {
                System.out.println("⚠️ Invalid format.");
            }
        }
        if (composition.isEmpty()) {
            System.out.println("⚠️ No data entered.");
            return;
        }
        try {
            CalculationResult result = calculateWithBreakdown(composition);
            System.out.println("\n" + result.breakdownText());
            exportResultToCSV(result.finalFormula(), composition);
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }
}


