package ui.views;

import core.PeriodicTable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PeriodicTableView {

    public static Parent getView() {
        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(10));
        layout.setStyle("-fx-background-color: #F5F5DC;");

        // --- Details Panel (Right Side) ---
        VBox detailPanel = new VBox(10);
        detailPanel.setPadding(new Insets(10));
        detailPanel.setPrefWidth(320);
        detailPanel.setStyle("-fx-background-color: white; -fx-border-color: lightgrey; -fx-border-radius: 5; -fx-background-radius: 5;");
        updateDetailPanel(null, detailPanel); // Show initial empty state

        // --- Periodic Table Grid (Center) ---
        GridPane periodicGrid = createPeriodicGrid(detailPanel);

        // --- NEW: Create the color legend ---
        Node legend = createLegend();

        // --- NEW: VBox to hold the table and the legend below it ---
        VBox centerLayout = new VBox(25, periodicGrid, legend);
        centerLayout.setAlignment(Pos.CENTER);

        layout.setCenter(centerLayout);
        layout.setRight(detailPanel);
        return layout;
    }

    private static GridPane createPeriodicGrid(VBox detailPanel) {
        GridPane periodicGrid = new GridPane();
        periodicGrid.setHgap(5);
        periodicGrid.setVgap(5);
        periodicGrid.setAlignment(Pos.CENTER);

        List<PeriodicTable.Element> allElements = PeriodicTable.getAllElements();
        for (PeriodicTable.Element element : allElements) {
            int gridColumn = element.group() - 1;
            int gridRow = element.period() - 1;
            if (element.atomicNumber() >= 57 && element.atomicNumber() <= 71) {
                gridRow = 8;
                gridColumn = element.atomicNumber() - 55;
            } else if (element.atomicNumber() >= 89 && element.atomicNumber() <= 103) {
                gridRow = 9;
                gridColumn = element.atomicNumber() - 87;
            }
            Node elementCell = createElementCell(element, detailPanel);
            periodicGrid.add(elementCell, gridColumn, gridRow);
        }
        return periodicGrid;
    }

    private static Node createElementCell(PeriodicTable.Element element, VBox detailPanel) {
        VBox cell = new VBox(2);
        cell.setPrefSize(55, 55);
        cell.setAlignment(Pos.CENTER);
        String bgColor = PeriodicTable.getHexColorForCategory(element.category());
        cell.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: black; -fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;");

        Label numberLabel = new Label(String.valueOf(element.atomicNumber()));
        numberLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 9));
        Label symbolLabel = new Label(element.symbol());
        symbolLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        cell.getChildren().addAll(numberLabel, symbolLabel);
        cell.setOnMouseClicked(e -> updateDetailPanel(element, detailPanel));
        return cell;
    }

    private static void updateDetailPanel(PeriodicTable.Element element, VBox detailPanel) {
        detailPanel.getChildren().clear();

        if (element == null) {
            Label title = new Label("Select an Element");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            detailPanel.getChildren().add(title);
            return;
        }

        Label title = new Label(element.name() + " (" + element.symbol() + ")");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setWrapText(true);

        // --- UPDATED: Added Group and Period to the details ---
        Label number = new Label("Atomic Number: " + element.atomicNumber());
        Label mass = new Label("Atomic Mass: " + String.format("%.3f u", element.atomicMass()));
        Label group = new Label("Group: " + element.group());
        Label period = new Label("Period: " + element.period());
        Label category = new Label("Category: " + element.category());
        Label summary = new Label(element.summary());
        summary.setWrapText(true);
        summary.setTextAlignment(TextAlignment.JUSTIFY);

        detailPanel.getChildren().addAll(title, new Separator(), number, mass, group, period, category, new Separator(), summary);
    }

    /** --- NEW: A helper method to create the legend --- */
    private static Node createLegend() {
        GridPane legendGrid = new GridPane();
        legendGrid.setHgap(10);
        legendGrid.setVgap(5);
        legendGrid.setAlignment(Pos.CENTER);

        // Get a unique list of categories and their colors
        Map<String, String> categoryColors = new LinkedHashMap<>();
        for(PeriodicTable.Element e : PeriodicTable.getAllElements()) {
            categoryColors.putIfAbsent(e.category(), PeriodicTable.getHexColorForCategory(e.category()));
        }

        int col = 0;
        int row = 0;
        for (Map.Entry<String, String> entry : categoryColors.entrySet()) {
            Rectangle colorRect = new Rectangle(15, 15, Color.web(entry.getValue()));
            colorRect.setStroke(Color.BLACK);
            Label nameLabel = new Label(entry.getKey());

            legendGrid.add(colorRect, col, row);
            legendGrid.add(nameLabel, col + 1, row);

            col += 2; // Move to the next spot in the grid
            if (col >= 10) { // Create 5 columns of legend items
                col = 0;
                row++;
            }
        }
        return legendGrid;
    }
}