package ui.views;

import core.TitrationSimulator;
import core.TitrationSimulator.Solution;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TitrationSimulatorView {

    private static final DecimalFormat DF = new DecimalFormat("#.###");

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Titration Simulator");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        VBox center = new VBox(10);
        center.setPadding(new Insets(12));

        // --- Analyte Input ---
        HBox analyteRow = new HBox(8);
        analyteRow.setAlignment(Pos.CENTER_LEFT);
        TextField analyteFormula = new TextField();
        analyteFormula.setPromptText("Analyte formula (e.g., HCl)");
        TextField analyteVolume = new TextField();
        analyteVolume.setPromptText("Volume (mL)");
        TextField analyteMolarity = new TextField();
        analyteMolarity.setPromptText("Molarity (M)");
        analyteRow.getChildren().addAll(new Label("Analyte:"), analyteFormula, analyteVolume, analyteMolarity);

        // --- Titrant Input ---
        HBox titrantRow = new HBox(8);
        titrantRow.setAlignment(Pos.CENTER_LEFT);
        TextField titrantFormula = new TextField();
        titrantFormula.setPromptText("Titrant formula (e.g., NaOH)");
        TextField titrantMolarity = new TextField();
        titrantMolarity.setPromptText("Molarity (M)");
        titrantRow.getChildren().addAll(new Label("Titrant:"), titrantFormula, titrantMolarity);

        // --- Run + Clear buttons ---
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        Button runBtn = new Button("Run Simulation");
        Button clearBtn = new Button("Clear");
        btnRow.getChildren().addAll(runBtn, clearBtn);

        // --- Log Output ---
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(8);

        // --- Chart for pH curve ---
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis(0, 14, 1);
        xAxis.setLabel("Volume of Titrant Added (mL)");
        yAxis.setLabel("pH");
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Titration Curve");
        chart.setCreateSymbols(false);
        chart.setPrefHeight(400);

        // --- Save Chart Button ---
        Button saveChartBtn = new Button("Save Chart as PNG");

        // Layout
        center.getChildren().addAll(analyteRow, titrantRow, btnRow, logArea, chart, saveChartBtn);
        root.setCenter(center);

        // --- Actions ---
        runBtn.setOnAction(e -> {
            logArea.clear();
            chart.getData().clear();

            String aFormula = analyteFormula.getText().trim();
            String tFormula = titrantFormula.getText().trim();
            if (aFormula.isEmpty() || tFormula.isEmpty()) {
                logArea.appendText("⚠ Please enter both analyte and titrant formulas.\n");
                return;
            }

            try {
                double aVol = Double.parseDouble(analyteVolume.getText().trim());
                double aM = Double.parseDouble(analyteMolarity.getText().trim());
                double tM = Double.parseDouble(titrantMolarity.getText().trim());

                Solution analyte = new Solution(aFormula, aM, aVol);
                Solution titrant = new Solution(tFormula, tM, 0);

                // Run simulation in background
                CompletableFuture.runAsync(() -> {
                    logArea.appendText("🔄 Running titration simulation...\n");

                    // Run CLI simulation (saves CSV + PNG log as before)
                    TitrationSimulator.runSimulation(analyte, titrant);

                    // Build curve for GUI chart
                    Map<Double, Double> curveData = new LinkedHashMap<>();
                    double vAnalyteL = analyte.getVolume() / 1000.0;
                    double initialMolesIonsAnalyte = analyte.getMolarity() * vAnalyteL * analyte.getEquivalents();
                    double vEquivL = initialMolesIonsAnalyte / (titrant.getMolarity() * titrant.getEquivalents());
                    double vEquivML = vEquivL * 1000.0;
                    double maxVolume = vEquivML * 2.0;
                    double increment = Math.max(0.1, vEquivML / 50.0);

                    try {
                        var method = TitrationSimulator.class.getDeclaredMethod(
                                "calculateStrongAcidStrongBasePh",
                                Solution.class, Solution.class, double.class
                        );
                        method.setAccessible(true);

                        for (double vAdded = 0; vAdded <= maxVolume; vAdded += increment) {
                            double pH = (double) method.invoke(null, analyte, titrant, vAdded);
                            curveData.put(vAdded, pH);
                        }

                        double epPh = (double) method.invoke(null, analyte, titrant, vEquivML);
                        curveData.put(vEquivML, epPh);

                        Platform.runLater(() -> {
                            logArea.appendText("✅ Equivalence Point ≈ " + DF.format(vEquivML) + " mL of " + titrant.getFormula() + "\n");
                            logArea.appendText("📂 Results stored in CSV file: composition_report.csv\n");

                            XYChart.Series<Number, Number> series = new XYChart.Series<>();
                            series.setName(analyte.getFormula() + " vs " + titrant.getFormula());
                            for (var entry : curveData.entrySet()) {
                                series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
                            }
                            chart.getData().add(series);
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> logArea.appendText("❌ Error computing curve: " + ex.getMessage() + "\n"));
                    }
                });

            } catch (NumberFormatException ex) {
                logArea.appendText("❌ Invalid number format for volume or molarity.\n");
            }
        });

        clearBtn.setOnAction(e -> {
            analyteFormula.clear();
            analyteVolume.clear();
            analyteMolarity.clear();
            titrantFormula.clear();
            titrantMolarity.clear();
            logArea.clear();
            chart.getData().clear();
        });

        saveChartBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Titration Curve");
            chooser.setInitialFileName("titration_curve.png");
            File file = chooser.showSaveDialog(root.getScene().getWindow());
            if (file != null) {
                try {
                    javafx.embed.swing.SwingFXUtils
                            .fromFXImage(chart.snapshot(null, null), null);
                    javax.imageio.ImageIO.write(
                            javafx.embed.swing.SwingFXUtils.fromFXImage(chart.snapshot(null, null), null),
                            "png", file);
                    logArea.appendText("💾 Chart saved to " + file.getAbsolutePath() + "\n");
                } catch (Exception ex) {
                    logArea.appendText("❌ Failed to save chart: " + ex.getMessage() + "\n");
                }
            }
        });

        return root;
    }
}
