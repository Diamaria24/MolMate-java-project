package ui.views;

import core.MolarMassCalculator;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.jfree.chart.JFreeChart;
import java.io.File;

public class MolarMassCalculatorView {

    public static Parent getView() {
        VBox layout = new VBox(20);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #F5F5DC;"); // Beige

        Label title = new Label("Molar Mass Calculator");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #8A2BE2;"); // Lavender

        TextField formulaInput = new TextField();
        formulaInput.setPromptText("Enter chemical formula (e.g., C6H12O6)");
        Button calculateButton = new Button("Calculate");
        calculateButton.setStyle("-fx-background-color: #98FB98; -fx-font-size: 14px; -fx-font-weight: bold;");
        HBox inputBox = new HBox(10, formulaInput, calculateButton);
        HBox.setHgrow(formulaInput, Priority.ALWAYS);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPromptText("Results will be displayed here...");
        resultArea.setFont(Font.font("Monospaced", 14));
        resultArea.setPrefHeight(200);

        Label csvConfirmationLabel = new Label(); // For the confirmation message
        csvConfirmationLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

        // --- NEW: UI elements for displaying charts ---
        ImageView barChartImage = new ImageView();
        ImageView pieChartImage = new ImageView();
        barChartImage.setFitHeight(300);
        barChartImage.setPreserveRatio(true);
        pieChartImage.setFitHeight(300);
        pieChartImage.setPreserveRatio(true);

        Button saveBarChartButton = new Button("Save Bar Chart");
        Button savePieChartButton = new Button("Save Pie Chart");

        VBox barChartBox = new VBox(5, barChartImage, saveBarChartButton);
        barChartBox.setAlignment(Pos.CENTER);
        VBox pieChartBox = new VBox(5, pieChartImage, savePieChartButton);
        pieChartBox.setAlignment(Pos.CENTER);

        HBox chartDisplayArea = new HBox(20, barChartBox, pieChartBox);
        chartDisplayArea.setVisible(false); // Hidden until a calculation is done

        // --- Event Handling ---
        calculateButton.setOnAction(e -> {
            String formula = formulaInput.getText();
            if (formula.isBlank()) {
                resultArea.setText("Please enter a formula.");
                chartDisplayArea.setVisible(false);
                csvConfirmationLabel.setText("");
                return;
            }
            try {
                // 1. Calculate results
                MolarMassCalculator.MassCalculationResult result = MolarMassCalculator.calculateWithBreakdown(formula);
                resultArea.setText(result.breakdownText());

                // 2. Export to CSV and show confirmation
                MolarMassCalculator.exportResult(formula, result.totalMass());
                csvConfirmationLabel.setText("✅ Result saved to composition_report.csv");

                // 3. Create chart objects
                JFreeChart barChart = MolarMassCalculator.createMassBarChart(formula, result.composition());
                JFreeChart pieChart = MolarMassCalculator.createMassPieChart(formula, result.composition());

                // 4. Convert charts to images and display them
                barChartImage.setImage(SwingFXUtils.toFXImage(barChart.createBufferedImage(400, 300), null));
                pieChartImage.setImage(SwingFXUtils.toFXImage(pieChart.createBufferedImage(400, 300), null));
                chartDisplayArea.setVisible(true); // Show the chart area

                // 5. Wire up the save buttons
                saveBarChartButton.setOnAction(ev -> MolarMassCalculator.saveChartAsPNG(barChart, new File(formula + "_bar_chart.png")));
                savePieChartButton.setOnAction(ev -> MolarMassCalculator.saveChartAsPNG(pieChart, new File(formula + "_pie_chart.png")));

            } catch (Exception ex) {
                resultArea.setText("❌ Error: " + ex.getMessage());
                chartDisplayArea.setVisible(false);
                csvConfirmationLabel.setText("");
            }
        });

        layout.getChildren().addAll(title, inputBox, resultArea, csvConfirmationLabel, chartDisplayArea);
        return layout;
    }
}