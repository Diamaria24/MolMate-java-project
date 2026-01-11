package ui.views;

import core.CompoundManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OnlineSearchView {

    public static Parent getView() {
        VBox layout = new VBox(20);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #F5F5DC;"); // Beige

        Label title = new Label("Online Compound Search (PubChem)");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #8A2BE2;"); // Lavender

        TextField searchInput = new TextField();
        searchInput.setPromptText("Enter compound name (e.g., Caffeine)");
        Button searchButton = new Button("Search");
        searchButton.setStyle("-fx-background-color: #98FB98; -fx-font-size: 14px; -fx-font-weight: bold;");
        HBox searchBox = new HBox(10, searchInput, searchButton);
        HBox.setHgrow(searchInput, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox resultsDisplay = new VBox(15);
        resultsDisplay.setPadding(new Insets(10));
        scrollPane.setContent(resultsDisplay);

        searchButton.setOnAction(e -> {
            String query = searchInput.getText();
            if (query == null || query.isBlank()) {
                resultsDisplay.getChildren().setAll(new Label("Please enter a compound to search."));
                return;
            }
            resultsDisplay.getChildren().setAll(new ProgressIndicator()); // Show loading spinner

            new Thread(() -> {
                Map<String, Object> data = CompoundManager.getCompoundData(query);
                Platform.runLater(() -> {
                    if (data != null) {
                        displayResults(resultsDisplay, data, query);
                    } else {
                        resultsDisplay.getChildren().setAll(new Label("Compound not found."));
                    }
                });
            }).start();
        });

        layout.getChildren().addAll(title, searchBox, scrollPane);
        return layout;
    }

    private static void displayResults(VBox resultsDisplay, Map<String, Object> data, String query) {
        resultsDisplay.getChildren().clear();

        ImageView structureImageView = new ImageView();
        structureImageView.setPreserveRatio(true);
        structureImageView.setFitWidth(400);
        try {
            String encodedName = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String imageUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + encodedName + "/PNG";
            structureImageView.setImage(new Image(imageUrl, true));
        } catch (Exception ex) { /* ignore */ }

        GridPane propertiesGrid = new GridPane();
        propertiesGrid.setHgap(10);
        propertiesGrid.setVgap(8);
        int row = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Label keyLabel = new Label(entry.getKey() + ":");
            keyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            Label valueLabel = new Label(entry.getValue().toString());
            valueLabel.setWrapText(true);
            propertiesGrid.add(keyLabel, 0, row);
            propertiesGrid.add(valueLabel, 1, row);
            row++;
        }

        resultsDisplay.getChildren().addAll(structureImageView, propertiesGrid);
    }
}