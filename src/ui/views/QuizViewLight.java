package ui.views;

import learning.QuizProgress;
import learning.Quiz;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

/**
 * Light-weight GUI quiz that reuses the CLI question generator (core.Quiz.generateQuestion).
 * Requires:
 *  - core.Quiz.generateQuestion(...) is public static
 *  - core.Quiz.QuizQuestion is public (static) record
 */
public class QuizViewLight {

    public static Node getView(String username, Runnable onFinish) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label header = new Label("Chemistry Quiz (Light)");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        VBox left = new VBox(8);
        left.setPadding(new Insets(8));
        ChoiceBox<String> difficulty = new ChoiceBox<>();
        difficulty.getItems().addAll("EASY","MEDIUM","HARD"); difficulty.setValue("MEDIUM");
        ChoiceBox<String> category = new ChoiceBox<>();
        category.getItems().addAll("ALL","MOLAR_MASS","BALANCING","ORGANIC","PERIODIC_TABLE","REACTION_PREDICTION"); category.setValue("ALL");
        Spinner<Integer> nSpinner = new Spinner<>(1,50,5);
        CheckBox timed = new CheckBox("Timed (40s)");
        Button start = new Button("Start");

        left.getChildren().addAll(new Label("Difficulty"), difficulty, new Label("Category"), category, new Label("Questions"), nSpinner, timed, start);

        VBox center = new VBox(8);
        center.setPadding(new Insets(8));
        TextArea qArea = new TextArea(); qArea.setEditable(false); qArea.setWrapText(true); qArea.setPrefRowCount(4);
        TextField ansField = new TextField();
        Button submit = new Button("Submit");
        Label score = new Label("Score: 0 / 0");
        TextArea log = new TextArea(); log.setEditable(false);
        center.getChildren().addAll(qArea, ansField, submit, score, log);

        root.setLeft(left); root.setCenter(center);

        final List<Quiz.QuizQuestion> questions = new ArrayList<>();
        final int[] idx = {-1}; final int[] s = {0};

        start.setOnAction(ev -> {
            start.setDisable(true); questions.clear(); s[0]=0; idx[0]=-1; log.clear();
            int count = nSpinner.getValue();
            // generate questions on FX thread quickly (small number)
            for (int i=0;i<count;i++) {
                Quiz.QuizQuestion qq = Quiz.generateQuestion(Quiz.Difficulty.valueOf(difficulty.getValue()), Quiz.Category.valueOf(category.getValue()));
                if (qq != null) questions.add(qq);
            }
            if (questions.isEmpty()) {
                log.appendText("Could not generate questions.\n");
                start.setDisable(false);
                return;
            }
            idx[0]=0; displayLightQuestion(questions.get(0), qArea, ansField);
            score.setText("Score: 0 / " + questions.size());
        });

        submit.setOnAction(ev -> {
            if (idx[0] < 0 || idx[0] >= questions.size()) return;
            Quiz.QuizQuestion q = questions.get(idx[0]);
            String user = ansField.getText().trim();
            boolean ok = checkLightAnswer(user, q.correctAnswer());
            if (ok) { log.appendText(String.format("✔ Q%d correct%n", idx[0]+1)); s[0]++; }
            else { log.appendText(String.format("✖ Q%d incorrect; correct: %s%n", idx[0]+1, q.correctAnswer())); }
            idx[0]++;
            if (idx[0] < questions.size()) {
                displayLightQuestion(questions.get(idx[0]), qArea, ansField);
            } else {
                // finished
                log.appendText(String.format("%nQuiz finished: %d / %d%n", s[0], questions.size()));
                // update progress
                QuizProgress qp = new QuizProgress(username);
                qp.updateStat("total_answered", questions.size());
                qp.updateStat("total_correct", s[0]);
                qp.saveProgress();
                // call onFinish to refresh dashboard
                if (onFinish != null) Platform.runLater(onFinish);
            }
            score.setText(String.format("Score: %d / %d", s[0], questions.size()));
        });

        return root;
    }

    private static void displayLightQuestion(Quiz.QuizQuestion q, TextArea qArea, TextField ansField) {
        qArea.setText(q.questionText());
        ansField.clear();
    }

    private static boolean checkLightAnswer(String user, String correct) {
        if (user == null) user = "";
        return user.trim().equalsIgnoreCase(correct.trim());
    }
}

