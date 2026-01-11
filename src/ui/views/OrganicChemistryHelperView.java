package ui.views;

import core.EquationBalancer;
import core.MolarMassCalculator;
import core.OrganicChemistryHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class OrganicChemistryHelperView {

    private static final DecimalFormat DF = new DecimalFormat("#.###");
    private static final String REPORT_FILE = "composition_result.csv";

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Organic Chemistry Helper");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        VBox center = new VBox(10);
        center.setPadding(new Insets(12));

        // Formula input row
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        TextField formulaField = new TextField();
        formulaField.setPromptText("Enter a semi-structural formula (e.g., CH3CH(CH3)CH3)");
        formulaField.setPrefWidth(600);
        Button analyzeBtn = new Button("Analyze");
        Button clearBtn = new Button("Clear");
        Button viewCsvBtn = new Button("View CSV Log");
        inputRow.getChildren().addAll(formulaField, analyzeBtn, clearBtn, viewCsvBtn);

        // Results area
        Label resultHeader = new Label("Results");
        resultHeader.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(15);

        center.getChildren().addAll(inputRow, resultHeader, logArea);
        root.setCenter(center);

        // --- Actions ---
        analyzeBtn.setOnAction(e -> {
            String formula = formulaField.getText().trim();
            if (formula.isEmpty()) {
                logArea.appendText("⚠️ Please enter a formula.\n");
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    Platform.runLater(() -> {
                        logArea.clear();
                        logArea.appendText("🔎 Analyzing formula: " + formula + "\n");
                        logArea.appendText("---------------------------------------------\n");
                    });

                    // Compute molecular formula
                    Map<String, Integer> elementCounts = EquationBalancer.parseCompound(formula);
                    if (elementCounts.isEmpty()) {
                        Platform.runLater(() -> logArea.appendText("❌ Could not parse the formula.\n"));
                        return;
                    }

                    String molecularFormula = formatMapToFormula(elementCounts);

                    // Compute molar mass
                    double molarMass = MolarMassCalculator.computeMass(elementCounts, false);

                    // Functional group
                    String fg = OrganicChemistryHelper.identifyFunctionalGroup(formula);

                    // IUPAC name
                    String iupac = OrganicChemistryHelper.getIupacName(formula);

                    // Build output
                    StringBuilder sb = new StringBuilder();
                    sb.append("✅ Molecular Formula: ").append(molecularFormula).append("\n");
                    sb.append("✅ Molar Mass: ").append(DF.format(molarMass)).append(" g/mol\n");
                    sb.append("✅ Percentage Composition:\n");

                    for (Map.Entry<String, Integer> eCount : elementCounts.entrySet()) {
                        String eName = eCount.getKey();
                        // Option 1: If PeriodicTable provides atomic masses
                        // Option 2: Use MolarMassCalculator for a single element
                        double massEl = MolarMassCalculator.computeMass(Map.of(eName, 1), false) * eCount.getValue();


                        double perc = (massEl / molarMass) * 100.0;
                        sb.append("   - ").append(eName).append(": ").append(DF.format(perc)).append("%\n");
                    }

                    sb.append("✅ Functional Group: ").append(fg).append("\n");
                    sb.append("✅ IUPAC Name: ").append(iupac).append("\n");
                    sb.append("---------------------------------------------\n");

                    Platform.runLater(() -> logArea.appendText(sb.toString()));

                    // --- Save results to CSV ---
                    try {
                        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        File csv = new File(REPORT_FILE);
                        boolean exists = csv.exists();

                        try (PrintWriter pw = new PrintWriter(new FileWriter(csv, true))) {
                            if (!exists) {
                                pw.println("Timestamp,Formula,MolecularFormula,MolarMass,FunctionalGroup,IUPAC");
                            }
                            pw.printf("%s,%s,%s,%.3f,%s,%s%n",
                                    ts, formula, molecularFormula, molarMass, fg, iupac);
                        }

                        Platform.runLater(() ->
                                logArea.appendText("📂 Results stored in CSV file: " + REPORT_FILE + "\n")
                        );

                    } catch (Exception csvEx) {
                        Platform.runLater(() ->
                                logArea.appendText("❌ Failed to save CSV: " + csvEx.getMessage() + "\n")
                        );
                    }

                } catch (Exception ex2) {
                    Platform.runLater(() -> logArea.appendText("❌ Error: " + ex2.getMessage() + "\n"));
                }
            });
        });

        clearBtn.setOnAction(e -> {
            formulaField.clear();
            logArea.clear();
        });

        viewCsvBtn.setOnAction(e -> {
            try {
                File csv = new File(REPORT_FILE);
                if (!csv.exists()) {
                    logArea.appendText("ℹ️ No CSV log found yet.\n");
                    return;
                }
                String content = Files.readString(csv.toPath(), StandardCharsets.UTF_8);
                TextArea ta = new TextArea(content);
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefSize(800, 500);
                Dialog<Void> d = new Dialog<>();
                d.getDialogPane().setContent(ta);
                d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                d.setTitle("CSV Log: " + REPORT_FILE);
                d.showAndWait();
            } catch (Exception ex) {
                logArea.appendText("❌ Failed to read CSV: " + ex.getMessage() + "\n");
            }
        });

        return root;
    }

    // --- Helper to format molecular formula ---
    private static String formatMapToFormula(Map<String, Integer> elementCounts) {
        StringBuilder sb = new StringBuilder();
        elementCounts.keySet().stream()
                .sorted((a, b) -> {
                    if (a.equals("C")) return -1;
                    if (b.equals("C")) return 1;
                    if (a.equals("H") && !a.equals(b)) return -1;
                    if (b.equals("H") && !a.equals(b)) return 1;
                    return a.compareTo(b);
                })
                .forEach(el -> {
                    sb.append(el);
                    int count = elementCounts.get(el);
                    if (count > 1) sb.append(count);
                });
        return sb.toString();
    }
}
