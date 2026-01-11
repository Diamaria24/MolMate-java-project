package ui.views;

import core.MolarMassCalculator;
import core.PercentageComposition;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.jfree.chart.JFreeChart;
import java.io.File;

public class PercentageCompositionView {

    public static Parent getView() {
        VBox layout = new VBox(20);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #F5F5DC;");

        Label title = new Label("Percentage Composition Calculator");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #8A2BE2;");

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
        resultArea.setPrefHeight(150);

        Label csvConfirmationLabel = new Label(); // For the confirmation message
        csvConfirmationLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

        ImageView pieChartImage = new ImageView();
        ImageView barChartImage = new ImageView();
        ImageView stackedChartImage = new ImageView();

        VBox pieBox = createChartBox(pieChartImage, "Pie Chart");
        VBox barBox = createChartBox(barChartImage, "Bar Chart");
        VBox stackedBox = createChartBox(stackedChartImage, "Stacked Bar Chart");

        HBox chartDisplayArea = new HBox(20, pieBox, barBox, stackedBox);
        chartDisplayArea.setVisible(false);

        calculateButton.setOnAction(e -> {
            String formula = formulaInput.getText();
            if (formula.isBlank()) { /* ... (error handling) ... */ return; }
            try {
                PercentageComposition.CompositionResult result = PercentageComposition.analyzeComposition(formula);
                resultArea.setText(result.breakdownText());

                // --- THE FIX: Automatically save to the main report file ---
                PercentageComposition.appendToMainReport(formula, result);
                csvConfirmationLabel.setText("✅ Result added to composition_report.csv");

                // Create and display charts
                String safeName = PercentageComposition.safeFileName(formula);
                JFreeChart pieChart = PercentageComposition.createPieChart(formula, result.totalMass(), result.elementMasses());
                JFreeChart barChart = PercentageComposition.createBarChart(formula, result.totalMass(), result.elementMasses());
                JFreeChart stackedChart = PercentageComposition.createStackedBarChart(formula, result.totalMass(), result.elementMasses());

                pieChartImage.setImage(SwingFXUtils.toFXImage(pieChart.createBufferedImage(350, 280), null));
                barChartImage.setImage(SwingFXUtils.toFXImage(barChart.createBufferedImage(350, 280), null));
                stackedChartImage.setImage(SwingFXUtils.toFXImage(stackedChart.createBufferedImage(350, 280), null));

                ((Button)pieBox.getChildren().get(1)).setOnAction(ev -> MolarMassCalculator.saveChartAsPNG(pieChart, new File(safeName + "_pie.png")));
                ((Button)barBox.getChildren().get(1)).setOnAction(ev -> MolarMassCalculator.saveChartAsPNG(barChart, new File(safeName + "_bar.png")));
                ((Button)stackedBox.getChildren().get(1)).setOnAction(ev -> MolarMassCalculator.saveChartAsPNG(stackedChart, new File(safeName + "_stacked.png")));

                chartDisplayArea.setVisible(true);
            } catch (Exception ex) {
                resultArea.setText("❌ Error: " + ex.getMessage());
                chartDisplayArea.setVisible(false);
                csvConfirmationLabel.setText("");
            }
        });

        // The export CSV button has been removed from the layout
        layout.getChildren().addAll(title, inputBox, resultArea, csvConfirmationLabel, chartDisplayArea);
        return layout;
    }

    private static VBox createChartBox(ImageView imageView, String title) {
        imageView.setFitHeight(280);
        imageView.setPreserveRatio(true);
        Button saveButton = new Button("Save " + title);
        VBox box = new VBox(5, imageView, saveButton);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
