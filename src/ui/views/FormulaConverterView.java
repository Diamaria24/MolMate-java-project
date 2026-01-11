package ui.views;

import core.FormulaConverter;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.Optional;

public class FormulaConverterView {

    public static Parent getView() {
        VBox layout = new VBox(20);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #F5F5DC;");

        Label title = new Label("Formula & Name Converter");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #8A2BE2;");

        TextField inputField = new TextField();
        inputField.setPromptText("Enter a chemical name (e.g., baking soda) or formula (e.g., H2O)");
        Button convertButton = new Button("Convert");
        convertButton.setStyle("-fx-background-color: #98FB98; -fx-font-size: 14px; -fx-font-weight: bold;");
        HBox inputBox = new HBox(10, inputField, convertButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPromptText("Results will be displayed here...");
        resultArea.setFont(Font.font("Monospaced", 14));
        resultArea.setPrefHeight(300);

        Label infoLabel = new Label();
        infoLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #006400;");
        infoLabel.setWrapText(true);

        convertButton.setOnAction(e -> {
            String input = inputField.getText();
            performConversion(input, resultArea, infoLabel);
        });

        layout.getChildren().addAll(title, inputBox, infoLabel, resultArea);
        return layout;
    }

    private static void performConversion(String input, TextArea resultArea, Label infoLabel) {
        FormulaConverter converter = FormulaConverter.getInstance();
        FormulaConverter.ConvertResult result = converter.convert(input);

        infoLabel.setText("");
        resultArea.setText("");

        if (result.isSuccess()) {
            if (result.extraInfo() != null) {
                infoLabel.setText("ℹ️ " + result.extraInfo());
            }
            resultArea.setText(result.resultText());
        } else {
            if (result.suggestion() != null) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Suggestion");
                alert.setHeaderText(result.resultText() + "\nDid you mean: " + result.suggestion() + "?");

                Optional<ButtonType> option = alert.showAndWait();
                if (option.isPresent() && option.get() == ButtonType.OK) {
                    performConversion(result.suggestion(), resultArea, infoLabel);
                } else {
                    resultArea.setText("Search for '" + input + "' not found.");
                }
            } else {
                resultArea.setText(result.resultText());
            }
        }
    }
}
