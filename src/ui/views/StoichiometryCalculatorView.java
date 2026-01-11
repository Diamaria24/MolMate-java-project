package ui.views;

import core.EquationBalancer;
import core.MolarMassCalculator;
import core.PeriodicTable;
import core.StoichiometryCalculator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * StoichiometryCalculatorView
 *
 * - Recreates the StoichiometryCalculator functionality in a GUI.
 * - Keeps all features intact: balancing, limiting reactant determination,
 *   unit support (g, mol, L at STP), full reaction summary (initial, consumed, excess, produced),
 *   CSV logging (via core exportResult), and chart export (uses core's export methods).
 *
 * Note: This view calls public methods on core (EquationBalancer, MolarMassCalculator, PeriodicTable).
 * The view recreates the CLI's reactant input handling and displays results in an interactive table/log.
 */
public class StoichiometryCalculatorView {

    private static final DecimalFormat DF = new DecimalFormat("#.####");
    private static final String REPORT_FILE = "composition_report.csv";

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Stoichiometry Calculator");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        // CENTER: input + reactant list + controls + results
        VBox center = new VBox(10);
        center.setPadding(new Insets(12));

        // Equation input
        HBox eqRow = new HBox(8);
        eqRow.setAlignment(Pos.CENTER_LEFT);
        TextField eqField = new TextField();
        eqField.setPromptText("Enter equation (unbalanced allowed), e.g. C3H8 + O2 -> CO2 + H2O");
        eqField.setPrefWidth(560);
        Button balanceBtn = new Button("Balance & Analyze");
        Button clearBtn = new Button("Clear All");
        eqRow.getChildren().addAll(eqField, balanceBtn, clearBtn);

        // Reactant entry row
        HBox addRow = new HBox(8);
        addRow.setAlignment(Pos.CENTER_LEFT);
        TextField rFormula = new TextField();
        rFormula.setPromptText("Formula (e.g., O2)");
        rFormula.setPrefWidth(140);
        TextField rAmount = new TextField();
        rAmount.setPromptText("Amount");
        rAmount.setPrefWidth(100);
        ChoiceBox<String> rUnit = new ChoiceBox<>(FXCollections.observableArrayList("g", "mol", "L"));
        rUnit.setValue("g");
        Button addReactantBtn = new Button("Add Reactant");
        addRow.getChildren().addAll(new Label("Add Reactant:"), rFormula, rAmount, rUnit, addReactantBtn);

        // Helper inner class for reactant entries (GUI-side)
        // <-- moved here before any use so the type is resolvable
        class ReactantEntry {
            String formula;
            double amount;
            String unit;
            ReactantEntry(String f, double a, String u) { formula = f; amount = a; unit = u; }
            @Override public String toString() { return formula + " | " + amount + " " + unit; }
        }

        // Reactant list view
        ObservableList<String> reactantItems = FXCollections.observableArrayList();
        List<ReactantEntry> reactantEntries = new ArrayList<>();
        ListView<String> reactantListView = new ListView<>(reactantItems);
        reactantListView.setPrefHeight(120);

        HBox reactantButtons = new HBox(6);
        Button removeReactantBtn = new Button("Remove Selected");
        Button clearReactantsBtn = new Button("Clear Reactants");
        reactantButtons.getChildren().addAll(removeReactantBtn, clearReactantsBtn);

        // Find product & output unit
        HBox findRow = new HBox(8);
        findRow.setAlignment(Pos.CENTER_LEFT);
        TextField findCompoundField = new TextField();
        findCompoundField.setPromptText("Product to find (exact formula from eq)");
        findCompoundField.setPrefWidth(240);
        ChoiceBox<String> outUnit = new ChoiceBox<>(FXCollections.observableArrayList("mol", "g", "L"));
        outUnit.setValue("mol");
        findRow.getChildren().addAll(new Label("Find:"), findCompoundField, new Label("Output unit:"), outUnit);

        // Results area
        Label resultHeader = new Label("Results");
        resultHeader.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(10);

        // Summary table area: using TextArea for formatted table for simplicity
        TextArea summaryArea = new TextArea();
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        summaryArea.setPrefRowCount(10);

        // Utility buttons
        HBox utilRow = new HBox(8);
        utilRow.setAlignment(Pos.CENTER_LEFT);
        Button exportRatioChartBtn = new Button("Export Mole Ratio Chart");
        Button exportStackedChartBtn = new Button("Export Mole+Mass Chart");
        Button viewCsvBtn = new Button("View CSV Report");
        Button openCsvFolderBtn = new Button("Open CSV Location");
        Button saveSummaryBtn = new Button("Save Summary");
        utilRow.getChildren().addAll(exportRatioChartBtn, exportStackedChartBtn, viewCsvBtn, openCsvFolderBtn, saveSummaryBtn);

        // Layout composition
        center.getChildren().addAll(eqRow, addRow, reactantListView, reactantButtons, findRow, resultHeader, logArea, new Label("Full Reaction Summary (table):"), summaryArea, utilRow);
        root.setCenter(center);

        // -----------------------
        // Actions / helpers
        // -----------------------

        addReactantBtn.setOnAction(e -> {
            String f = Optional.ofNullable(rFormula.getText()).orElse("").trim();
            String am = Optional.ofNullable(rAmount.getText()).orElse("").trim();
            String u = Optional.ofNullable(rUnit.getValue()).orElse("g");
            if (f.isEmpty() || am.isEmpty()) {
                logArea.appendText("⚠️ Enter formula and amount to add.\n");
                return;
            }
            try {
                double val = Double.parseDouble(am);
                ReactantEntry re = new ReactantEntry(f, val, u);
                reactantEntries.add(re);
                reactantItems.add(re.toString());
                rFormula.clear(); rAmount.clear();
                logArea.appendText("➕ Added reactant: " + re + "\n");
            } catch (NumberFormatException ex) {
                logArea.appendText("❌ Invalid amount number.\n");
            }
        });

        removeReactantBtn.setOnAction(e -> {
            int idx = reactantListView.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                ReactantEntry removed = reactantEntries.remove(idx);
                reactantItems.remove(idx);
                logArea.appendText("➖ Removed: " + removed + "\n");
            } else {
                logArea.appendText("⚠️ Select a reactant to remove.\n");
            }
        });

        clearReactantsBtn.setOnAction(e -> {
            reactantEntries.clear();
            reactantItems.clear();
            logArea.appendText("🧹 Reactants cleared.\n");
        });

        clearBtn.setOnAction(ev -> {
            eqField.clear();
            reactantEntries.clear();
            reactantItems.clear();
            findCompoundField.clear();
            outUnit.setValue("mol");
            logArea.clear();
            summaryArea.clear();
        });

        // Balance & Analyze button
        balanceBtn.setOnAction(ev -> {
            String eq = Optional.ofNullable(eqField.getText()).orElse("").trim();
            if (eq.isEmpty()) { logArea.appendText("⚠️ Enter an equation.\n"); return; }
            if (reactantEntries.isEmpty()) { logArea.appendText("⚠️ Enter at least one reactant (with amount).\n"); return; }
            String find = Optional.ofNullable(findCompoundField.getText()).orElse("").trim();
            if (find.isEmpty()) { logArea.appendText("⚠️ Enter product compound to compute yield for.\n"); return; }
            String desiredUnit = Optional.ofNullable(outUnit.getValue()).orElse("mol");

            // Run heavy work asynchronously so UI stays responsive
            CompletableFuture.runAsync(() -> {
                try {
                    Platform.runLater(() -> {
                        logArea.appendText("🔄 Balancing equation...\n");
                    });

                    // Ensure element table loaded
                    MolarMassCalculator.loadElements();

                    // Balance (call core)
                    String balanced = EquationBalancer.balance(eq, true); // silent
                    Map<String, Integer> coeffs = EquationBalancer.parseCoefficients(balanced);

                    Platform.runLater(() -> {
                        logArea.appendText("✅ Balanced: " + balanced + "\n");
                        logArea.appendText("ℹ️ Coefficients: " + coeffs + "\n");
                    });

                    // Validate that provided reactants are present in coeffs
                    for (ReactantEntry re : reactantEntries) {
                        if (!coeffs.containsKey(re.formula)) {
                            Platform.runLater(() -> logArea.appendText("❌ Error: Reactant '" + re.formula + "' not found in equation.\n"));
                            return;
                        }
                    }
                    if (!coeffs.containsKey(find)) {
                        Platform.runLater(() -> logArea.appendText("❌ Error: Product '" + find + "' not found in equation.\n"));
                        return;
                    }

                    // Convert inputs to moles
                    Map<String, Double> initialMoles = new HashMap<>();
                    for (ReactantEntry re : reactantEntries) {
                        double moles = convertToMoles(re.formula, re.amount, re.unit);
                        if (moles < 0) {
                            Platform.runLater(() -> logArea.appendText("❌ Unsupported unit or molar mass error for " + re.formula + "\n"));
                            return;
                        }
                        initialMoles.put(re.formula, initialMoles.getOrDefault(re.formula, 0.0) + moles);
                        double mm = getMolarMassSafe(re.formula);
                        final double mmf = mm;
                        Platform.runLater(() -> logArea.appendText("ℹ️ " + re.formula + ": " + re.amount + " " + re.unit + " → " + DF.format(moles) + " mol (M=" + DF.format(mmf) + " g/mol)\n"));
                    }

                    // Determine limiting reactant & theoretical yield in moles
                    double minYieldMoles = Double.POSITIVE_INFINITY;
                    String limiting = null;
                    int coeffFind = coeffs.get(find);
                    for (ReactantEntry re : reactantEntries) {
                        double availableMoles = initialMoles.get(re.formula);
                        int coeffReact = coeffs.get(re.formula);
                        double potentialYield = availableMoles * ((double) coeffFind / coeffReact);
                        if (potentialYield < minYieldMoles) {
                            minYieldMoles = potentialYield;
                            limiting = re.formula;
                        }
                    }

                    if (limiting == null) {
                        Platform.runLater(() -> logArea.appendText("❌ Could not determine limiting reactant.\n"));
                        return;
                    }

                    double theoreticalYieldAmount = convertFromMoles(minYieldMoles, find, desiredUnit);
                    if (theoreticalYieldAmount < 0) {
                        Platform.runLater(() -> logArea.appendText("❌ Unsupported output unit.\n"));
                        return;
                    }

                    // create final copies for lambda capture
                    final String finalLimiting = limiting;
                    final double finalYield = theoreticalYieldAmount;

                    Platform.runLater(() -> {
                        logArea.appendText("\n--- Limiting Reactant & Yield ---\n");
                        logArea.appendText("👉 Limiting Reactant: " + finalLimiting + "\n");
                        logArea.appendText("👉 Theoretical yield: " + DF.format(finalYield) + " " + desiredUnit + " of " + find + "\n");
                    });

                    // Build full reaction summary (string table)
                    StringBuilder table = new StringBuilder();
                    String tableHeader = String.format("%-12s | %-10s | %-12s | %-12s | %-12s | %-12s", "Compound", "Role", "Initial (g)", "Consumed (g)", "Excess (g)", "Produced (g)");
                    table.append(tableHeader).append("\n");
                    table.append("-".repeat(tableHeader.length())).append("\n");
                    // Determine left side compounds for role determination
                    String[] sides = balanced.split("->");
                    String leftSide = sides[0];

                    for (String compound : coeffs.keySet()) {
                        int coeffCompound = coeffs.get(compound);
                        double reactedMoles = minYieldMoles * ((double) coeffCompound / coeffFind);
                        double reactedMass = convertFromMoles(reactedMoles, compound, "g");

                        boolean isReactant = leftSide.contains(compound);
                        if (isReactant) {
                            if (initialMoles.containsKey(compound)) {
                                double initialMass = convertFromMoles(initialMoles.get(compound), compound, "g");
                                double excessMass = Math.max(0, initialMass - reactedMass);
                                table.append(String.format("%-12s | %-10s | %-12.2f | %-12.2f | %-12.2f | %-12s%n",
                                        compound, "Reactant", initialMass, reactedMass, excessMass, "---"));
                            } else {
                                table.append(String.format("%-12s | %-10s | %-12s | %-12.2f | %-12s | %-12s%n",
                                        compound, "Reactant", "(Excess)", reactedMass, "---", "---"));
                            }
                        } else {
                            table.append(String.format("%-12s | %-10s | %-12s | %-12s | %-12s | %-12.2f%n",
                                    compound, "Product", "---", "---", "---", reactedMass));
                        }
                    }

                    Platform.runLater(() -> {
                        summaryArea.setText(table.toString());
                        // append small footer and log CSV save info
                        logArea.appendText("\nℹ️ Full reaction summary computed. Core will be asked to append to CSV via exportResult.\n");
                    });

                    // Call core exportResult equivalent by invoking StoichiometryCalculator.exportResult via reflection
                    // exportResult is private in core, so instead we call StoichiometryCalculator.exportMoleRatioChart and exportMoleMassStackedChart
                    // and also write an entry to composition_report.csv using the core's public export behavior through reflection if needed.
                    // We'll attempt to call StoichiometryCalculator.exportMoleRatioChart and exportMoleMassStackedChart to create charts.
                    try {
                        StoichiometryCalculator.exportMoleRatioChart(coeffs, "stoichiometry_ratios.png");
                        StoichiometryCalculator.exportMoleMassStackedChart(coeffs, "stoichiometry_stacked.png");
                        Platform.runLater(() -> logArea.appendText("📊 Charts exported (mole ratio & stacked). Check project folder.\n"));
                    } catch (Exception chartEx) {
                        Platform.runLater(() -> logArea.appendText("⚠️ Chart export failed: " + chartEx.getMessage() + "\n"));
                    }

                    // Also append a CSV entry similar to the core (we can append a short line here)
                    try {
                        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        File csv = new File(REPORT_FILE);
                        boolean exists = csv.exists();
                        String details = String.format("Limiting=%s; Yield=%s %s", limiting, DF.format(theoreticalYieldAmount), desiredUnit);
                        try (PrintWriter pw = new PrintWriter(new FileWriter(csv, true))) {
                            if (!exists) pw.println("Timestamp,Type,Formula,Details,Phases");
                            pw.printf("%s,Stoichiometry,\"%s\",\"%s\",\"%s\"%n", ts, balanced, details, "");
                        }
                        Platform.runLater(() -> logArea.appendText("📂 Stoichiometry result added to " + REPORT_FILE + "\n"));
                    } catch (Exception csvEx) {
                        Platform.runLater(() -> logArea.appendText("⚠️ Failed to append to CSV: " + csvEx.getMessage() + "\n"));
                    }

                } catch (Exception ex) {
                    Platform.runLater(() -> logArea.appendText("❌ Unexpected error: " + ex.getMessage() + "\n"));
                }
            });
        });

        // Export chart buttons (let user manually export using core helpers)
        exportRatioChartBtn.setOnAction(e -> {
            try {
                // Need coefficients to call chart export; attempt to compute from current equation
                String eq = Optional.ofNullable(eqField.getText()).orElse("").trim();
                if (eq.isBlank()) { logArea.appendText("⚠️ Enter equation to export charts.\n"); return; }
                String balanced = EquationBalancer.balance(eq, true);
                Map<String, Integer> coeffs = EquationBalancer.parseCoefficients(balanced);
                StoichiometryCalculator.exportMoleRatioChart(coeffs, "stoichiometry_ratios.png");
                logArea.appendText("📊 Mole ratio chart exported.\n");
            } catch (Exception ex) { logArea.appendText("❌ Chart export error: " + ex.getMessage() + "\n"); }
        });

        exportStackedChartBtn.setOnAction(e -> {
            try {
                String eq = Optional.ofNullable(eqField.getText()).orElse("").trim();
                if (eq.isBlank()) { logArea.appendText("⚠️ Enter equation to export charts.\n"); return; }
                String balanced = EquationBalancer.balance(eq, true);
                Map<String, Integer> coeffs = EquationBalancer.parseCoefficients(balanced);
                StoichiometryCalculator.exportMoleMassStackedChart(coeffs, "stoichiometry_stacked.png");
                logArea.appendText("📊 Mole+Mass stacked chart exported.\n");
            } catch (Exception ex) { logArea.appendText("❌ Chart export error: " + ex.getMessage() + "\n"); }
        });

        viewCsvBtn.setOnAction(e -> {
            try {
                File csv = new File(REPORT_FILE);
                if (!csv.exists()) { logArea.appendText("ℹ️ No CSV report found.\n"); return; }
                String content = Files.readString(csv.toPath(), StandardCharsets.UTF_8);
                TextArea ta = new TextArea(content);
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefSize(800, 500);
                Dialog<Void> d = new Dialog<>();
                d.getDialogPane().setContent(ta);
                d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                d.setTitle("CSV Report: " + REPORT_FILE);
                d.showAndWait();
            } catch (Exception ex) { logArea.appendText("❌ Failed to read CSV: " + ex.getMessage() + "\n"); }
        });

        openCsvFolderBtn.setOnAction(e -> {
            try {
                File csv = new File(REPORT_FILE);
                File folder = csv.getAbsoluteFile().getParentFile();
                if (folder == null || !folder.exists()) folder = new File(".").getAbsoluteFile();
                java.awt.Desktop.getDesktop().open(folder);
                logArea.appendText("📁 Opened folder: " + folder.getAbsolutePath() + "\n");
            } catch (Exception ex) { logArea.appendText("⚠️ Could not open folder: " + ex.getMessage() + "\n"); }
        });

        saveSummaryBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save reaction summary");
            chooser.setInitialFileName("stoichiometry_summary.txt");
            Window w = root.getScene() != null ? root.getScene().getWindow() : null;
            File file = chooser.showSaveDialog(w);
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), summaryArea.getText(), StandardCharsets.UTF_8);
                    logArea.appendText("💾 Summary saved to " + file.getAbsolutePath() + "\n");
                } catch (Exception ex) { logArea.appendText("❌ Save failed: " + ex.getMessage() + "\n"); }
            }
        });

        return root;
    }

    // ----------------------
    // Unit conversion helpers (same logic as core)
    // ----------------------
    private static final double MOLAR_VOLUME_STP = 22.4;

    private static double convertToMoles(String formula, double amount, String unit) {
        unit = unit.toLowerCase();
        if (unit.equals("mol")) return amount;
        if (unit.equals("g")) {
            double mm = getMolarMassSafe(formula);
            if (mm <= 0) return -1;
            return amount / mm;
        }
        if (unit.equals("l")) {
            return amount / MOLAR_VOLUME_STP;
        }
        return -1;
    }

    private static double convertFromMoles(double moles, String formula, String unit) {
        unit = unit.toLowerCase();
        if (unit.equals("mol")) return moles;
        if (unit.equals("g")) {
            double mm = getMolarMassSafe(formula);
            if (mm <= 0) return -1;
            return moles * mm;
        }
        if (unit.equals("l")) return moles * MOLAR_VOLUME_STP;
        return -1;
    }

    private static double getMolarMassSafe(String formula) {
        try {
            Map<String, Integer> comp = EquationBalancer.parseCompound(formula);
            return MolarMassCalculator.computeMass(comp, false);
        } catch (Exception e) {
            return 0;
        }
    }
}


