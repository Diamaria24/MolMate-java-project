package ui.views;

import core.UnitConverter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Optional;

/**
 * GUI wrapper for core.UnitConverter
 *
 * - Keeps all features: factor-based conversions, temperature special cases,
 *   mole <-> particles via Avogadro, scientific-formatting rules and informative errors.
 * - Supports structured fields and free-text input like the CLI ("150 g to oz").
 */
public class UnitConverterView {

    private static final DecimalFormat DF_STD = new DecimalFormat("#.####");
    private static final DecimalFormat DF_SCI = new DecimalFormat("0.####E0");

    public static Node getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Unit Converter");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        VBox body = new VBox(10);
        body.setPadding(new Insets(10));

        // Row 1: Free-text input (CLI-like)
        HBox freeRow = new HBox(8);
        freeRow.setAlignment(Pos.CENTER_LEFT);
        TextField freeField = new TextField();
        freeField.setPromptText("Free input (e.g. '150 g to oz' or '72 F to C')");
        freeField.setPrefWidth(560);
        Button freeConvertBtn = new Button("Convert (free)");
        freeRow.getChildren().addAll(freeField, freeConvertBtn);

        // Row 2: Structured input
        HBox structuredRow = new HBox(8);
        structuredRow.setAlignment(Pos.CENTER_LEFT);
        TextField valueField = new TextField();
        valueField.setPromptText("Value (e.g. 150)");
        valueField.setPrefWidth(110);
        TextField fromUnitField = new TextField();
        fromUnitField.setPromptText("From unit (e.g. g, C, mol)");
        fromUnitField.setPrefWidth(120);
        Button swapBtn = new Button("⇄");
        TextField toUnitField = new TextField();
        toUnitField.setPromptText("To unit (e.g. oz, K, atoms)");
        toUnitField.setPrefWidth(120);
        Button structConvertBtn = new Button("Convert");
        Button clearBtn = new Button("Clear");
        structuredRow.getChildren().addAll(new Label("Value:"), valueField, new Label("From:"), fromUnitField, swapBtn, new Label("To:"), toUnitField, structConvertBtn, clearBtn);

        // Result row
        HBox resultRow = new HBox(8);
        resultRow.setAlignment(Pos.CENTER_LEFT);
        TextField resultField = new TextField();
        resultField.setEditable(false);
        resultField.setPrefWidth(520);
        Button copyBtn = new Button("Copy");
        resultRow.getChildren().addAll(new Label("Result:"), resultField, copyBtn);

        // Quick examples
        HBox exampleRow = new HBox(8);
        exampleRow.setAlignment(Pos.CENTER_LEFT);
        Button ex1 = new Button("150 g -> oz");
        Button ex2 = new Button("72 F -> C");
        Button ex3 = new Button("0.05 mol -> atoms");
        Button ex4 = new Button("10 L -> mol (gas STP)");
        exampleRow.getChildren().addAll(new Label("Examples:"), ex1, ex2, ex3, ex4);

        // History / logs
        Label histLabel = new Label("Activity / History:");
        TextArea historyArea = new TextArea();
        historyArea.setEditable(false);
        historyArea.setWrapText(true);
        historyArea.setPrefRowCount(10);

        // Utilities row
        HBox utilRow = new HBox(8);
        utilRow.setAlignment(Pos.CENTER_LEFT);
        Button saveHistoryBtn = new Button("Save History");
        Button clearHistoryBtn = new Button("Clear History");
        utilRow.getChildren().addAll(saveHistoryBtn, clearHistoryBtn);

        body.getChildren().addAll(freeRow, structuredRow, resultRow, exampleRow, histLabel, historyArea, utilRow);
        root.setCenter(body);

        // --- Actions ---

        // Helper: format result using same logic as CLI: scientific if >1e5 or <1e-3 (and not zero)
        var formatResult = (java.util.function.Consumer<Double>) (Double value) -> {
            String formatted;
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                formatted = String.valueOf(value);
            } else if ((Math.abs(value) > 1e5) || (Math.abs(value) < 1e-3 && value != 0.0)) {
                formatted = DF_SCI.format(value);
            } else {
                formatted = DF_STD.format(value);
            }
            resultField.setText(formatted);
        };

        // perform conversion via core UnitConverter and return text & numeric result
        java.util.function.BiConsumer<String, String> doConvertStructured = (fromRaw, toRaw) -> {
            try {
                String vtext = valueField.getText();
                if (vtext == null || vtext.isBlank()) {
                    historyArea.appendText("⚠️ Enter a value to convert.\n");
                    return;
                }
                double val = Double.parseDouble(vtext.trim());
                String from = fromRaw;
                String to = toRaw;
                double res = UnitConverter.convert(from, to, val);

                // build descriptive text like CLI
                String desc;
                if ((Math.abs(res) > 1e5) || (Math.abs(res) < 1e-3 && res != 0.0)) {
                    desc = String.format("✅ %s %s = %s %s%n", DF_STD.format(val), from, DF_SCI.format(res), to);
                } else {
                    desc = String.format("✅ %s %s = %s %s%n", DF_STD.format(val), from, DF_STD.format(res), to);
                }
                historyArea.appendText(desc);
                formatResult.accept(res);
            } catch (NumberFormatException nfe) {
                historyArea.appendText("❌ Invalid number format: " + nfe.getMessage() + "\n");
                resultField.setText("");
            } catch (IllegalArgumentException iae) {
                historyArea.appendText("❌ Error: " + iae.getMessage() + "\n");
                resultField.setText("");
            } catch (Exception ex) {
                historyArea.appendText("❌ Unexpected error: " + ex.getMessage() + "\n");
                resultField.setText("");
            }
        };

        // structured convert button
        structConvertBtn.setOnAction(e -> {
            String from = Optional.ofNullable(fromUnitField.getText()).orElse("").trim();
            String to = Optional.ofNullable(toUnitField.getText()).orElse("").trim();
            if (from.isEmpty() || to.isEmpty()) {
                historyArea.appendText("⚠️ Fill both unit fields (From / To) or use the free input box.\n");
                return;
            }
            doConvertStructured.accept(from, to);
        });

        // swap units
        swapBtn.setOnAction(e -> {
            String a = fromUnitField.getText();
            fromUnitField.setText(toUnitField.getText());
            toUnitField.setText(a);
        });

        // free-form convert (CLI-like parsing)
        freeConvertBtn.setOnAction(e -> {
            String in = Optional.ofNullable(freeField.getText()).orElse("").trim();
            if (in.isEmpty()) {
                historyArea.appendText("⚠️ Type something like '150 g to oz' in the free input field.\n");
                return;
            }
            // simple parser similar to CLI: "<value> <from> to <to>"
            String[] parts = in.split("\\s+");
            if (parts.length == 4 && parts[2].equalsIgnoreCase("to")) {
                try {
                    double val = Double.parseDouble(parts[0]);
                    String from = parts[1];
                    String to = parts[3];
                    double res = UnitConverter.convert(from, to, val);
                    String desc;
                    if ((Math.abs(res) > 1e5) || (Math.abs(res) < 1e-3 && res != 0.0)) {
                        desc = String.format("✅ %s %s = %s %s%n", DF_STD.format(val), from, DF_SCI.format(res), to);
                    } else {
                        desc = String.format("✅ %s %s = %s %s%n", DF_STD.format(val), from, DF_STD.format(res), to);
                    }
                    historyArea.appendText(desc);
                    formatResult.accept(res);
                } catch (NumberFormatException nfe) {
                    historyArea.appendText("❌ Invalid number format in free input: " + nfe.getMessage() + "\n");
                    resultField.setText("");
                } catch (IllegalArgumentException iae) {
                    historyArea.appendText("❌ Error: " + iae.getMessage() + "\n");
                    resultField.setText("");
                } catch (Exception ex) {
                    historyArea.appendText("❌ Unexpected error: " + ex.getMessage() + "\n");
                    resultField.setText("");
                }
            } else {
                historyArea.appendText("❌ Invalid free-input format. Use: <value> <from_unit> to <to_unit>\n");
            }
        });

        // copy result to clipboard
        copyBtn.setOnAction(e -> {
            String txt = resultField.getText();
            if (txt == null || txt.isBlank()) {
                historyArea.appendText("⚠️ Nothing to copy.\n");
                return;
            }
            ClipboardContent cc = new ClipboardContent();
            cc.putString(txt);
            Clipboard.getSystemClipboard().setContent(cc);
            historyArea.appendText("📋 Result copied to clipboard: " + txt + "\n");
        });

        // examples
        ex1.setOnAction(e -> {
            freeField.setText("150 g to oz");
            freeConvertBtn.fire();
        });
        ex2.setOnAction(e -> {
            freeField.setText("72 F to C");
            freeConvertBtn.fire();
        });
        ex3.setOnAction(e -> {
            freeField.setText("0.05 mol to atoms");
            freeConvertBtn.fire();
        });
        ex4.setOnAction(e -> {
            freeField.setText("10 L to mol");
            freeConvertBtn.fire();
        });

        // clear fields
        clearBtn.setOnAction(e -> {
            valueField.clear();
            fromUnitField.clear();
            toUnitField.clear();
            resultField.clear();
        });

        // save / clear history
        saveHistoryBtn.setOnAction(e -> {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Save Unit Converter History");
                chooser.setInitialFileName("unit_converter_history.txt");
                Window w = root.getScene() != null ? root.getScene().getWindow() : null;
                File file = chooser.showSaveDialog(w);
                if (file != null) {
                    Files.writeString(file.toPath(), historyArea.getText(), StandardCharsets.UTF_8);
                }
            } catch (Exception ex) {
                historyArea.appendText("❌ Failed to save history: " + ex.getMessage() + "\n");
            }
        });

        clearHistoryBtn.setOnAction(e -> historyArea.clear());

        return root;
    }
}
