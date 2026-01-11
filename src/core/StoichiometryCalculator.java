package core;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

public class StoichiometryCalculator {

    private static final String REPORT_FILE = "composition_report.csv";
    private static final double MOLAR_VOLUME_STP = 22.4; // liters per mole

    // Private record to hold reactant input data for clarity
    private record ReactantInput(String formula, double amount, String unit) {}

    // --- Main Calculation Engine ---
    public static void calculateLimitingReactant(String equation, List<ReactantInput> inputs, String findCompound, String findUnit) {
        MolarMassCalculator.loadElements();
        String balanced = EquationBalancer.balance(equation);
        System.out.println("✅ Balanced: " + balanced);
        Map<String, Integer> coeffs = EquationBalancer.parseCoefficients(balanced);

        // Validation checks...
        for(ReactantInput input : inputs) { if (!coeffs.containsKey(input.formula())) { System.out.println("❌ Error: Reactant '" + input.formula() + "' not found in the equation."); return; } }
        if (!coeffs.containsKey(findCompound)) { System.out.println("❌ Error: Product '" + findCompound + "' not found in the equation."); return; }

        Map<String, Double> initialMoles = new HashMap<>();
        for (ReactantInput input : inputs) {
            double moles = getMolesFromInput(input);
            if (moles < 0) return;
            initialMoles.put(input.formula(), initialMoles.getOrDefault(input.formula(), 0.0) + moles);
        }

        double minYieldInMoles = Double.POSITIVE_INFINITY;
        String limitingReactant = null;
        int coeffFind = coeffs.get(findCompound);
        for (ReactantInput input : inputs) {
            double molesOfThisReactant = initialMoles.get(input.formula());
            int coeffReactant = coeffs.get(input.formula());
            double potentialYield = molesOfThisReactant * ((double) coeffFind / coeffReactant);
            if (potentialYield < minYieldInMoles) {
                minYieldInMoles = potentialYield;
                limitingReactant = input.formula();
            }
        }

        System.out.println("\n--- Limiting Reactant & Yield ---");
        if (limitingReactant == null) { System.out.println("❌ Could not determine limiting reactant."); return; }
        System.out.println("👉 Limiting Reactant is: " + limitingReactant);
        double theoreticalYieldAmount = getAmountFromMoles(minYieldInMoles, findCompound, findUnit);
        if (theoreticalYieldAmount < 0) return;
        System.out.printf("👉 Theoretical Yield: %.3f %s of %s%n", theoreticalYieldAmount, findUnit, findCompound);

        // --- IMPROVED: Full Reaction Summary Table Logic ---
        System.out.println("\n--- Full Reaction Summary ---");
        System.out.println("Based on the reaction with " + limitingReactant + " as the limiting reactant:");
        String header = String.format("%-12s | %-10s | %-12s | %-12s | %-12s | %-12s", "Compound", "Role", "Initial (g)", "Consumed (g)", "Excess (g)", "Produced (g)");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        String[] sides = balanced.split("->");
        String leftSide = sides[0];

        for (String compound : coeffs.keySet()) {
            int coeffCompound = coeffs.get(compound);
            double reactedMoles = minYieldInMoles * ((double) coeffCompound / coeffFind);
            double reactedMass = getAmountFromMoles(reactedMoles, compound, "g");

            String role = leftSide.contains(compound) ? "Reactant" : "Product";

            if (role.equals("Reactant")) {
                if (initialMoles.containsKey(compound)) {
                    // This is a reactant the user provided an amount for.
                    double initialMass = getAmountFromMoles(initialMoles.get(compound), compound, "g");
                    double excessMass = initialMass - reactedMass;
                    excessMass = Math.max(0, excessMass);
                    System.out.printf("%-12s | %-10s | %-12.2f | %-12.2f | %-12.2f | %-12s%n", compound, role, initialMass, reactedMass, excessMass, "---");
                } else {
                    // This is a reactant required by the equation but not provided by the user (assumed excess).
                    System.out.printf("%-12s | %-10s | %-12s | %-12.2f | %-12s | %-12s%n", compound, role, "(Excess)", reactedMass, "---", "---");
                }
            } else { // It's a product
                System.out.printf("%-12s | %-10s | %-12s | %-12s | %-12s | %-12.2f%n", compound, role, "---", "---", "---", reactedMass);
            }
        }

        String finalOutput = String.format("Theoretical yield of %s is %.3f %s, limited by %s.", findCompound, theoreticalYieldAmount, findUnit, limitingReactant);
        exportResult(equation, balanced, finalOutput);
        exportMoleRatioChart(coeffs, "stoichiometry_ratios.png");
        exportMoleMassStackedChart(coeffs, "stoichiometry_stacked.png");
    }

    // --- CLI to handle multiple reactants ---
    public static void startCLI(Scanner sc) {
        System.out.println("\n=== Stoichiometry Calculator ===");
        System.out.println("Enter equation (unbalanced allowed): ");
        String eq = sc.nextLine().trim();
        if (eq.isEmpty()) return;
        List<ReactantInput> reactants = new ArrayList<>();
        while (true) {
            System.out.print("Enter a reactant and its amount (e.g., H2 5.0 g), or type 'done' to continue: ");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("done")) {
                if (reactants.isEmpty()) { System.out.println("❌ You must enter at least one reactant."); continue; }
                break;
            }
            Pattern pattern = Pattern.compile("([A-Za-z0-9()]+)\\s+([0-9.]+)\\s*(g|mol|L|l)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(line);
            if (!matcher.matches()) { System.out.println("❌ Invalid format. Please use: <Formula> <Amount> <Unit(g, mol, L)>"); continue; }
            String formula = matcher.group(1);
            double amount = Double.parseDouble(matcher.group(2));
            String unit = matcher.group(3);
            reactants.add(new ReactantInput(formula, amount, unit));
        }
        System.out.print("Find compound (product to calculate yield for): ");
        String findCompound = sc.nextLine().trim();
        System.out.print("Output unit for yield (mol, g, or L): ");
        String outUnit = sc.nextLine().trim();
        calculateLimitingReactant(eq, reactants, findCompound, outUnit);
    }

    // Helper methods...
    private static double getMolesFromInput(ReactantInput input) { if (input.unit().equalsIgnoreCase("mol")) { return input.amount(); } else if (input.unit().equalsIgnoreCase("g")) { double molarMass = getMolarMass(input.formula()); if (molarMass == 0) return -1; return input.amount() / molarMass; } else if (input.unit().equalsIgnoreCase("l")) { return input.amount() / MOLAR_VOLUME_STP; } System.out.println("❌ Unsupported input unit: " + input.unit()); return -1; }
    private static double getAmountFromMoles(double moles, String formula, String unit) { if (unit.equalsIgnoreCase("mol")) { return moles; } else if (unit.equalsIgnoreCase("g")) { double molarMass = getMolarMass(formula); if (molarMass == 0) return -1; return moles * molarMass; } else if (unit.equalsIgnoreCase("l")) { return moles * MOLAR_VOLUME_STP; } System.out.println("❌ Unsupported output unit: " + unit); return -1; }
    private static double getMolarMass(String formula) { try { Map<String, Integer> comp = EquationBalancer.parseCompound(formula); return MolarMassCalculator.computeMass(comp, false); } catch (Exception e) { System.out.println("❌ Error calculating molar mass for " + formula + ": " + e.getMessage()); return 0; } }
    private static void tryOpenFile(File file) { if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) { try { Desktop.getDesktop().open(file); } catch (IOException e) { System.out.println("⚠️ Auto-open failed. Please open the file manually from the path above."); } } else { System.out.println("⚠️ Auto-open is not supported on this system. Please open the file manually."); } }
    public static void exportMoleRatioChart(Map<String, Integer> coeffs, String filename) { try { MolarMassCalculator.loadElements(); DefaultCategoryDataset dataset = new DefaultCategoryDataset(); for (Map.Entry<String, Integer> entry : coeffs.entrySet()) dataset.addValue(entry.getValue(), "Mole Ratio", entry.getKey()); JFreeChart chart = ChartFactory.createBarChart("Mole Ratio Chart", "Compound", "Moles", dataset); File file = new File(filename); ChartUtils.saveChartAsPNG(file, chart, 800, 600); System.out.println("📊 Mole Ratio Chart saved to: " + file.getAbsolutePath()); tryOpenFile(file); } catch (Exception e) { System.out.println("❌ Failed to export Mole Ratio Chart: " + e.getMessage()); } }
    public static void exportMoleMassStackedChart(Map<String, Integer> coeffs, String filename) { try { MolarMassCalculator.loadElements(); DefaultCategoryDataset dataset = new DefaultCategoryDataset(); for (Map.Entry<String, Integer> entry : coeffs.entrySet()) { String comp = entry.getKey(); int coeff = entry.getValue(); double molarMass = getMolarMass(comp); dataset.addValue(coeff, "Molar Equivalent (moles)", comp); dataset.addValue(coeff * molarMass, "Mass Equivalent (grams)", comp); } JFreeChart chart = ChartFactory.createStackedBarChart("Molar vs Mass Equivalents in Balanced Equation", "Compounds", "Value", dataset); File file = new File(filename); ChartUtils.saveChartAsPNG(file, chart, 800, 600); System.out.println("📊 Mole+Mass Stacked Chart saved to: " + file.getAbsolutePath()); tryOpenFile(file); } catch (Exception e) { System.out.println("❌ Failed to export Mole+Mass Stacked Chart: " + e.getMessage()); } }
    private static void exportResult(String inputEq, String balancedEq, String details) { String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()); boolean fileExists = new File(REPORT_FILE).exists(); try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) { if (!fileExists) pw.println("Timestamp,Type,Formula,Details,Phases"); Map<String, Integer> coeffs = EquationBalancer.parseCoefficients(balancedEq); StringBuilder phases = new StringBuilder(); for (String comp : coeffs.keySet()) { String phase = PeriodicTable.getCompoundState(comp); if (phase != null) phases.append(comp).append("=").append(phase).append("; "); } pw.printf("%s,Stoichiometry,\"%s\",\"%s\",\"%s\"%n", ts, balancedEq, details, phases.toString().trim()); } catch (Exception e) { System.out.println("❌ Failed to write CSV: " + e.getMessage()); } System.out.println("📂 Stoichiometry result (with phases) added to " + REPORT_FILE); }
}
    // === CLI Method ===












