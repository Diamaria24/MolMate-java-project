package ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import learning.Handbook;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HandbookView {

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #FAF9F6;");

        Label title = new Label("Reference Handbook");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#2F4F4F"));
        BorderPane.setAlignment(title, Pos.CENTER);
        root.setTop(title);

        // Left: Categories list
        ListView<Handbook.HandbookCategory> categoryList = new ListView<>();
        categoryList.setPrefWidth(220);
        categoryList.getItems().addAll(Handbook.getCategories());
        categoryList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Handbook.HandbookCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.name());
            }
        });

        // Middle: search + entries list
        VBox middle = new VBox(8);
        middle.setPrefWidth(320);

        HBox searchRow = new HBox(8);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("Search entries by title, summary, or body...");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button clearBtn = new Button("Clear");
        clearBtn.setFocusTraversable(false);

        CheckBox allCategoriesCheck = new CheckBox("Search all categories");
        allCategoriesCheck.setSelected(false);

        searchRow.getChildren().addAll(searchField, clearBtn);

        ListView<Handbook.HandbookEntry> entryList = new ListView<>();
        entryList.setPrefWidth(320);
        entryList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Handbook.HandbookEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.title());
            }
        });

        // Right: Entry details
        VBox entryDetails = new VBox(10);
        entryDetails.setPadding(new Insets(10));
        entryDetails.setStyle("-fx-background-color: white; -fx-border-color: #D3D3D3; -fx-background-radius: 6; -fx-border-radius: 6;");
        entryDetails.setPrefWidth(480);

        Label entryTitle = new Label("Select an entry");
        entryTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        Label entrySummary = new Label();
        entrySummary.setWrapText(true);
        entrySummary.setStyle("-fx-font-style: italic; -fx-text-fill: #555;");
        ScrollPane bodyScroll = new ScrollPane();
        bodyScroll.setFitToWidth(true);
        bodyScroll.setStyle("-fx-background-color: transparent;");
        Label entryBody = new Label();
        entryBody.setWrapText(true);
        entryBody.setMaxWidth(440);
        bodyScroll.setContent(entryBody);
        bodyScroll.setPrefHeight(420);

        entryDetails.getChildren().addAll(entryTitle, entrySummary, new Separator(), bodyScroll);

        // Populate entries for the initially selected category (if any)
        if (!categoryList.getItems().isEmpty()) {
            categoryList.getSelectionModel().selectFirst();
            Handbook.HandbookCategory initialCat = categoryList.getSelectionModel().getSelectedItem();
            if (initialCat != null) entryList.getItems().addAll(initialCat.entries());
        }

        // Search helper: filter entries by text and check allCategories flag
        final java.util.function.BiFunction<String, Boolean, List<Handbook.HandbookEntry>> searchFn = (query, searchAll) -> {
            String q = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);
            List<Handbook.HandbookEntry> results = new ArrayList<>();
            if (q.isEmpty()) {
                // If empty, return entries of selected category (handled by caller)
                return results;
            }

            if (searchAll) {
                // search all categories
                for (Handbook.HandbookCategory cat : Handbook.getCategories()) {
                    for (Handbook.HandbookEntry e : cat.entries()) {
                        if (matchesQuery(e, q)) results.add(e);
                    }
                }
            } else {
                // search only within selected category
                Handbook.HandbookCategory sel = categoryList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    for (Handbook.HandbookEntry e : sel.entries()) {
                        if (matchesQuery(e, q)) results.add(e);
                    }
                }
            }
            return results;
        };

        // Wire up selection behavior and search behavior
        categoryList.getSelectionModel().selectedItemProperty().addListener((obs, oldCat, selectedCategory) -> {
            // If there's a search query active and "search all" is not checked, refresh search results for new category
            String q = searchField.getText();
            boolean searching = q != null && !q.trim().isEmpty();
            if (searching && !allCategoriesCheck.isSelected()) {
                List<Handbook.HandbookEntry> filtered = searchFn.apply(q, false);
                entryList.getItems().setAll(filtered);
            } else {
                entryList.getItems().clear();
                if (selectedCategory != null) entryList.getItems().addAll(selectedCategory.entries());
            }
        });

        // When search text changes, update entries list accordingly
        final Runnable applySearch = () -> {
            String q = searchField.getText();
            boolean searchAll = allCategoriesCheck.isSelected();
            if (q == null || q.trim().isEmpty()) {
                // If empty, show entries for selected category
                Handbook.HandbookCategory sel = categoryList.getSelectionModel().getSelectedItem();
                entryList.getItems().clear();
                if (sel != null) entryList.getItems().addAll(sel.entries());
            } else {
                List<Handbook.HandbookEntry> res = searchFn.apply(q, searchAll);
                entryList.getItems().setAll(res);
            }
        };

        searchField.textProperty().addListener((obs, old, nw) -> applySearch.run());
        allCategoriesCheck.selectedProperty().addListener((obs, old, nw) -> applySearch.run());

        // Clear button behavior
        clearBtn.setOnAction(e -> {
            searchField.clear();
            allCategoriesCheck.setSelected(false);
            // restore entries of selected category
            Handbook.HandbookCategory sel = categoryList.getSelectionModel().getSelectedItem();
            entryList.getItems().clear();
            if (sel != null) entryList.getItems().addAll(sel.entries());
        });

        // Support Enter key in search field to apply search immediately (already reactive, but helpful)
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) applySearch.run();
        });

        entryList.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, selectedEntry) -> {
            if (selectedEntry != null) {
                entryTitle.setText(selectedEntry.title());
                entrySummary.setText("Summary: " + selectedEntry.summary());
                entryBody.setText(selectedEntry.body());
            } else {
                entryTitle.setText("Select an entry");
                entrySummary.setText("");
                entryBody.setText("");
            }
        });

        // Top of middle column: search controls and 'search all' checkbox in a second row
        HBox searchBottomRow = new HBox(8, allCategoriesCheck);
        searchBottomRow.setAlignment(Pos.CENTER_LEFT);

        middle.getChildren().addAll(searchRow, searchBottomRow, entryList);
        VBox.setVgrow(entryList, Priority.ALWAYS);

        // Layout: HBox with 3 columns
        HBox content = new HBox(12, categoryList, middle, entryDetails);
        content.setPrefHeight(560);
        root.setCenter(content);

        return root;
    }

    private static boolean matchesQuery(Handbook.HandbookEntry e, String q) {
        if (q == null || q.isEmpty()) return true;
        String lc = q.toLowerCase(Locale.ROOT);
        if (e.title() != null && e.title().toLowerCase(Locale.ROOT).contains(lc)) return true;
        if (e.summary() != null && e.summary().toLowerCase(Locale.ROOT).contains(lc)) return true;
        if (e.body() != null && e.body().toLowerCase(Locale.ROOT).contains(lc)) return true;
        return false;
    }
}

