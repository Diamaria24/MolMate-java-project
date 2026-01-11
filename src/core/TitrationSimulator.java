package core;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class TitrationSimulator {

    private static final String REPORT_FILE = "composition_report.csv";

    public enum ChemicalType { ACID, BASE }
    public enum ChemicalStrength { STRONG, WEAK }

    public static class Solution {
        private String formula;
        private double molarity;
        private double volume; // in mL
        private ChemicalType type;
        private ChemicalStrength strength;
        private int equivalents; // Number of H+ or OH- ions

        public Solution(String formula, double molarity, double volume) {
            this.formula = formula;
            this.molarity = molarity;
            this.volume = volume;

            ChemicalProperties props = KNOWN_CHEMICALS.get(formula);
            if (props != null) {
                this.type = props.type;
                this.strength = props.strength;
                this.equivalents = props.equivalents;
            } else {
                this.type = ChemicalType.ACID;
                this.strength = ChemicalStrength.STRONG;
                this.equivalents = 1; // default if unknown
            }
        }

        // ✅ Getters
        public String getFormula() { return formula; }
        public double getMolarity() { return molarity; }
        public double getVolume() { return volume; }
        public ChemicalType getType() { return type; }
        public ChemicalStrength getStrength() { return strength; }
        public int getEquivalents() { return equivalents; }

        @Override
        public String toString() {
            return String.format("%.3f M %s (%.1f mL, %s %s, %d equiv.)",
                    molarity, formula, volume, strength, type, equivalents);
        }
    }


    // UPDATED record to include the number of equivalents
    private record ChemicalProperties(ChemicalType type, ChemicalStrength strength, int equivalents) {}

    // UPDATED database with equivalent counts
    private static final Map<String, ChemicalProperties> KNOWN_CHEMICALS = Map.ofEntries(
            Map.entry("HCl", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.STRONG, 1)),
            Map.entry("HBr", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.STRONG, 1)),
            Map.entry("HI", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.STRONG, 1)),
            Map.entry("HNO3", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.STRONG, 1)),
            Map.entry("H2SO4", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.STRONG, 2)), // <-- UPDATED
            Map.entry("HClO4", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.STRONG, 1)),
            Map.entry("NaOH", new ChemicalProperties(ChemicalType.BASE, ChemicalStrength.STRONG, 1)),
            Map.entry("KOH", new ChemicalProperties(ChemicalType.BASE, ChemicalStrength.STRONG, 1)),
            Map.entry("LiOH", new ChemicalProperties(ChemicalType.BASE, ChemicalStrength.STRONG, 1)),
            Map.entry("Ca(OH)2", new ChemicalProperties(ChemicalType.BASE, ChemicalStrength.STRONG, 2)), // <-- UPDATED
            Map.entry("CH3COOH", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.WEAK, 1)),
            Map.entry("NH3", new ChemicalProperties(ChemicalType.BASE, ChemicalStrength.WEAK, 1)),
            Map.entry("HF", new ChemicalProperties(ChemicalType.ACID, ChemicalStrength.WEAK, 1))
    );

    /** --- The main simulation engine, now upgraded for equivalents --- */
    public static void runSimulation(Solution analyte, Solution titrant) {
        if (analyte.strength != ChemicalStrength.STRONG || titrant.strength != ChemicalStrength.STRONG) { /* ... */ return; }
        if (analyte.type == titrant.type) { /* ... */ return; }

        double vAnalyteL = analyte.volume / 1000.0;
        // --- UPGRADED: Calculate initial moles of H+ or OH- IONS ---
        double initialMolesIonsAnalyte = analyte.molarity * vAnalyteL * analyte.equivalents;

        // --- UPGRADED: Equivalence point calculation now uses equivalents ---
        double vEquivL = initialMolesIonsAnalyte / (titrant.molarity * titrant.equivalents);
        double vEquivML = vEquivL * 1000.0;
        System.out.printf("\nℹ️ Equivalence point will be reached at %.2f mL of %s added.\n", vEquivML, titrant.formula);

        Map<Double, Double> curveData = new LinkedHashMap<>();
        double maxVolume = vEquivML * 2.0;
        double increment = Math.max(0.1, vEquivML / 50.0);

        for (double vAddedML = 0; vAddedML <= maxVolume; vAddedML += increment) {
            double pH = calculateStrongAcidStrongBasePh(analyte, titrant, vAddedML);
            curveData.put(vAddedML, pH);
        }
        curveData.put(vEquivML, calculateStrongAcidStrongBasePh(analyte, titrant, vEquivML));

        System.out.println("\n--- Titration Data ---");
        System.out.println("Volume Added (mL) | pH");
        System.out.println("------------------|-------");
        List<Double> sortedVolumes = new ArrayList<>(curveData.keySet());
        Collections.sort(sortedVolumes);
        for (Double vol : sortedVolumes) {
            System.out.printf("%-18.2f|  %.2f\n", vol, curveData.get(vol));
        }

        exportTitrationCurve(curveData, analyte, titrant);
        exportResultToCSV(analyte, titrant, vEquivML);
    }

    /** --- The core pH logic, now upgraded for equivalents --- */
    private static double calculateStrongAcidStrongBasePh(Solution analyte, Solution titrant, double titrantVolumeAddedML) {
        double vAnalyteL = analyte.volume / 1000.0;
        double vTitrantL = titrantVolumeAddedML / 1000.0;
        double totalVolumeL = vAnalyteL + vTitrantL;

        // --- UPGRADED: Calculations are now based on moles of IONS (H+ or OH-) ---
        double initialMolesIonsAnalyte = analyte.molarity * vAnalyteL * analyte.equivalents;
        double molesIonsTitrantAdded = titrant.molarity * vTitrantL * titrant.equivalents;

        if (titrantVolumeAddedML == 0) {
            double ionConcentration = analyte.molarity * analyte.equivalents;
            if (analyte.type == ChemicalType.ACID) return -Math.log10(ionConcentration);
            else { double pOH = -Math.log10(ionConcentration); return 14.0 - pOH; }
        }

        if (Math.abs(molesIonsTitrantAdded - initialMolesIonsAnalyte) < 1e-9) return 7.0;

        if (molesIonsTitrantAdded < initialMolesIonsAnalyte) { // Before Equivalence
            double excessMolesIons = initialMolesIonsAnalyte - molesIonsTitrantAdded;
            double concentration = excessMolesIons / totalVolumeL;
            if (analyte.type == ChemicalType.ACID) return -Math.log10(concentration);
            else { double pOH = -Math.log10(concentration); return 14.0 - pOH; }
        } else { // After Equivalence
            double excessMolesIons = molesIonsTitrantAdded - initialMolesIonsAnalyte;
            double concentration = excessMolesIons / totalVolumeL;
            if (titrant.type == ChemicalType.BASE) { double pOH = -Math.log10(concentration); return 14.0 - pOH; }
            else { return -Math.log10(concentration); }
        }
    }



    /** --- Method to generate the titration curve chart --- */
    private static void exportTitrationCurve(Map<Double, Double> curveData, Solution analyte, Solution titrant) {
        XYSeries series = new XYSeries("Titration Curve");
        List<Double> sortedVolumes = new ArrayList<>(curveData.keySet());
        Collections.sort(sortedVolumes);
        for (Double vol : sortedVolumes) {
            series.add(vol, curveData.get(vol));
        }
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart( "Titration of " + analyte.formula + " with " + titrant.formula, "Volume of " + titrant.formula + " Added (mL)", "pH", dataset);
        String filename = analyte.formula + "_" + titrant.formula + "_titration.png";
        try {
            File file = new File(filename);
            ChartUtils.saveChartAsPNG(file, chart, 800, 600);
            System.out.println("\n📊 Titration curve graph saved to: " + file.getAbsolutePath());
            tryOpenFile(file);
        } catch (IOException e) {
            System.out.println("❌ Failed to save chart: " + e.getMessage());
        }
    }

    /** --- Method to log results to the main CSV report --- */
    private static void exportResultToCSV(Solution analyte, Solution titrant, double equivalenceVolume) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String details = String.format("Analyte: %.2f mL of %.3f M %s. Titrant: %.3f M %s. Equiv. Point: %.2f mL", analyte.volume, analyte.molarity, analyte.formula, titrant.molarity, titrant.formula, equivalenceVolume);
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            pw.printf("%s,Titration,\"%s with %s\",\"%s\"%n", ts, analyte.formula, titrant.formula, details);
        } catch (Exception e) {
            System.out.println("❌ Failed to write titration result to CSV: " + e.getMessage());
        }
        System.out.println("📂 Titration summary added to " + REPORT_FILE);
    }

    /** --- Helper method for auto-opening files --- */
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

    /** The Command-Line Interface. */
    public static void startCLI(Scanner sc) {
        System.out.println("\n=== Interactive Titration Simulator ===");
        try {
            System.out.println("\n--- Analyte Setup (solution in flask) ---");
            System.out.print("Enter analyte formula (e.g., HCl): ");
            String analyteFormula = sc.nextLine().trim();
            System.out.print("Enter analyte volume (mL): ");
            double analyteVolume = Double.parseDouble(sc.nextLine().trim());
            System.out.print("Enter analyte molarity (M): ");
            double analyteMolarity = Double.parseDouble(sc.nextLine().trim());
            Solution analyte = new Solution(analyteFormula, analyteMolarity, analyteVolume);

            System.out.println("\n--- Titrant Setup (solution from buret) ---");
            System.out.print("Enter titrant formula (e.g., NaOH): ");
            String titrantFormula = sc.nextLine().trim();
            System.out.print("Enter titrant molarity (M): ");
            double titrantMolarity = Double.parseDouble(sc.nextLine().trim());
            Solution titrant = new Solution(titrantFormula, titrantMolarity, 0);

            System.out.println("\n--- Running Simulation & Generating Graph ---");
            runSimulation(analyte, titrant);
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid number format. Please try again.");
        } catch (Exception e) {
            System.out.println("❌ An error occurred: " + e.getMessage());
        }
    }
}
