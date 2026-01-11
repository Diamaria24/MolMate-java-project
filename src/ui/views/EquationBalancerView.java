package ui.views;

import core.EquationBalancer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;

/**
 * EquationBalancerView - updated to properly call augmented/redox balancing
 * (if core provides balanceRedox) while keeping all other features intact.
 */
public class EquationBalancerView {

    private static final String REPORT_FILE = "composition_report.csv";

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        // Header
        Label header = new Label("Equation Balancer");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        // Center layout
        VBox center = new VBox(12);
        center.setPadding(new Insets(12));
        center.setPrefWidth(860);

        // Input row
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        TextField equationField = new TextField();
        equationField.setPromptText("Enter equation, e.g. Fe + O2 -> Fe2O3");
        equationField.setPrefWidth(540);

        CheckBox alsoPrintConsole = new CheckBox("Also print debug to console");
        alsoPrintConsole.setSelected(false);

        Button balanceBtn = new Button("Balance");
        balanceBtn.setPrefWidth(100);

        Button clearBtn = new Button("Clear");
        clearBtn.setPrefWidth(80);

        inputRow.getChildren().addAll(equationField, balanceBtn, clearBtn, alsoPrintConsole);

        // Options row
        HBox optsRow = new HBox(8);
        optsRow.setAlignment(Pos.CENTER_LEFT);

        Label modeLabel = new Label("Mode:");
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton normalMode = new RadioButton("Normal");
        normalMode.setToggleGroup(modeGroup);
        normalMode.setSelected(true);
        RadioButton augmentedMode = new RadioButton("Augmented (adds species if supported)");
        augmentedMode.setToggleGroup(modeGroup);

        ChoiceBox<String> mediumChoice = new ChoiceBox<>();
        mediumChoice.getItems().addAll("none", "acidic", "basic");
        mediumChoice.setValue("none");

        optsRow.getChildren().addAll(modeLabel, normalMode, augmentedMode, new Label("Medium:"), mediumChoice);

        // Output areas
        Label resultLabel = new Label("Balanced Equation:");
        TextField balancedField = new TextField();
        balancedField.setEditable(false);
        balancedField.setPrefWidth(800);

        Label coeffLabel = new Label("Coefficients (compound -> coeff):");
        TextArea coeffArea = new TextArea();
        coeffArea.setEditable(false);
        coeffArea.setWrapText(true);
        coeffArea.setPrefRowCount(6);

        Label consoleLabel = new Label("Activity / Logs:");
        TextArea consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(true);
        consoleArea.setPrefRowCount(10);

        // Utility buttons
        HBox utilRow = new HBox(8);
        utilRow.setAlignment(Pos.CENTER_LEFT);
        Button viewCsvBtn = new Button("View CSV Report");
        Button openCsvFolderBtn = new Button("Open CSV Location");
        Button saveResultBtn = new Button("Save Balanced Eq.");
        utilRow.getChildren().addAll(viewCsvBtn, openCsvFolderBtn, saveResultBtn);

        center.getChildren().addAll(inputRow, optsRow, resultLabel, balancedField, coeffLabel, coeffArea, consoleLabel, consoleArea, utilRow);
        root.setCenter(center);

        // --------------------
        // Actions
        // --------------------

        clearBtn.setOnAction(e -> {
            equationField.clear();
            balancedField.clear();
            coeffArea.clear();
            consoleArea.clear();
        });

        balanceBtn.setOnAction(e -> {
            String eq = Optional.ofNullable(equationField.getText()).orElse("").trim();
            if (eq.isEmpty()) {
                consoleArea.appendText("⚠️ Enter an equation first.\n");
                return;
            }

            boolean wantConsolePrint = alsoPrintConsole.isSelected();
            boolean useAugmented = augmentedMode.isSelected();
            String medium = mediumChoice.getValue();
            if (medium == null) medium = "none";

            try {
                String balanced = null;
                boolean usedAugmented = false;

                if (useAugmented && !"none".equalsIgnoreCase(medium)) {
                    // Try to find and call balanceRedox variants reflectively.
                    // Prefer: balanceRedox(String eq, String medium, boolean isSilent)
                    try {
                        Method m = EquationBalancer.class.getMethod("balanceRedox", String.class, String.class, boolean.class);
                        // call silent to capture in GUI
                        balanced = (String) m.invoke(null, eq, medium, true);
                        usedAugmented = true;
                        consoleArea.appendText("ℹ️ Called EquationBalancer.balanceRedox(eq, medium, silent=true).\n");
                        if (wantConsolePrint) {
                            // also call non-silent for console output
                            try { m.invoke(null, eq, medium, false); } catch (Exception ex) { /* ignore console-only failure */ }
                        }
                    } catch (NoSuchMethodException ns1) {
                        // Try balanceRedox(String, String)
                        try {
                            Method m2 = EquationBalancer.class.getMethod("balanceRedox", String.class, String.class);
                            balanced = (String) m2.invoke(null, eq, medium);
                            usedAugmented = true;
                            consoleArea.appendText("ℹ️ Called EquationBalancer.balanceRedox(eq, medium).\n");
                            if (wantConsolePrint) {
                                // Many two-arg variants are likely silent; we attempt to call normal balance for console output
                                try { EquationBalancer.balance(eq, false); } catch (Exception ex) { /* ignore */ }
                            }
                        } catch (NoSuchMethodException ns2) {
                            // No balanceRedox present
                            usedAugmented = false;
                            consoleArea.appendText("ℹ️ balanceRedox not found in core. Falling back to normal balance().\n");
                        }
                    }
                }

                if (!useAugmented || !usedAugmented) {
                    // Normal balancing path (call silent to capture result)
                    balanced = EquationBalancer.balance(eq, true);
                    if (wantConsolePrint) {
                        try { EquationBalancer.balance(eq, false); } catch (Exception ex) { /* ignore */ }
                    }
                    if (useAugmented && !usedAugmented) {
                        consoleArea.appendText("⚠️ Augmented requested but not available; normal balancing used.\n");
                    }
                }

                if (balanced == null) {
                    consoleArea.appendText("❌ Balancing returned no result.\n");
                    return;
                }

                balancedField.setText(balanced);
                consoleArea.appendText("✅ Balanced: " + balanced + "\n");

                // Parse coefficients using core utility
                try {
                    Map<String, Integer> coeffs = EquationBalancer.parseCoefficients(balanced);
                    StringBuilder sb = new StringBuilder();
                    coeffs.forEach((cmp, cval) -> sb.append(cmp).append(" -> ").append(cval).append("\n"));
                    coeffArea.setText(sb.toString());
                    consoleArea.appendText("ℹ️ Coefficients parsed and shown.\n");
                } catch (Exception ex) {
                    coeffArea.setText("");
                    consoleArea.appendText("⚠️ Could not parse coefficients: " + ex.getMessage() + "\n");
                }

                consoleArea.appendText("ℹ️ Result logged to " + REPORT_FILE + " by core (if export is enabled).\n");
            } catch (Exception ex) {
                consoleArea.appendText("❌ Error: " + ex.getMessage() + "\n");
            }
        });

        // View CSV report
        viewCsvBtn.setOnAction(e -> {
            File csv = new File(REPORT_FILE);
            if (!csv.exists()) {
                consoleArea.appendText("ℹ️ No report file found (" + REPORT_FILE + ").\n");
                return;
            }
            try {
                String content = Files.readString(csv.toPath(), StandardCharsets.UTF_8);
                TextArea ta = new TextArea(content);
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefSize(800, 500);
                Dialog<Void> d = new Dialog<>();
                d.getDialogPane().setContent(ta);
                d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                d.setTitle("CSV Report: " + REPORT_FILE);
                d.showAndWait();
            } catch (IOException ex) {
                consoleArea.appendText("❌ Failed to read CSV: " + ex.getMessage() + "\n");
            }
        });

        // Open CSV folder
        openCsvFolderBtn.setOnAction(e -> {
            try {
                File csv = new File(REPORT_FILE);
                File folder = csv.getAbsoluteFile().getParentFile();
                if (folder == null || !folder.exists()) folder = new File(".").getAbsoluteFile();
                java.awt.Desktop.getDesktop().open(folder);
                consoleArea.appendText("📁 Opened folder: " + folder.getAbsolutePath() + "\n");
            } catch (Exception ex) {
                consoleArea.appendText("⚠️ Could not open folder: " + ex.getMessage() + "\n");
            }
        });

        // Save balanced equation to file
        saveResultBtn.setOnAction(e -> {
            String balanced = balancedField.getText();
            if (balanced == null || balanced.isBlank()) {
                consoleArea.appendText("⚠️ No balanced result to save. Balance an equation first.\n");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save balanced equation");
            chooser.setInitialFileName("balanced_equation.txt");
            Window w = root.getScene() != null ? root.getScene().getWindow() : null;
            File file = chooser.showSaveDialog(w);
            if (file != null) {
                try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
                    fw.write(balanced);
                    consoleArea.appendText("💾 Saved balanced equation to " + file.getAbsolutePath() + "\n");
                } catch (IOException ex) {
                    consoleArea.appendText("❌ Failed to save: " + ex.getMessage() + "\n");
                }
            }
        });

        return root;
    }
}

