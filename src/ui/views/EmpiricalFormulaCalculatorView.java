package ui.views;

import core.EmpiricalFormulaCalculator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.HashMap;
import java.util.Map;

public class EmpiricalFormulaCalculatorView {

    public static Parent getView() {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #F5F5DC;");

        Label title = new Label("Empirical Formula Calculator");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #8A2BE2;");

        // Data Management
        Map<String, Double> compositionMap = new HashMap<>();
        ObservableList<String> compositionList = FXCollections.observableArrayList();

        // UI Components for Input
        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(10);
        inputGrid.setVgap(10);

        TextField elementInput = new TextField();
        elementInput.setPromptText("Element (e.g., C)");
        TextField percentInput = new TextField();
        percentInput.setPromptText("Mass or % (e.g., 40.0)");
        Button addButton = new Button("Add Element");

        inputGrid.add(new Label("Element:"), 0, 0);
        inputGrid.add(elementInput, 1, 0);
        inputGrid.add(new Label("Mass / Percent:"), 2, 0);
        inputGrid.add(percentInput, 3, 0);
        inputGrid.add(addButton, 4, 0);

        ListView<String> compositionListView = new ListView<>(compositionList);
        compositionListView.setPrefHeight(150);

        // UI Components for Actions and Results
        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPromptText("The step-by-step breakdown will appear here...");
        resultArea.setFont(Font.font("Monospaced", 14));
        resultArea.setPrefHeight(250);

        Label csvConfirmationLabel = new Label();
        csvConfirmationLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

        Button calculateButton = new Button("Calculate Formula");
        calculateButton.setStyle("-fx-background-color: #98FB98; -fx-font-size: 14px; -fx-font-weight: bold;");
        Button resetButton = new Button("Reset");
        HBox actionButtons = new HBox(10, calculateButton, resetButton);

        // Event Handling
        addButton.setOnAction(e -> {
            try {
                String element = elementInput.getText().trim();
                double percent = Double.parseDouble(percentInput.getText().trim());
                if (!element.isEmpty() && percent > 0) {
                    compositionMap.put(element, percent);
                    compositionList.setAll(compositionMap.entrySet().stream()
                            .map(entry -> String.format("%s: %.2f %%", entry.getKey(), entry.getValue()))
                            .toList());
                    elementInput.clear();
                    percentInput.clear();
                    elementInput.requestFocus();
                }
            } catch (NumberFormatException ex) {
                resultArea.setText("Error: Invalid percentage value.");
            }
        });

        calculateButton.setOnAction(e -> {
            if (compositionMap.isEmpty()) {
                resultArea.setText("Error: Please add at least one element.");
                return;
            }
            try {
                // Call the backend method that returns the full breakdown
                EmpiricalFormulaCalculator.CalculationResult result = EmpiricalFormulaCalculator.calculateWithBreakdown(compositionMap);

                // Display the breakdown in the text area
                resultArea.setText(result.breakdownText());

                // Automatically save the result to the main CSV report and show confirmation
                EmpiricalFormulaCalculator.exportResultToCSV(result.finalFormula(), compositionMap);
                csvConfirmationLabel.setText("✅ Result also saved to composition_report.csv");
            } catch (Exception ex) {
                resultArea.setText("❌ Error: " + ex.getMessage());
            }
        });

        resetButton.setOnAction(e -> {
            compositionMap.clear();
            compositionList.clear();
            resultArea.clear();
            resultArea.setPromptText("Results will be displayed here...");
            csvConfirmationLabel.setText("");
            elementInput.clear();
            percentInput.clear();
        });

        layout.getChildren().addAll(title, inputGrid, new Label("Current Composition:"), compositionListView, actionButtons, resultArea, csvConfirmationLabel);
        return layout;
    }
}
