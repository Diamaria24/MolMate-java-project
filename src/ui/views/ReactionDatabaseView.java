package ui.views;

import core.Reaction;
import core.ReactionDatabase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

public class ReactionDatabaseView {

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Reaction Database");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        VBox center = new VBox(12);
        center.setPadding(new Insets(10));

        // --- Search row ---
        HBox searchRow = new HBox(8);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        TextField searchField = new TextField();
        searchField.setPrefWidth(300);
        searchField.setPromptText("Search by compound or name");
        Button searchByCompoundBtn = new Button("Search Compound");
        Button searchByNameBtn = new Button("Search Name");
        Button randomBtn = new Button("Random Reaction");
        searchRow.getChildren().addAll(searchField, searchByCompoundBtn, searchByNameBtn, randomBtn);

        // --- Results table ---
        TableView<Reaction> table = new TableView<>();
        ObservableList<Reaction> data = FXCollections.observableArrayList();
        table.setItems(data);

        TableColumn<Reaction, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(200);

        TableColumn<Reaction, String> eqCol = new TableColumn<>("Equation");
        eqCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().equation()));
        eqCol.setPrefWidth(300);

        TableColumn<Reaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().type()));
        typeCol.setPrefWidth(150);

        table.getColumns().addAll(nameCol, eqCol, typeCol);

        // --- Details area ---
        TextArea detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefHeight(160);

        VBox detailsBox = new VBox(new Label("Reaction Details:"), detailsArea);
        detailsBox.setSpacing(5);

        center.getChildren().addAll(searchRow, table, detailsBox);
        root.setCenter(center);

        // --- Button actions ---
        searchByCompoundBtn.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) return;
            List<Reaction> results = ReactionDatabase.findReactionsByCompound(query);
            data.setAll(results);
            if (results.isEmpty()) detailsArea.setText("No reactions found for compound: " + query);
        });

        searchByNameBtn.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) return;
            List<Reaction> results = ReactionDatabase.findReactionsByName(query);
            data.setAll(results);
            if (results.isEmpty()) detailsArea.setText("No reactions found with name containing: " + query);
        });

        randomBtn.setOnAction(e -> {
            Reaction r = ReactionDatabase.getRandomReaction();
            data.setAll(r);
            detailsArea.setText(formatReactionDetails(r));
        });

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                detailsArea.setText(formatReactionDetails(newSel));
            }
        });

        return root;
    }

    private static String formatReactionDetails(Reaction r) {
        return "Name: " + r.name() + "\n"
                + "Equation: " + r.equation() + "\n"
                + "Type: " + r.type() + "\n"
                + "Description: " + r.description();
    }
}
