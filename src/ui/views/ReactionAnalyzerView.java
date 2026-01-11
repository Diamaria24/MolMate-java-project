package ui.views;

import core.ReactionAnalyzer;
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
import java.text.SimpleDateFormat;
import java.util.*;

public class ReactionAnalyzerView {

    private static final String REPORT_FILE = "composition_report.csv";

    // Table row model
    public static class OxidationRow {
        private final String compound;
        private final String element;
        private final int oxidationState;

        public OxidationRow(String compound, String element, int oxidationState) {
            this.compound = compound;
            this.element = element;
            this.oxidationState = oxidationState;
        }

        public String getCompound() { return compound; }
        public String getElement() { return element; }
        public int getOxidationState() { return oxidationState; }
    }

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Reaction Properties Analyzer");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        VBox center = new VBox(10);
        center.setPadding(new Insets(12));

        // Equation input
        HBox eqRow = new HBox(8);
        eqRow.setAlignment(Pos.CENTER_LEFT);
        TextField eqField = new TextField();
        eqField.setPrefWidth(500);
        eqField.setPromptText("Enter a BALANCED equation, e.g. Zn + CuSO4 -> ZnSO4 + Cu");
        Button analyzeBtn = new Button("Analyze");
        Button clearBtn = new Button("Clear");
        eqRow.getChildren().addAll(eqField, analyzeBtn, clearBtn);

        // Reaction type
        Label typeLbl = new Label("Reaction Type");
        typeLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        TextArea typeArea = new TextArea();
        typeArea.setEditable(false);
        typeArea.setPrefRowCount(2);

        // Redox analysis
        Label redoxLbl = new Label("Redox Analysis");
        redoxLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        TextArea redoxArea = new TextArea();
        redoxArea.setEditable(false);
        redoxArea.setPrefRowCount(6);

        // Oxidation table
        Label oxLbl = new Label("Oxidation States by Compound");
        oxLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        TableView<OxidationRow> oxTable = new TableView<>();
        oxTable.setPrefHeight(220);
        TableColumn<OxidationRow, String> compCol = new TableColumn<>("Compound");
        compCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getCompound()));
        compCol.setPrefWidth(180);
        TableColumn<OxidationRow, String> elemCol = new TableColumn<>("Element");
        elemCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getElement()));
        elemCol.setPrefWidth(100);
        TableColumn<OxidationRow, String> oxCol = new TableColumn<>("Oxidation State");
        oxCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().getOxidationState())));
        oxCol.setPrefWidth(140);
        oxTable.getColumns().addAll(compCol, elemCol, oxCol);

        // Utility buttons
        HBox utilRow = new HBox(8);
        Button saveBtn = new Button("Save Analysis");
        Button viewCsvBtn = new Button("View CSV Log");
        utilRow.getChildren().addAll(saveBtn, viewCsvBtn);

        center.getChildren().addAll(eqRow, typeLbl, typeArea, redoxLbl, redoxArea, oxLbl, oxTable, utilRow);
        root.setCenter(center);

        // --- Actions ---
        analyzeBtn.setOnAction(e -> {
            String eq = eqField.getText().trim();
            if (eq.isEmpty()) {
                redoxArea.setText("⚠️ Enter a balanced equation first.");
                return;
            }

            try {
                String type = ReactionAnalyzer.classifyReaction(eq);
                String redox = ReactionAnalyzer.analyzeRedox(eq);

                typeArea.setText(type);
                redoxArea.setText(redox);

                // Populate oxidation table (✅ FIXED)
                oxTable.getItems().clear();
                String[] sides = eq.split("->");
                if (sides.length != 2) return;

                String[] reactants = sides[0].split("\\+");
                String[] products = sides[1].split("\\+");

                // Reactants
                for (String compRaw : reactants) {
                    String comp = compRaw.trim().replaceAll("^[0-9]+\\s*", "");
                    Map<String, Integer> states = ReactionAnalyzer.getOxidationStates(comp);
                    for (Map.Entry<String, Integer> entry : states.entrySet()) {
                        oxTable.getItems().add(new OxidationRow(comp + " (Reactant)", entry.getKey(), entry.getValue()));
                    }
                }

                // Products
                for (String compRaw : products) {
                    String comp = compRaw.trim().replaceAll("^[0-9]+\\s*", "");
                    Map<String, Integer> states = ReactionAnalyzer.getOxidationStates(comp);
                    for (Map.Entry<String, Integer> entry : states.entrySet()) {
                        oxTable.getItems().add(new OxidationRow(comp + " (Product)", entry.getKey(), entry.getValue()));
                    }
                }

                // Export results to CSV
                exportToCSV(eq, type, redox, oxTable.getItems());

            } catch (Exception ex) {
                redoxArea.setText("❌ Error: " + ex.getMessage());
            }
        });

        clearBtn.setOnAction(e -> {
            eqField.clear();
            typeArea.clear();
            redoxArea.clear();
            oxTable.getItems().clear();
        });

        saveBtn.setOnAction(e -> {
            try {
                File f = new File("reaction_analysis.txt");
                try (FileWriter fw = new FileWriter(f, false)) {
                    fw.write("Reaction Type:\n" + typeArea.getText() + "\n\n");
                    fw.write("Redox Analysis:\n" + redoxArea.getText() + "\n");
                }
                new Alert(Alert.AlertType.INFORMATION, "💾 Saved to " + f.getAbsolutePath()).showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "❌ Save failed: " + ex.getMessage()).showAndWait();
            }
        });

        viewCsvBtn.setOnAction(e -> {
            try {
                File f = new File(REPORT_FILE);
                if (!f.exists()) {
                    new Alert(Alert.AlertType.INFORMATION, "ℹ️ No CSV log found yet.").showAndWait();
                    return;
                }
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                TextArea ta = new TextArea(content);
                ta.setWrapText(true);
                ta.setEditable(false);
                ta.setPrefSize(800, 500);
                Dialog<Void> d = new Dialog<>();
                d.setTitle("CSV Log");
                d.getDialogPane().setContent(ta);
                d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                d.showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "❌ Failed to read CSV: " + ex.getMessage()).showAndWait();
            }
        });

        return root;
    }

    // ✅ Export analysis to CSV with confirmation
    private static void exportToCSV(String eq, String type, String redox, List<OxidationRow> oxRows) {
        try {
            File csv = new File(REPORT_FILE);
            boolean exists = csv.exists();
            try (PrintWriter pw = new PrintWriter(new FileWriter(csv, true))) {
                if (!exists) {
                    pw.println("Timestamp,Equation,ReactionType,Redox,OxidationStates");
                }
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String oxString = oxRows.stream()
                        .map(r -> r.getCompound() + ":" + r.getElement() + "=" + r.getOxidationState())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("");
                pw.printf("%s,\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        ts, eq, type, redox.replace("\n", " "), oxString);
            }

            Platform.runLater(() -> {
                new Alert(Alert.AlertType.INFORMATION, "✅ Analysis results stored in " + REPORT_FILE).showAndWait();
            });

        } catch (Exception e) {
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.ERROR, "❌ Failed to store results in CSV.").showAndWait();
            });
        }
    }
}
