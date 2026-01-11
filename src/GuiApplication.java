// Add this one line to import all view classes
import ui.views.*;
import core.*;
import learning.*;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import learning.FlashcardProgress;
import learning.Handbook;
import learning.QuizProgress;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * GuiApplication with:
 * - Discover -> immediate switch to Login (no cinematic transition)
 * - Login validates against a local user store (~/.molmate_users.txt)
 * - Register support
 * - "Remember me" stores encrypted username to ~/.molmate_remember
 * - Header with avatar + centered title on main app
 * - Learning dashboard + progress tracking
 */
public class GuiApplication extends Application {

    private Stage primaryStage;
    private Scene discoverScene;
    private Scene loginScene;
    private Scene mainAppScene;
    private String currentUser = "guest";


    private final UserStore userStore = new UserStore();
    private final RememberStore rememberStore = new RememberStore();

    private ProgressBar flashcardsProgressBar = null;
    private Label flashcardsPercentLabel = null;

    // ----- Add these fields near other per-app stores/fields -----
    private final Path themeFile = Paths.get(System.getProperty("user.home"), ".molmate_theme");
    private final Path historyFile = Paths.get(System.getProperty("user.home"), ".molmate_history");
    private final Path favoritesFile = Paths.get(System.getProperty("user.home"), ".molmate_favorites");

    // in-memory caches
    private List<String> historyEntries = new ArrayList<>();
    private Set<String> favoriteSet = new LinkedHashSet<>(); // preserve insertion order

    // REMOVABLE listener reference so we can unregister on stop()
    private java.util.function.Consumer<Double> flashcardsListener = null;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("MolMate");

        // Build scenes first
        discoverScene = new Scene(createDiscoverLayout(), 1280, 720);
        loginScene = new Scene(createLoginLayout(), 1280, 720);
        mainAppScene = new Scene(createMainAppLayout(), 1280, 720);

        // Instead of maximizing, size the stage to the visual bounds (will fit typical laptop screen)
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double scale = 0.98; // keep some margin from edges
        double targetW = Math.max(900, vb.getWidth() * scale);
        double targetH = Math.max(600, vb.getHeight() * scale);
        primaryStage.setWidth(targetW);
        primaryStage.setHeight(targetH);
        primaryStage.setX((vb.getWidth() - targetW) / 2 + vb.getMinX());
        primaryStage.setY((vb.getHeight() - targetH) / 2 + vb.getMinY());

        primaryStage.setScene(discoverScene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // remove registered FlashcardProgress listener to avoid leaking references
        try {
            if (flashcardsListener != null) {
                FlashcardProgress.removeProgressListener(flashcardsListener);
                flashcardsListener = null;
            }
            // If you also used the older FlashcardsView global listener, make sure to clear it as well.
            FlashcardsView.unregisterProgressListener();
        } catch (Throwable t) {
            System.err.println("Error while unregistering flashcards listener: " + t.getMessage());
        }
        super.stop();
    }


    // ---------------------------
    // Discover layout (start screen)
    // ---------------------------
    private Parent createDiscoverLayout() {
        StackPane root = new StackPane();

        ImageView background = new ImageView(new Image("background.jpg"));
        background.setPreserveRatio(true);
        background.fitWidthProperty().bind(root.widthProperty());
        background.fitHeightProperty().bind(root.heightProperty());

        Region overlay = new Region();
        overlay.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(245,245,220,0.72), rgba(245,245,220,0.44));");
        overlay.prefWidthProperty().bind(root.widthProperty());
        overlay.prefHeightProperty().bind(root.heightProperty());

        Label title = new Label("MolMate");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 90));
        title.setTextFill(Color.web("#1E3A8A"));
        title.setEffect(new DropShadow(10, Color.color(0,0,0,0.25)));
        addGlowOnHover(title, Color.web("#1E3A8A"), Color.web("#27408B"));

        Label tagline = new Label("Your personal Chemistry Companion");
        tagline.setFont(Font.font("Arial", FontWeight.NORMAL, 28));
        tagline.setTextFill(Color.web("#00695c"));
        tagline.setWrapText(true);
        tagline.setMaxWidth(840);
        tagline.setEffect(new DropShadow(6, Color.color(0,0,0,0.18)));
        addGlowOnHover(tagline, Color.web("#00695c"), Color.web("#00897b"));

        VBox titleBox = new VBox(10, title, tagline);
        titleBox.setAlignment(Pos.CENTER);

        Label description = new Label(
                "MolMate is a comprehensive toolkit for students and professionals, featuring calculators, learning tools, a reaction database, and more."
        );
        description.setWrapText(true);
        description.setMaxWidth(520);
        description.setFont(Font.font("Arial", 18));
        description.setTextFill(Color.web("#1E3A8A"));

        Button discoverButton = new Button("Discover");
        discoverButton.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #b2fcb2, #70db70);" +
                        "-fx-text-fill: #2b2b2b; -fx-font-size: 18px; -fx-padding: 10px 30px; -fx-background-radius: 20;"
        );
        addButtonHoverEffects(discoverButton);

        BorderPane contentLayout = new BorderPane();
        VBox centerBox = new VBox(14, titleBox, description);
        centerBox.setAlignment(Pos.CENTER);
        contentLayout.setCenter(centerBox);
        contentLayout.setBottom(discoverButton);
        BorderPane.setAlignment(discoverButton, Pos.CENTER);
        contentLayout.setPadding(new Insets(0,0,80,0));

        root.getChildren().addAll(background, overlay, contentLayout);

        root.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            double mw = Math.max(360, Math.min(1000, w * 0.6));
            tagline.setMaxWidth(mw);
            double titleSize = clamp(w / 1280.0, 0.6, 1.3) * 90;
            title.setFont(Font.font("Arial", FontWeight.BOLD, titleSize));
        });

        // ---------- IMMEDIATE NAVIGATION (NO ANIMATION) ----------
        discoverButton.setOnAction(e -> {
            // Immediately switch to loginScene (retain hover image effect on login)
            primaryStage.setScene(loginScene);
        });

        return root;
    }

    // ---------------------------
    // Login layout (separate full page) with validation, register & remember
    // ---------------------------
    private Parent createLoginLayout() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #FAF9F6;"); // cream

        HBox main = new HBox();
        main.setPadding(new Insets(30));
        main.setSpacing(40);

        // LEFT BOX: login form
        VBox leftBox = new VBox(14);
        leftBox.setPadding(new Insets(28));
        leftBox.setAlignment(Pos.TOP_CENTER);
        leftBox.setPrefWidth(420);
        leftBox.setStyle("-fx-background-color: linear-gradient(#ffffff, #f3f3f1); -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: rgba(0,0,0,0.06); -fx-border-width:1;");

        Label formTitle = new Label("Welcome back");
        formTitle.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        formTitle.setTextFill(Color.web("#2F4F4F"));

        Label formSub = new Label("Sign in to continue to MolMate");
        formSub.setFont(Font.font(14));
        formSub.setTextFill(Color.web("#555555"));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(Double.MAX_VALUE);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(Double.MAX_VALUE);

        CheckBox remember = new CheckBox("Remember me");
        remember.setSelected(false);

        // If we have a remembered user, prefill & check box
        String remembered = rememberStore.loadRememberedUsername();
        if (remembered != null && !remembered.isBlank()) {
            usernameField.setText(remembered);
            remember.setSelected(true);
        }

        HBox actionRow = new HBox(8);
        Button loginBtn = new Button("Login");
        loginBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #98fb98, #70db70); -fx-font-weight:bold; -fx-padding: 10 12; -fx-background-radius:6;");
        addButtonHoverEffects(loginBtn);

        Button registerBtn = new Button("Register");
        registerBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #ffdba6, #ffb86b); -fx-font-weight:bold; -fx-padding: 10 12; -fx-background-radius:6;");
        addButtonHoverEffects(registerBtn);

        actionRow.getChildren().addAll(loginBtn, registerBtn);
        actionRow.setAlignment(Pos.CENTER);

        Label loginStatus = new Label();
        loginStatus.setTextFill(Color.web("#cc0000"));

        leftBox.getChildren().addAll(formTitle, formSub, usernameField, passwordField, remember, actionRow, loginStatus);

        // RIGHT BOX: visual image + hover transitions
        StackPane imagePane = new StackPane();
        imagePane.setAlignment(Pos.CENTER);
        imagePane.setStyle("-fx-background-color: transparent;");
        ImageView imageView = new ImageView(new Image("background.jpg"));
        imageView.setPreserveRatio(true);
        imageView.fitHeightProperty().bind(primaryStage.heightProperty().multiply(0.78));
        imageView.setSmooth(true);
        imageView.setEffect(new DropShadow(12, Color.color(0,0,0,0.18)));

        imagePane.getChildren().add(imageView);

        // keep hover effect
        addImageHoverEffects(imageView);

        HBox.setHgrow(imagePane, Priority.ALWAYS);
        HBox.setHgrow(leftBox, Priority.NEVER);

        main.getChildren().addAll(leftBox, imagePane);
        root.setCenter(main);

        Label pageHeader = new Label("MolMate");
        pageHeader.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        pageHeader.setTextFill(Color.web("#27408B"));
        pageHeader.setPadding(new Insets(12));
        BorderPane.setAlignment(pageHeader, Pos.CENTER);
        root.setTop(pageHeader);

        // Actions: login / register
        Runnable doLogin = () -> {
            String u = Optional.ofNullable(usernameField.getText()).orElse("").trim();
            String p = Optional.ofNullable(passwordField.getText()).orElse("");
            if (u.isEmpty()) {
                loginStatus.setText("Please enter a username.");
                usernameField.requestFocus();
                return;
            }
            boolean ok = userStore.validate(u, p);
            if (!ok) {
                loginStatus.setText("Invalid username or password.");
                return;
            }
            // Successful login
            this.currentUser = u;

            // If remember checked -> store encrypted username; else remove remembered file
            if (remember.isSelected()) {
                rememberStore.saveRememberedUsername(u);
            } else {
                rememberStore.clearRememberedUsername();
            }

            mainAppScene = new Scene(createMainAppLayout(), primaryStage.getWidth(), primaryStage.getHeight());
            primaryStage.setScene(mainAppScene);
        };

        loginBtn.setOnAction(e -> doLogin.run());
        passwordField.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) doLogin.run(); });

        registerBtn.setOnAction(e -> {
            String u = Optional.ofNullable(usernameField.getText()).orElse("").trim();
            String p = Optional.ofNullable(passwordField.getText()).orElse("");
            if (u.isEmpty() || p.isEmpty()) {
                loginStatus.setText("Enter username and password to register.");
                return;
            }
            boolean created = userStore.register(u, p);
            if (!created) {
                loginStatus.setText("User already exists. Choose another username.");
            } else {
                loginStatus.setText("Account created — you can now login.");
            }
        });

        Platform.runLater(() -> {
            if (primaryStage.getHeight() > 0) imageView.setFitHeight(primaryStage.getHeight() * 0.78);
        });

        return root;
    }



    // ---------------------------
    // Main application layout (learning dashboard)
    // includes header with avatar on left + centered title
    // ---------------------------
    private Parent createMainAppLayout() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #F5F5DC;"); // beige background

        // TOP: header with avatar on left and centered title
        BorderPane top = new BorderPane();
        top.setPadding(new Insets(8, 14, 8, 14));
        top.setStyle("-fx-border-color: rgba(0,0,0,0.06); -fx-border-width: 0 0 1 0;");

        // left: avatar + username
        HBox left = new HBox(10);
        left.setAlignment(Pos.CENTER_LEFT);
        Node avatar = createAvatarNode(currentUser, 28);
        Label userLabel = new Label(" " + currentUser);
        userLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        userLabel.setTextFill(Color.web("#333333"));
        left.getChildren().addAll(avatar, userLabel);

        // center: title
        Label centerTitle = new Label("MolMate: Your Personal Chemistry Companion");
        centerTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        centerTitle.setTextFill(Color.web("#8A2BE2"));
        HBox centerBox = new HBox(centerTitle);
        centerBox.setAlignment(Pos.CENTER);

        // right: controls
        HBox right = new HBox(8);
        right.setAlignment(Pos.CENTER_RIGHT);
        Button history = new Button("History");
        Button fav = new Button("Favorites");
        ToggleButton theme = new ToggleButton("Dark Mode");

        // load caches from disk at UI init
        loadHistoryAndFavorites();

        // history button opens dialog
        history.setOnAction(e -> showHistoryDialog());

        // favorites button opens dialog -> now passes mainLayout so dialog can open module
        fav.setOnAction(e -> showFavoritesDialog(mainLayout));

        // theme toggle behaviour: change colors and persist
        boolean initialDark = isDarkModeSaved();
        theme.setSelected(initialDark);
        theme.setText(initialDark ? "Light Mode" : "Dark Mode");
        theme.setOnAction(ev -> {
            boolean nowDark = theme.isSelected();
            theme.setText(nowDark ? "Light Mode" : "Dark Mode");
            saveThemeChoice(nowDark);
            // Apply a few key changes to the top-level scene root and nav
            Scene s = primaryStage.getScene();
            if (s != null && s.getRoot() instanceof Region) {
                if (nowDark) ((Region) s.getRoot()).setStyle("-fx-background-color: #2b2b2b;");
                else ((Region) s.getRoot()).setStyle("-fx-background-color: #F5F5DC;");
            }
            // Optionally update nav and top styles if you keep references (they're created below)
        });

        right.getChildren().addAll(history, fav, theme);


        top.setLeft(left);
        top.setCenter(centerBox);
        top.setRight(right);

        mainLayout.setTop(top);

        // LEFT: navigation
        VBox nav = new VBox(10);
        nav.setPadding(new Insets(12));
        nav.setPrefWidth(260);
        List<String> modules = List.of(
                "My Learning", "Online Compound Search", "Periodic Table", "Molar Mass Calculator",
                "Percentage Composition", "Formula Converter", "Empirical Formula Calculator", "Equation Balancer",
                "Stoichiometry Calculator", "Unit Converter", "Reaction Properties Analyzer", "Reaction Database",
                "Organic Chemistry Helper", "Titration Simulator", "Chemistry Quiz", "Flashcards", "Reference Handbook"
        );

        ToggleGroup navGroup = new ToggleGroup();
        for (String m : modules) {
            ToggleButton tb = new ToggleButton(m);
            tb.setToggleGroup(navGroup);
            tb.setMaxWidth(Double.MAX_VALUE);
            tb.setPrefHeight(40);
            tb.setAlignment(Pos.CENTER_LEFT);
            tb.setStyle("-fx-background-color: #98FB98; -fx-text-fill: #1E2A38; -fx-font-weight:bold;");
            tb.setOnAction(e -> {
                mainLayout.setCenter(createModuleContent(m, mainLayout));
                addHistoryEntry("Opened module: " + m);
            });

            // Add right-click context menu for favorites
            ContextMenu cm = new ContextMenu();
            MenuItem favItem = new MenuItem("Add to Favorites");
            favItem.setOnAction(e -> {
                toggleFavorite(m);
                // optional feedback
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.INFORMATION, "Added to Favorites: " + m, ButtonType.OK);
                    a.setHeaderText(null);
                    a.showAndWait();
                });
            });
            cm.getItems().add(favItem);

            // Optionally show "Remove from Favorites" when already favorite
            MenuItem removeFavItem = new MenuItem("Remove from Favorites");
            removeFavItem.setOnAction(e -> {
                toggleFavorite(m);
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.INFORMATION, "Removed from Favorites: " + m, ButtonType.OK);
                    a.setHeaderText(null);
                    a.showAndWait();
                });
            });
            cm.getItems().add(removeFavItem);

            tb.setOnContextMenuRequested(e -> {
                // Before showing, adjust menu visibility depending on current status
                boolean isFav = isFavorite(m);
                favItem.setVisible(!isFav);
                removeFavItem.setVisible(isFav);
                cm.show(tb, e.getScreenX(), e.getScreenY());
            });

            nav.getChildren().add(tb);
            if (m.equals("My Learning")) Platform.runLater(() -> tb.setSelected(true));
        }


        mainLayout.setLeft(nav);
        mainLayout.setCenter(createModuleContent("My Learning", mainLayout));

        // create (or reuse) the listener and register it only once
        if (flashcardsListener == null) {
            flashcardsListener = ignoredPct -> Platform.runLater(() -> {
                try {
                    // Re-query model for reviewed/totalKnown so UI shows the reviewed fraction (immediate feedback)
                    FlashcardProgress fp = new FlashcardProgress(currentUser);
                    int reviewedNow = fp.getTotalReviewed();
                    int totalKnownNow = fp.getTotalKnownCards();
                    double pctNow = (totalKnownNow > 0) ? ((double) reviewedNow / totalKnownNow) : 0.0;

                    if (flashcardsProgressBar != null) flashcardsProgressBar.setProgress(pctNow);
                    if (flashcardsPercentLabel != null) {
                        flashcardsPercentLabel.setText(String.format("%d / %d (%.1f%%)", reviewedNow, totalKnownNow, pctNow * 100));
                    }
                } catch (Throwable t) {
                    System.err.println("Flashcard progress UI update failed: " + t.getMessage());
                }
            });
            FlashcardProgress.addProgressListener(flashcardsListener);
            System.out.println("[GuiApplication] registered flashcardsListener (currentUser=" + currentUser + ")");
        } else {
            System.out.println("[GuiApplication] flashcardsListener already registered; skipping duplicate registration");
        }


        return mainLayout;
    }

    // ---------------------------
    // Module content: My Learning special case includes progress and simple controls
    // ---------------------------
    private Node createModuleContent(String moduleName, BorderPane mainLayout) {
        // Check which module was selected
        switch (moduleName) {
            case "My Learning":
                // --- This is the new, live dashboard ---
                VBox dashboard = new VBox(25);
                dashboard.setPadding(new Insets(20));
                dashboard.setAlignment(Pos.TOP_CENTER);

                Label welcome = new Label(currentUser + "'s Learning Dashboard");
                welcome.setFont(Font.font("Arial", FontWeight.BOLD, 32));
                welcome.setTextFill(Color.web("#8A2BE2"));

                HBox progressContainer = createProgressBars();
                HBox studyGoalsContainer = createStudyGoalsBox();
                VBox affirmationsBox = createAffirmationBox();
                // Add favorites quick access box in the dashboard
                Node favQuick = createFavoritesQuickAccessBox(mainLayout);

                dashboard.getChildren().addAll(welcome, progressContainer, studyGoalsContainer, favQuick, affirmationsBox);
                VBox.setMargin(welcome, new Insets(10, 0, 20, 0));
                return dashboard;

            case "Online Compound Search":
                // --- NEW: Display the Online Search view ---
                return OnlineSearchView.getView();

            case "Periodic Table": // <-- NEW CASE
                return PeriodicTableView.getView();

            case "Molar Mass Calculator": // <-- NEW
                return MolarMassCalculatorView.getView();

            case "Percentage Composition": // <-- NEW
                return PercentageCompositionView.getView();

            case "Formula Converter": // <-- NEW
                return FormulaConverterView.getView();

            case "Empirical Formula Calculator": // <-- NEW
                return EmpiricalFormulaCalculatorView.getView();

            case "Equation Balancer":
                return EquationBalancerView.getView();

            case "Stoichiometry Calculator":
                return StoichiometryCalculatorView.getView();

            case "Unit Converter":
                return UnitConverterView.getView();

            case "Reaction Properties Analyzer": // <-- NEW
                return ReactionAnalyzerView.getView();

            case "Reaction Database": // <-- NEW
                return ReactionDatabaseView.getView();

            case "Organic Chemistry Helper": // <-- NEW
                return OrganicChemistryHelperView.getView();

            case "Titration Simulator": // <-- NEW
                return TitrationSimulatorView.getView();

            case "Chemistry Quiz":
                // full GUI quiz
                return QuizView.getView(currentUser, () -> {
                    Platform.runLater(() -> mainLayout.setCenter(createModuleContent("My Learning", mainLayout)));
                });

            case "Chemistry Quiz (Light)":
                return QuizViewLight.getView(currentUser, () -> {
                    Platform.runLater(() -> mainLayout.setCenter(createModuleContent("My Learning", mainLayout)));
                });

            case "Flashcards":
                return FlashcardsView.getView(currentUser, () -> {
                    // refresh the My Learning dashboard when flashcards session finishes
                    Platform.runLater(() -> mainLayout.setCenter(createModuleContent("My Learning", mainLayout)));
                });

            case "Reference Handbook":
                return HandbookView.getView();

            default:
                // This is the placeholder for all other modules
                Label placeholder = new Label("Opened module: " + moduleName + " GUI is under construction!");
                placeholder.setFont(Font.font(24));
                placeholder.setStyle("-fx-text-fill: #555;");
                return new StackPane(placeholder);
        }
    }

    private HBox createProgressBars() {
        HBox progressContainer = new HBox(30);
        progressContainer.setAlignment(Pos.CENTER);

        // Quiz progress (unchanged)
        QuizProgress quizProgress = new QuizProgress(currentUser);
        double quizAccuracy = quizProgress.getAccuracy();
        VBox quizBox = createProgressBox("Quiz Accuracy", quizAccuracy);

        // Flashcards progress: create and keep references so listeners can update them
        // Flashcards progress: create and keep references so listeners can update them
        FlashcardProgress flashProgress = new FlashcardProgress(currentUser);
        int reviewed = flashProgress.getTotalReviewed();        // how many cards we've reviewed (lifetime)
        int totalKnown = flashProgress.getTotalKnownCards();    // how many cards are known (in cardStates)
        double flashcardMastery = (totalKnown > 0) ? ((double) reviewed / totalKnown) : 0.0;

        // Build the UI for flashcards mastery
        VBox flashBox = new VBox(8);
        flashBox.setStyle("-fx-background-color: white; -fx-border-color: #D3D3D3; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15;");
        flashBox.setPrefWidth(350);
        flashBox.setAlignment(Pos.CENTER_LEFT);

        Label flashLabel = new Label("Flashcards Mastery");
        flashLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        // store the progress bar in the class field so it can be updated externally
        flashcardsProgressBar = new ProgressBar(flashcardMastery);
        flashcardsProgressBar.setMaxWidth(Double.MAX_VALUE);
        flashcardsProgressBar.setStyle("-fx-accent: #98FB98;");

        // show text like "3 / 23 (13.0%)"
        String percentText = String.format("%d / %d (%.1f%%)", reviewed, totalKnown, flashcardMastery * 100);
        flashcardsPercentLabel = new Label(percentText);
        flashcardsPercentLabel.setFont(Font.font("Arial", 14));

        flashBox.getChildren().addAll(flashLabel, flashcardsProgressBar, flashcardsPercentLabel);


        progressContainer.getChildren().addAll(quizBox, flashBox);
        return progressContainer;
    }


    private VBox createProgressBox(String title, double progressValue) {
        VBox box = new VBox(8);
        box.setStyle("-fx-background-color: white; -fx-border-color: #D3D3D3; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15;");
        box.setPrefWidth(350);
        box.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(title);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        ProgressBar progressBar = new ProgressBar(progressValue);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #98FB98;");

        Label percentLabel = new Label(String.format("%.1f%% Progress", progressValue * 100));
        percentLabel.setFont(Font.font("Arial", 14));

        box.getChildren().addAll(label, progressBar, percentLabel);
        return box;
    }



    /** --- NEW: Creates the UI for the Weekly Study Target feature --- */
    private HBox createStudyGoalsBox() {
        HBox container = new HBox(30);
        container.setAlignment(Pos.CENTER);

        // --- Study Time Tracker ---
        VBox timeBox = new VBox(10);
        timeBox.setStyle("-fx-background-color: white; -fx-border-color: #D3D3D3; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15;");
        Label timeTitle = new Label("Total Study Time");
        timeTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        QuizProgress qp = new QuizProgress(currentUser);
        FlashcardProgress fp = new FlashcardProgress(currentUser);
        long totalMillis = qp.getTotalStudyTimeMillis() + fp.getTotalStudyTimeMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(totalMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60;
        Label timeLabel = new Label(String.format("%d hours, %d minutes", hours, minutes));
        timeLabel.setFont(Font.font("Arial", 20));
        timeBox.getChildren().addAll(timeTitle, timeLabel);

        // --- Weekly Goal Setter ---
        VBox goalBox = new VBox(10);
        goalBox.setStyle("-fx-background-color: white; -fx-border-color: #D3D3D3; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15;");
        Label goalTitle = new Label("Weekly Study Target");
        goalTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        TextField goalInput = new TextField();
        goalInput.setPromptText("e.g., 120 (minutes)");
        Button setGoalButton = new Button("Set Goal");
        Label goalStatus = new Label("Current Goal: 120 mins. | Progress: 30 / 120 mins.");
        goalBox.getChildren().addAll(goalTitle, goalInput, setGoalButton, goalStatus);

        container.getChildren().addAll(timeBox, goalBox);
        return container;
    }

    /** --- NEW: Creates the UI for the My Affirmations feature --- */
    private VBox createAffirmationBox() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-padding: 20px;");
        Label title = new Label("My Affirmations");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#8A2BE2"));

        List<String> affirmations = List.of(
                "Every reaction you study sparks new understanding.",
                "You’re not just learning chemistry—you’re uncovering the secrets of the universe.",
                "Every equation you balance sharpens your mind.",
                "Your curiosity is the catalyst for discovery.",
                "Even the smallest step today brings you closer to mastery.",
                "Like atoms forming bonds, every bit of knowledge connects to something bigger."
        );
        Label affirmationText = new Label("\"" + affirmations.get(new Random().nextInt(affirmations.size())) + "\"");
        affirmationText.setFont(Font.font("Arial", FontPosture.ITALIC, 18));
        affirmationText.setWrapText(true);
        affirmationText.setMaxWidth(600);

        box.getChildren().addAll(title, affirmationText);
        return box;
    }

    /**
     * Small dashboard box showing favorite modules as buttons for one-click open.
     */
    private Node createFavoritesQuickAccessBox(BorderPane mainLayout) {
        VBox box = new VBox(8);
        box.setStyle("-fx-background-color: white; -fx-border-color: #D3D3D3; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
        box.setPrefWidth(420);
        Label title = new Label("Favorite Modules");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        HBox listBox = new HBox();
        listBox.setSpacing(8);

        loadHistoryAndFavorites();
        if (favoriteSet.isEmpty()) {
            Label none = new Label("No favorites yet. Right-click a module in the nav to add.");
            none.setWrapText(true);
            box.getChildren().addAll(title, none);
            return box;
        }

        FlowPane fp = new FlowPane();
        fp.setHgap(8);
        fp.setVgap(8);
        for (String f : favoriteSet) {
            Button b = new Button(f);
            b.setOnAction(e -> {
                mainLayout.setCenter(createModuleContent(f, mainLayout));
                addHistoryEntry("Opened favorite: " + f);
            });
            b.setStyle("-fx-background-color:#E0F7EA; -fx-border-color:#C6E9CE; -fx-padding:6 10;");
            fp.getChildren().add(b);
        }

        box.getChildren().addAll(title, fp);
        return box;
    }


    // ---------------------------
    // Helpers: button hover, label glow, image hover, avatar creation
    // ---------------------------
    private static void addButtonHoverEffects(Button btn) {
        String base = "-fx-background-color: linear-gradient(to bottom, #b2fcb2, #70db70); -fx-text-fill: #2b2b2b; -fx-font-size: 18px; -fx-padding: 10px 30px; -fx-background-radius: 10;";
        String hover = "-fx-background-color: linear-gradient(to bottom, #70db70, #4caf50); -fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-padding: 10px 30px; -fx-background-radius: 10;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> {
            btn.setStyle(hover);
            ScaleTransition st = new ScaleTransition(Duration.millis(140), btn);
            st.setToX(1.04); st.setToY(1.04); st.play();
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle(base);
            ScaleTransition st = new ScaleTransition(Duration.millis(140), btn);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });
    }

    private static void addGlowOnHover(Label label, Color fromColor, Color toColor) {
        DropShadow ds = new DropShadow(8, fromColor);
        ds.setSpread(0.2);
        label.setEffect(ds);

        label.setOnMouseEntered(e -> {
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(ds.colorProperty(), fromColor),
                            new KeyValue(ds.radiusProperty(), 8)),
                    new KeyFrame(Duration.millis(450),
                            new KeyValue(ds.colorProperty(), toColor),
                            new KeyValue(ds.radiusProperty(), 18))
            );
            t.play();
        });

        label.setOnMouseExited(e -> {
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(ds.colorProperty(), toColor),
                            new KeyValue(ds.radiusProperty(), 18)),
                    new KeyFrame(Duration.millis(400),
                            new KeyValue(ds.colorProperty(), fromColor),
                            new KeyValue(ds.radiusProperty(), 8))
            );
            t.play();
        });
    }

    private static void addImageHoverEffects(ImageView iv) {
        iv.setOnMouseEntered(e -> {
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(iv.scaleXProperty(), iv.getScaleX()),
                            new KeyValue(iv.scaleYProperty(), iv.getScaleY()),
                            new KeyValue(iv.rotateProperty(), iv.getRotate())),
                    new KeyFrame(Duration.millis(420),
                            new KeyValue(iv.scaleXProperty(), 1.06, Interpolator.EASE_OUT),
                            new KeyValue(iv.scaleYProperty(), 1.06, Interpolator.EASE_OUT),
                            new KeyValue(iv.rotateProperty(), 1.8, Interpolator.EASE_OUT))
            );
            t.play();
            DropShadow ds = new DropShadow(20, Color.color(0.02, 0.35, 0.3, 0.28));
            iv.setEffect(ds);
        });

        iv.setOnMouseExited(e -> {
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(iv.scaleXProperty(), iv.getScaleX()),
                            new KeyValue(iv.scaleYProperty(), iv.getScaleY()),
                            new KeyValue(iv.rotateProperty(), iv.getRotate())),
                    new KeyFrame(Duration.millis(420),
                            new KeyValue(iv.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                            new KeyValue(iv.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                            new KeyValue(iv.rotateProperty(), 0.0, Interpolator.EASE_BOTH))
            );
            t.play();
            PauseTransition pt = new PauseTransition(Duration.millis(430));
            pt.setOnFinished(ev -> iv.setEffect(new DropShadow(12, Color.color(0,0,0,0.18))));
            pt.play();
        });
    }

    private Node createAvatarNode(String username, double radius) {
        String initials = "U";
        if (username != null && !username.isBlank()) {
            String[] parts = username.trim().split("\\s+");
            if (parts.length >= 2) initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
            else initials = ("" + parts[0].charAt(0)).toUpperCase();
        }
        Circle circle = new Circle(radius);
        int hash = Math.abs(Objects.hashCode(username));
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);
        Color bg = Color.rgb(100 + (r % 100), 80 + (g % 100), 80 + (b % 100));
        circle.setFill(bg);
        Label lab = new Label(initials);
        lab.setTextFill(Color.WHITE);
        lab.setFont(Font.font("Arial", FontWeight.BOLD, radius * 0.7));
        StackPane stack = new StackPane(circle, lab);
        return stack;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------
    // Simple file-backed user store (username:sha256(password))
    // ---------------------------
    static class UserStore {
        private final Path file;
        private final Map<String, String> users = new HashMap<>();

        UserStore() {
            String home = System.getProperty("user.home");
            file = Paths.get(home, ".molmate_users.txt");
            load();
        }

        private void load() {
            users.clear();
            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int idx = line.indexOf(':');
                        if (idx > 0) {
                            String u = line.substring(0, idx);
                            String h = line.substring(idx + 1);
                            users.put(u, h);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Failed to read user store: " + e.getMessage());
                }
            } else {
                try { Files.createFile(file); } catch (IOException ignored) {}
            }
        }

        private void save() {
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Map.Entry<String, String> e : users.entrySet()) {
                    bw.write(e.getKey() + ":" + e.getValue());
                    bw.newLine();
                }
            } catch (IOException ex) {
                System.err.println("Failed to write user store: " + ex.getMessage());
            }
        }

        public boolean validate(String username, String password) {
            if (username == null || password == null) return false;
            String stored = users.get(username);
            if (stored == null) return false;
            String hash = sha256(password);
            return stored.equals(hash);
        }

        public boolean register(String username, String password) {
            if (username == null || username.isBlank() || password == null || password.isEmpty()) return false;
            if (users.containsKey(username)) return false;
            users.put(username, sha256(password));
            save();
            return true;
        }

        private static String sha256(String s) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte bt : b) sb.append(String.format("%02x", bt));
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ---------------------------
    // Remember store: encrypted username in a file
    // ---------------------------
    static class RememberStore {
        private final Path file;
        // appSalt: used to derive key together with OS username so the key is machine-specific
        private static final String APP_SALT = "MolMateLocalSalt_v1";
        private static final String CIPHER_ALG = "AES/GCM/NoPadding";
        private static final int GCM_TAG_BITS = 128;
        private static final int IV_SIZE = 12; // recommended for GCM

        RememberStore() {
            String home = System.getProperty("user.home");
            file = Paths.get(home, ".molmate_remember");
        }

        /**
         * Save encrypted username to file (base64 of IV + ciphertext)
         */
        public void saveRememberedUsername(String username) {
            try {
                SecretKey key = deriveKey();
                // create random IV
                byte[] iv = new byte[IV_SIZE];
                SecureRandom sr = new SecureRandom();
                sr.nextBytes(iv);

                Cipher cipher = Cipher.getInstance(CIPHER_ALG);
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
                cipher.init(Cipher.ENCRYPT_MODE, key, spec);
                byte[] ct = cipher.doFinal(username.getBytes(StandardCharsets.UTF_8));

                // store iv + ct as base64 text
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(iv);
                baos.write(ct);
                byte[] combined = baos.toByteArray();
                String b64 = Base64.getEncoder().encodeToString(combined);

                Files.write(file, Collections.singletonList(b64), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception e) {
                System.err.println("Failed to save remembered username: " + e.getMessage());
            }
        }

        /**
         * Load and decrypt remembered username; returns null if none or invalid
         */
        public String loadRememberedUsername() {
            try {
                if (!Files.exists(file)) return null;
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (lines.isEmpty()) return null;
                String b64 = lines.get(0).trim();
                if (b64.isEmpty()) return null;
                byte[] combined = Base64.getDecoder().decode(b64);
                if (combined.length < IV_SIZE + 1) return null;
                byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE);
                byte[] ct = Arrays.copyOfRange(combined, IV_SIZE, combined.length);

                SecretKey key = deriveKey();
                Cipher cipher = Cipher.getInstance(CIPHER_ALG);
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
                cipher.init(Cipher.DECRYPT_MODE, key, spec);
                byte[] pt = cipher.doFinal(ct);
                return new String(pt, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // if decryption fails, clear file to avoid repeated errors
                try { Files.deleteIfExists(file); } catch (IOException ignored) {}
                System.err.println("Failed to read remembered username (cleared): " + e.getMessage());
                return null;
            }
        }

        public void clearRememberedUsername() {
            try { Files.deleteIfExists(file); } catch (Exception ignored) {}
        }

        private SecretKey deriveKey() throws Exception {
            // Derive a 256-bit AES key via PBKDF2 from the OS username + APP_SALT
            String osUser = System.getProperty("user.name", "unknown");
            String passphrase = osUser + "|" + APP_SALT;
            byte[] salt = ("molmate-remember-salt-v1").getBytes(StandardCharsets.UTF_8);
            int iterations = 65536;
            int keyLen = 256;
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyLen);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = skf.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        }
    }


    // ---------- Theme helpers ----------
    private boolean isDarkModeSaved() {
        try {
            if (Files.exists(themeFile)) {
                String val = Files.readString(themeFile, StandardCharsets.UTF_8).trim();
                return "dark".equalsIgnoreCase(val);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void saveThemeChoice(boolean dark) {
        try { Files.writeString(themeFile, dark ? "dark" : "light", StandardCharsets.UTF_8); } catch (Exception ignored) {}
    }

    private void applyThemeToNode(Node node, boolean dark) {
        // lightweight approach: apply background colors and text color for key layout nodes.
        // We'll target BorderPane root and navigation if present in createMainAppLayout handler.
        // This helper is here in case you want to apply to other nodes later.
        if (node instanceof Region) {
            if (dark) ((Region) node).setStyle("-fx-background-color: #222; -fx-text-fill: #EEE;");
            else ((Region) node).setStyle("-fx-background-color: #F5F5DC; -fx-text-fill: #333;");
        }
    }

    // ---------- History & Favorites persistence ----------
    private void loadHistoryAndFavorites() {
        try {
            if (Files.exists(historyFile)) {
                historyEntries = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
            } else {
                historyEntries = new ArrayList<>();
            }
        } catch (Exception e) {
            historyEntries = new ArrayList<>();
        }

        // favorites: load, sanitize older entries (strip prefixes), and normalize
        try {
            if (Files.exists(favoritesFile)) {
                List<String> raw = Files.readAllLines(favoritesFile, StandardCharsets.UTF_8);
                Set<String> cleaned = new LinkedHashSet<>();
                for (String r : raw) {
                    if (r == null) continue;
                    String s = r.trim();
                    if (s.isEmpty()) continue;

                    // Migration: strip history-like prefixes that were accidentally saved
                    String[] prefixes = new String[] {
                            "Opened module: ", "Opened favorite: ", "Favorite: "
                    };
                    for (String p : prefixes) {
                        if (s.startsWith(p)) {
                            s = s.substring(p.length()).trim();
                            break;
                        }
                    }

                    // Keep exact module name case (but trim)
                    if (!s.isEmpty()) cleaned.add(s);
                }
                favoriteSet = cleaned;
                // persist back cleaned favorites (this migrates old file automatically)
                try { Files.write(favoritesFile, new ArrayList<>(favoriteSet), StandardCharsets.UTF_8); } catch (Exception ignored) {}
            } else {
                favoriteSet = new LinkedHashSet<>();
            }
        } catch (Exception e) {
            favoriteSet = new LinkedHashSet<>();
        }
    }

    private void addHistoryEntry(String entry) {
        if (entry == null || entry.isBlank()) return;
        // keep most recent at top, avoid duplicates
        historyEntries.remove(entry);
        historyEntries.add(0, entry);
        // limit size
        if (historyEntries.size() > 200) historyEntries = historyEntries.subList(0, 200);
        try { Files.write(historyFile, historyEntries, StandardCharsets.UTF_8); } catch (Exception ignored) {}
    }

    private void toggleFavorite(String entry) {
        if (entry == null) return;
        String module = entry.trim();
        if (module.isEmpty()) return;

        // Normalize: store only the module name (no prefixes)
        if (favoriteSet.contains(module)) {
            favoriteSet.remove(module);
        } else {
            favoriteSet.add(module);
        }

        // persist favorites file (overwrite)
        try {
            Files.write(favoritesFile, new ArrayList<>(favoriteSet), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    private boolean isFavorite(String entry) {
        return entry != null && favoriteSet.contains(entry);
    }

    private void showHistoryDialog() {
        loadHistoryAndFavorites(); // refresh
        Dialog<Void> d = new Dialog<>();
        d.setTitle("History");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        if (historyEntries.isEmpty()) {
            box.getChildren().add(new Label("No history yet."));
        } else {
            for (String h : historyEntries) {
                HBox row = new HBox(8);
                Label lbl = new Label(h);
                lbl.setMaxWidth(520);
                lbl.setWrapText(true);
                Button star = new Button(isFavorite(h) ? "★" : "☆");
                star.setOnAction(ev -> { toggleFavorite(h); star.setText(isFavorite(h) ? "★" : "☆"); });
                Button open = new Button("Open");
                open.setOnAction(ev -> {
                    // Try to open the module that corresponds; this is app-specific.
                    // For example, if the history entry is a compound name, open OnlineSearchView and populate.
                    // For now we just add to history again and close.
                    addHistoryEntry(h);
                    d.close();
                });
                row.getChildren().addAll(star, lbl, open);
                box.getChildren().add(row);
            }
        }
        d.getDialogPane().setContent(new ScrollPane(box));
        d.showAndWait();
    }

    // show favorites dialog that can open a favorite module in the provided main layout
    private void showFavoritesDialog(BorderPane mainLayout) {
        loadHistoryAndFavorites();
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Favorites");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        if (favoriteSet.isEmpty()) {
            Label none = new Label("No favorites yet.\nRight-click a module in the left nav and choose 'Add to Favorites'.");
            none.setWrapText(true);
            box.getChildren().add(none);
        } else {
            for (String f : favoriteSet) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);

                Label lbl = new Label(f);
                lbl.setWrapText(true);
                lbl.setMaxWidth(420);

                Button openBtn = new Button("Open");
                openBtn.setOnAction(ev -> {
                    try {
                        // Open the module in the provided main layout
                        Node content = createModuleContent(f, mainLayout);
                        mainLayout.setCenter(content);
                        addHistoryEntry("Opened favorite: " + f);
                    } catch (Exception ex) {
                        // fallback: add history and close dialog
                        addHistoryEntry("Tried to open favorite: " + f);
                    }
                    d.close();
                });

                Button unfav = new Button("Unfavorite");
                unfav.setOnAction(ev -> {
                    toggleFavorite(f);
                    d.close();
                    // reopen to refresh list
                    showFavoritesDialog(mainLayout);
                });

                row.getChildren().addAll(lbl, openBtn, unfav);
                box.getChildren().add(row);
            }
        }
        d.getDialogPane().setContent(new ScrollPane(box));
        d.showAndWait();
    }




    public static void main(String[] args) {
        // keep loaders you need
        MolarMassCalculator.loadElements();
        ReactionDatabase.loadDatabase();
        PeriodicTable.loadElements();
        Handbook.loadData();
        launch(args);
    }
}
