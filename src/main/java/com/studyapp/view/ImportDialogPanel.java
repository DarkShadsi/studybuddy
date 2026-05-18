package com.studyapp.view;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.studyapp.controller.CustomException;
import com.studyapp.controller.MainController;
import com.studyapp.model.Deck;
import com.studyapp.service.CardJson;
import com.studyapp.service.ImportPreview;
import com.studyapp.util.UiScale;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Full-screen modal dialog for importing flashcard questions from a JSON or CSV file.
 *
 * <p>Layout overview:
 * <pre>
 * ┌───────────────────────────────────────────────────────────────┐
 * │                    IMPORT QUESTIONS (header)                  │
 * ├──────────────────────────┬────────────────────────────────────┤
 * │ ADD TO DECK:             │                  [SELECT ALL □]    │
 * │  ○ Add to Existing Deck  │  ╔══════════════════════════════╗  │
 * │    [deck dropdown]       │  ║ placeholder / preview table  ║  │
 * │  ○ Add to New Deck       │  ╚══════════════════════════════╝  │
 * │    [name field]          │                                    │
 * │    [desc field]          │                                    │
 * │  [IMPORT FROM CSV]       │                                    │
 * │  [IMPORT FROM JSON]      │                                    │
 * │  [IMPORT TO SYSTEM]      │                                    │
 * │  [CANCEL]                │                                    │
 * └──────────────────────────┴────────────────────────────────────┘
 * </pre>
 */
public class ImportDialogPanel {

    /* ── colour constants (match the app's existing blue palette) ─── */
    private static final String HEADER_DARK  = "#3a5a8a";
    private static final String PRIMARY_BLUE = "#2a548f";
    private static final String PANEL_BG     = "#e8e8f5";
    private static final String PANEL_BORDER = "#6a7acd";
    private static final String PAGE_BG      = "#f0f2f8";

    // ────────────────────────────────────────────────────────────────
    // Inner data model
    // ────────────────────────────────────────────────────────────────

    /**
     * Represents one flashcard candidate in the preview table.
     *
     * <p>Each field is a JavaFX property so table cells can bind to it directly.
     * A {@code null} difficulty means the user has not yet assigned one
     * (displayed as "--Select Difficulty--").
     */
    public static class CardRowData {

        private final BooleanProperty selected   = new SimpleBooleanProperty(false);
        private final StringProperty  question   = new SimpleStringProperty("");
        private final StringProperty  answer     = new SimpleStringProperty("");
        private final DoubleProperty  previewHeight = new SimpleDoubleProperty(UiScale.size(64));
        /**
         * {@code null}  → "--Select Difficulty--" (user must choose before importing).
         * {@code "Easy"} / {@code "Medium"} / {@code "Hard"}  → pre-filled from file.
         */
        private final StringProperty  difficulty = new SimpleStringProperty(null);

        /**
         * @param q    raw question string from the parsed file (trimmed internally)
         * @param a    raw answer string from the parsed file (trimmed internally)
         * @param diff raw difficulty from the file; null or unrecognised → stored as null
         */
        public CardRowData(String q, String a, String diff) {
            question.set(q    != null ? q.trim() : "");
            answer.set(a      != null ? a.trim() : "");
            difficulty.set(validateDifficulty(diff));
        }

        /**
         * Returns "Easy", "Medium", or "Hard" when recognised; {@code null} otherwise.
         * Unlike the import-service normaliser, unrecognised values are NOT defaulted
         * to "Medium" — they are left as null so the UI can prompt the user.
         */
        private static String validateDifficulty(String raw) {
            if (raw == null || raw.isBlank()) return null;
            return switch (raw.trim().toLowerCase()) {
                case "easy"   -> "Easy";
                case "medium" -> "Medium";
                case "hard"   -> "Hard";
                default       -> null;
            };
        }

        public BooleanProperty selectedProperty()   { return selected; }
        public StringProperty  questionProperty()   { return question; }
        public StringProperty  answerProperty()     { return answer; }
        public StringProperty  difficultyProperty() { return difficulty; }
        public DoubleProperty  previewHeightProperty() { return previewHeight; }

        public boolean isSelected()    { return selected.get(); }
        public String  getQuestion()   { return question.get(); }
        public String  getAnswer()     { return answer.get(); }
        /** Returns {@code null} when no difficulty has been assigned. */
        public String  getDifficulty() { return difficulty.get(); }
    }

    // ────────────────────────────────────────────────────────────────
    // Public entry-point
    // ────────────────────────────────────────────────────────────────

    /**
     * Opens the Import Questions dialog as a maximised, application-modal stage.
     * All import logic, preview, and post-import navigation are handled internally.
     *
     * @param mainLayout root {@link BorderPane} of the main window
     * @param mc         controller for all business/data operations
     */
    public static void show(BorderPane mainLayout, MainController mc) {

        Scene appScene = mainLayout.getScene();
        Parent previousRoot = appScene.getRoot();
        Window ownerWindow = appScene.getWindow();

        StackPane modalRoot = new StackPane();
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(15, 23, 42, 0.28);");
        overlay.setPickOnBounds(true);
        Runnable closeOverlay = () -> {
            modalRoot.getChildren().remove(previousRoot);
            appScene.setRoot(previousRoot);
        };

        /* Shared list backing the preview TableView. */
        ObservableList<CardRowData> cardRows = FXCollections.observableArrayList();

        // ── root ──────────────────────────────────────────────────────
        VBox root = new VBox(0);
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        root.setStyle(
            "-fx-background-color: " + PAGE_BG + "; " +
            "-fx-background-radius: 12; " +
            "-fx-border-color: " + PRIMARY_BLUE + "; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 12;"
        );
        StackPane.setMargin(root, UiScale.insets(24));

        // ── title header bar ──────────────────────────────────────────
        Label titleLbl = new Label("IMPORT CARDS");
        titleLbl.setFont(UiScale.titleFont(52));
        titleLbl.setTextFill(Color.WHITE);
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        titleLbl.setAlignment(Pos.CENTER);
        titleLbl.setPadding(UiScale.insets(14, 20, 14, 20));
        titleLbl.setStyle("-fx-background-color: " + HEADER_DARK + "; -fx-background-radius: 10 10 0 0;");

        // ── body: left panel | right panel ────────────────────────────
        HBox body = new HBox(UiScale.size(20));
        body.setPadding(UiScale.insets(20, 24, 20, 24));
        body.setStyle("-fx-background-color: " + PAGE_BG + ";");
        VBox.setVgrow(body, Priority.ALWAYS);

        // ════════════════════════════════════════
        // LEFT PANEL
        // ════════════════════════════════════════
        VBox left = new VBox(UiScale.size(14));
        left.setPrefWidth(UiScale.size(340));
        left.setMinWidth(UiScale.size(320));
        left.setMaxWidth(UiScale.size(380));
        left.setPadding(UiScale.insets(20, 20, 24, 20));
        left.setStyle(
            "-fx-background-color: " + PANEL_BG + "; " +
            "-fx-border-color: " + PANEL_BORDER + "; " +
            "-fx-border-width: 1.5; " +
            "-fx-border-radius: 10; -fx-background-radius: 10;"
        );

        // "ADD TO DECK:" heading
        Label addToDeckLbl = new Label("ADD TO DECK:");
        addToDeckLbl.setFont(UiScale.buttonFont(20));
        addToDeckLbl.setStyle("-fx-font-weight: bold;");
        addToDeckLbl.setTextFill(Color.web(PRIMARY_BLUE));

        ToggleGroup deckToggle = new ToggleGroup();

        // ── "Add to Existing Deck" branch ─────────────────────────────
        RadioButton existingRadio = new RadioButton("Add to Existing Deck:");
        existingRadio.setToggleGroup(deckToggle);
        existingRadio.setFont(UiScale.bodyFont(18));
        existingRadio.setTextFill(Color.web(PRIMARY_BLUE));

        ComboBox<Deck> existingCombo = new ComboBox<>();
        existingCombo.getItems().addAll(mc.allDecks());
        existingCombo.setMaxWidth(Double.MAX_VALUE);
        existingCombo.setPromptText("-- Select a Deck --");
        existingCombo.setStyle(comboStyle());
        existingCombo.setCellFactory(lv -> deckCell());
        existingCombo.setButtonCell(deckCell());
        existingCombo.setVisible(false);
        existingCombo.setManaged(false);

        // ── "Add to New Deck" branch ───────────────────────────────────
        RadioButton newDeckRadio = new RadioButton("Add to New Deck:");
        newDeckRadio.setToggleGroup(deckToggle);
        newDeckRadio.setFont(UiScale.bodyFont(18));
        newDeckRadio.setTextFill(Color.web(PRIMARY_BLUE));

        TextField nameField = new TextField();
        nameField.setPromptText("Deck Name");
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameField.setStyle(inputStyle());
        nameField.setVisible(false);
        nameField.setManaged(false);

        TextField descField = new TextField();
        descField.setPromptText("Deck Description");
        descField.setMaxWidth(Double.MAX_VALUE);
        descField.setStyle(inputStyle());
        descField.setVisible(false);
        descField.setManaged(false);

        // Toggle visibility of the deck sub-controls when a radio is selected
        existingRadio.selectedProperty().addListener((obs, old, sel) -> {
            existingCombo.setVisible(sel);
            existingCombo.setManaged(sel);
        });
        newDeckRadio.selectedProperty().addListener((obs, old, sel) -> {
            nameField.setVisible(sel);
            nameField.setManaged(sel);
            descField.setVisible(sel);
            descField.setManaged(sel);
        });

        // Default: use "Existing" when decks are available, else "New"
        if (mc.allDecks().isEmpty()) {
            newDeckRadio.setSelected(true);
        } else {
            existingRadio.setSelected(true);
        }

        // ── file-import buttons ────────────────────────────────────────
        Button csvBtn  = fileImportButton("IMPORT FROM CSV");
        Button jsonBtn = fileImportButton("IMPORT FROM JSON");

        // ── main action buttons ────────────────────────────────────────
        Button importBtn = actionButton("IMPORT TO SYSTEM", "#8bc34a", "#00bf63");
        importBtn.setDisable(true);   // enabled only when ≥1 row is checked

        Button cancelBtn = actionButton("CANCEL", "#e57373", "#d32f2f");

        // ── cancel handler ─────────────────────────────────────────────
        cancelBtn.setOnAction(e -> {
            if (confirmCancel(ownerWindow)) closeOverlay.run();
        });

        // ── IMPORT TO SYSTEM handler ───────────────────────────────────
        importBtn.setOnAction(e -> {

            List<CardRowData> selected = cardRows.stream()
                .filter(CardRowData::isSelected)
                .toList();

            // Validate: no selected row may have a blank question or answer
            long blankCards = selected.stream()
                .filter(r -> r.getQuestion() == null || r.getQuestion().isBlank()
                          || r.getAnswer()   == null || r.getAnswer().isBlank())
                .count();

            if (blankCards > 0) {
                customWarning(ownerWindow,
                    "Invalid\nCards!",
                    blankCards + " selected card(s) have a blank question or answer.\n"
                    + "Fill in or deselect them before importing.");
                return;
            }

            // Validate: every selected row must have a difficulty assigned
            long missingDiff = selected.stream()
                .filter(r -> r.getDifficulty() == null || r.getDifficulty().isBlank())
                .count();

            if (missingDiff > 0) {
                customWarning(ownerWindow,
                    "Missing\nDifficulty!",
                    missingDiff + " selected card(s) have no difficulty set.\n"
                    + "Assign Easy, Medium, or Hard on each affected row before importing.");
                return;   // stay on the dialog so the user can fix it
            }

            // Create cards in memory (no DB write yet)
            try {
                if (existingRadio.isSelected()) {
                    Deck deck = existingCombo.getValue();
                    if (deck == null) {
                        MainFrame.showErrorDialog("Please select an existing deck.");
                        return;
                    }
                    mc.importCardsToExistingDeck(deck.getDeckID(), toCardJsonList(selected));
                } else {
                    String deckName = nameField.getText().trim();
                    if (deckName.isEmpty()) {
                        MainFrame.showErrorDialog("Please enter a deck name.");
                        return;
                    }
                    mc.importCardsToNewDeck(deckName, descField.getText().trim(), toCardJsonList(selected));
                }
            } catch (CustomException ex) {
                MainFrame.showErrorDialog("Import failed: " + ex.getMessage());
                return;
            }

            // Persist and navigate back to the deck dashboard
            closeOverlay.run();
            MainFrame.runSaveTask(
                ownerWindow,
                mc,
                "Saving imported cards...",
                () -> {
                    MainFrame.showSuccessDialog(selected.size() + " card(s) imported successfully!");
                    mainLayout.setCenter(MyDeckPanel.create(mainLayout, mc));
                },
                errorMsg -> {
                    MainFrame.showErrorDialog("Autosave failed: " + errorMsg);
                    mainLayout.setCenter(MyDeckPanel.create(mainLayout, mc));
                }
            );
        });

        left.getChildren().addAll(
            addToDeckLbl,
            existingRadio, existingCombo,
            newDeckRadio, nameField, descField,
            csvBtn, jsonBtn
        );

        // ════════════════════════════════════════
        // RIGHT PANEL: preview area
        // ════════════════════════════════════════
        VBox right = new VBox(UiScale.size(8));
        HBox.setHgrow(right, Priority.ALWAYS);

        // "SELECT ALL" row
        CheckBox selectAllCb = new CheckBox("SELECT ALL");
        selectAllCb.setFont(UiScale.bodyFont(18));
        selectAllCb.setTextFill(Color.web(PRIMARY_BLUE));
        Button editPreviewBtn = new Button("EDIT PREVIEW");
        editPreviewBtn.setFont(UiScale.buttonFont(16));
        editPreviewBtn.setStyle("-fx-background-color: white; -fx-text-fill: " + PRIMARY_BLUE + "; "
                + "-fx-border-color: " + PANEL_BORDER + "; -fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-padding: " + UiScale.size(6) + " " + UiScale.size(12) + "; -fx-cursor: hand;");

        BooleanProperty editPreviewMode = new SimpleBooleanProperty(false);
        HBox selectAllRow = new HBox(UiScale.size(16), selectAllCb, editPreviewBtn);
        selectAllRow.setAlignment(Pos.CENTER_LEFT);
        selectAllRow.setPadding(UiScale.insets(0, 0, 4, 4));

        Label sourceDeckInfo = new Label();
        sourceDeckInfo.setFont(UiScale.bodyFont(18));
        sourceDeckInfo.setTextFill(Color.web(PRIMARY_BLUE));
        sourceDeckInfo.setWrapText(true);
        sourceDeckInfo.setVisible(false);
        sourceDeckInfo.setManaged(false);
        sourceDeckInfo.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: " + PANEL_BORDER + "; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; -fx-background-radius: 8; " +
            "-fx-padding: " + UiScale.size(8) + " " + UiScale.size(12) + ";"
        );

        csvBtn.setOnAction(e -> {
            File f = chooseFile(ownerWindow, "Import from CSV", "CSV Files", "*.csv");
            if (f != null) {
                loadPreview(f, "CSV", cardRows, importBtn, selectAllCb, nameField, descField, sourceDeckInfo, mc);
            }
        });

        jsonBtn.setOnAction(e -> {
            File f = chooseFile(ownerWindow, "Import from JSON", "JSON Files", "*.json");
            if (f != null) {
                loadPreview(f, "JSON", cardRows, importBtn, selectAllCb, nameField, descField, sourceDeckInfo, mc);
            }
        });

        // Placeholder (shown before any file is loaded)
        StackPane placeholder = new StackPane();
        placeholder.setStyle(previewBoxStyle());
        VBox.setVgrow(placeholder, Priority.ALWAYS);
        Label placeholderLbl = new Label("-QUESTIONS WILL BE PREVIEWED HERE-");
        placeholderLbl.setFont(UiScale.bodyFont(20));
        placeholderLbl.setTextFill(Color.web("#8888b8"));
        placeholder.getChildren().add(placeholderLbl);

        // Table (hidden until cards are loaded)
        TableView<CardRowData> table = buildTable(cardRows, selectAllCb, importBtn, editPreviewMode);
        table.setVisible(false);
        table.setManaged(false);
        VBox.setVgrow(table, Priority.ALWAYS);

        editPreviewBtn.setOnAction(e -> {
            boolean editing = !editPreviewMode.get();
            editPreviewMode.set(editing);
            editPreviewBtn.setText(editing ? "LOCK PREVIEW" : "EDIT PREVIEW");
            table.refresh();
        });

        // Swap placeholder ↔ table when cardRows changes
        cardRows.addListener((ListChangeListener<CardRowData>) c -> {
            boolean hasRows = !cardRows.isEmpty();
            placeholder.setVisible(!hasRows);
            placeholder.setManaged(!hasRows);
            table.setVisible(hasRows);
            table.setManaged(hasRows);
            if (hasRows) {
                Platform.runLater(() -> {
                    table.refresh();
                    table.requestLayout();
                });
            }
        });

        HBox actionRow = new HBox(UiScale.size(16), importBtn, cancelBtn);
        HBox.setHgrow(importBtn, Priority.ALWAYS);
        HBox.setHgrow(cancelBtn, Priority.ALWAYS);
        actionRow.setPadding(UiScale.insets(12, 0, 0, 0));

        right.getChildren().addAll(selectAllRow, sourceDeckInfo, placeholder, table, actionRow);

        body.getChildren().addAll(left, right);
        root.getChildren().addAll(titleLbl, body);

        overlay.getChildren().add(root);
        appScene.setRoot(modalRoot);
        modalRoot.getChildren().addAll(previousRoot, overlay);
    }

    // ────────────────────────────────────────────────────────────────
    // Table builder
    // ────────────────────────────────────────────────────────────────

    /**
     * Builds the preview {@link TableView} with four columns:
     * {@code checkbox | QUESTION | ANSWER | DIFFICULTY}.
     *
     * <p>Rows are read-only by default. The EDIT PREVIEW toggle swaps question,
     * answer, and difficulty cells into editable controls.
     *
     * @param rows        observable list that backs the table
     * @param selectAllCb the "SELECT ALL" checkbox for bulk selection
     * @param importBtn   button whose disabled state tracks whether any row is selected
     */
    private static TableView<CardRowData> buildTable(
            ObservableList<CardRowData> rows,
            CheckBox selectAllCb,
            Button importBtn,
            BooleanProperty editPreviewMode) {

        TableView<CardRowData> table = new TableView<>(rows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(-1);
        table.setFocusTraversable(false);
        table.setOnMousePressed(e -> Platform.runLater(() -> table.getSelectionModel().clearSelection()));
        table.setStyle(
            "-fx-background-color: " + PANEL_BG + "; " +
            "-fx-border-color: " + PANEL_BORDER + "; " +
            "-fx-border-width: 1.5; " +
            "-fx-border-radius: 10; -fx-background-radius: 10; " +
            "-fx-control-inner-background: " + PANEL_BG + "; " +
            UiScale.uiFontCss(18)
        );

        // ── checkbox column ────────────────────────────────────────────
        TableColumn<CardRowData, Boolean> cbCol = new TableColumn<>("");
        cbCol.setPrefWidth(UiScale.size(46));
        cbCol.setMinWidth(UiScale.size(46));
        cbCol.setMaxWidth(UiScale.size(46));
        cbCol.setSortable(false);
        cbCol.setEditable(true);
        cbCol.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        cbCol.setCellFactory(CheckBoxTableCell.forTableColumn(cbCol));

        // ── question column ────────────────────────────────────────────
        TableColumn<CardRowData, String> qCol = new TableColumn<>("QUESTION");
        qCol.setSortable(false);
        qCol.setCellValueFactory(cell -> cell.getValue().questionProperty());
        qCol.setCellFactory(col -> previewTextCell(CardRowData::questionProperty, editPreviewMode));

        // ── answer column ──────────────────────────────────────────────
        TableColumn<CardRowData, String> aCol = new TableColumn<>("ANSWER");
        aCol.setSortable(false);
        aCol.setCellValueFactory(cell -> cell.getValue().answerProperty());
        aCol.setCellFactory(col -> previewTextCell(CardRowData::answerProperty, editPreviewMode));

        // ── difficulty column ──────────────────────────────────────────
        TableColumn<CardRowData, String> dCol = new TableColumn<>("DIFFICULTY");
        dCol.setPrefWidth(UiScale.size(160));
        dCol.setMinWidth(UiScale.size(140));
        dCol.setMaxWidth(UiScale.size(180));
        dCol.setSortable(false);
        dCol.setCellValueFactory(cell -> cell.getValue().difficultyProperty());
        dCol.setCellFactory(col -> difficultyComboCell(editPreviewMode));

        table.getColumns().addAll(cbCol, qCol, aCol, dCol);

        // SELECT ALL bulk-toggles every row's selected state
        selectAllCb.setOnAction(e -> {
            boolean all = selectAllCb.isSelected();
            rows.forEach(r -> r.selectedProperty().set(all));
            importBtn.setDisable(!all || rows.isEmpty());
        });

        // Attach a per-row listener whenever new rows arrive (including via setAll())
        // so the import button tracks selection count correctly.
        rows.addListener((ListChangeListener<CardRowData>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (CardRowData row : change.getAddedSubList()) {
                        row.selectedProperty().addListener((obs, old, nv) ->
                            importBtn.setDisable(rows.stream().noneMatch(CardRowData::isSelected)));
                    }
                }
            }
        });

        return table;
    }

    /**
     * Creates a {@link TableCell} that shows a read-only label until preview-edit mode
     * is enabled, then swaps to a {@link TextArea}. Edits flow directly into the model.
     *
     * @param propGetter extracts the {@link StringProperty} to bind from a row object
     */
    private static TableCell<CardRowData, String> previewTextCell(
            Function<CardRowData, StringProperty> propGetter,
            BooleanProperty editPreviewMode) {

        return new TableCell<CardRowData, String>() {

            private final TextArea ta = new TextArea();
            private final Label label = new Label();
            private CardRowData boundRow = null;

            {
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setMinHeight(UiScale.size(64));
                label.setPadding(UiScale.insets(10, 12, 10, 12));
                label.setTextFill(Color.BLACK);
                label.setTextOverrun(OverrunStyle.CLIP);
                label.setStyle(
                    "-fx-background-color: white; " +
                    "-fx-border-color: " + PANEL_BORDER + "; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 4; -fx-background-radius: 4; " +
                    UiScale.uiFontCss(18)
                );

                ta.setWrapText(true);
                ta.setStyle(
                    "-fx-text-fill: black; " +
                    "-fx-background-color: white; " +
                    "-fx-border-color: " + PANEL_BORDER + "; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 4; -fx-background-radius: 4; " +
                    UiScale.uiFontCss(18) + " -fx-padding: " + UiScale.size(6) + " " + UiScale.size(8) + ";"
                );
                ta.setEditable(true);
                ta.setMaxWidth(Double.MAX_VALUE);
                ta.setMinHeight(UiScale.size(64));
                ta.textProperty().addListener((obs, old, nv) -> adjustHeight(nv));
                widthProperty().addListener((obs, old, nv) -> adjustHeight(ta.getText()));
            }

            private void adjustHeight(String text) {
                if (text == null) text = "";
                long explicit = text.chars().filter(c -> c == '\n').count();
                double cellWidth = getWidth();
                if (cellWidth < UiScale.size(160) && getTableColumn() != null) {
                    cellWidth = getTableColumn().getWidth();
                }
                if (cellWidth < UiScale.size(160)) {
                    cellWidth = UiScale.size(360);
                }
                double usableWidth = Math.max(UiScale.size(120), cellWidth - UiScale.size(36));
                double averageCharWidth = UiScale.size(9.5);
                double charsPerLine = Math.max(12, usableWidth / averageCharWidth);
                double approxWrapped = text.length() / charsPerLine;
                double lines = Math.max(1, explicit + 1 + approxWrapped);
                double height = Math.max(UiScale.size(64), lines * UiScale.size(26) + UiScale.size(18));
                if (boundRow != null && height > boundRow.previewHeightProperty().get()) {
                    boundRow.previewHeightProperty().set(height);
                }
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                CardRowData row = (getTableRow() == null) ? null : getTableRow().getItem();

                if (empty || row == null) {
                    // Detach from the old row when the cell is cleared
                    if (boundRow != null) {
                        ta.textProperty().unbindBidirectional(propGetter.apply(boundRow));
                        label.textProperty().unbind();
                        label.prefHeightProperty().unbind();
                        label.minHeightProperty().unbind();
                        ta.prefHeightProperty().unbind();
                        ta.minHeightProperty().unbind();
                        boundRow = null;
                    }
                    setGraphic(null);
                    setText(null);
                } else {
                    // Re-bind only when the cell has been recycled for a different row
                    if (boundRow != row) {
                        if (boundRow != null) {
                            ta.textProperty().unbindBidirectional(propGetter.apply(boundRow));
                            label.textProperty().unbind();
                            label.prefHeightProperty().unbind();
                            label.minHeightProperty().unbind();
                            ta.prefHeightProperty().unbind();
                            ta.minHeightProperty().unbind();
                        }
                        boundRow = row;
                        ta.textProperty().bindBidirectional(propGetter.apply(row));
                        label.textProperty().bind(propGetter.apply(row));
                        label.prefHeightProperty().bind(row.previewHeightProperty());
                        label.minHeightProperty().bind(row.previewHeightProperty());
                        ta.prefHeightProperty().bind(row.previewHeightProperty());
                        ta.minHeightProperty().bind(row.previewHeightProperty());
                    }
                    adjustHeight(ta.getText());
                    setGraphic(editPreviewMode.get() ? ta : label);
                    setText(null);
                }
            }
        };
    }

    /**
     * Creates a {@link TableCell} that shows a read-only difficulty label until
     * preview-edit mode is enabled, then swaps to a {@link ComboBox} with:
     * "--Select Difficulty--", "Easy", "Medium", "Hard" (in that order).
     *
     * <p>Selecting "--Select Difficulty--" stores {@code null} in the model.
     * Selecting any other option stores the string value directly.
     */
    private static TableCell<CardRowData, String> difficultyComboCell(BooleanProperty editPreviewMode) {

        return new TableCell<CardRowData, String>() {

            private final ComboBox<String> combo = new ComboBox<>();
            private final Label label = new Label();
            private CardRowData boundRow = null;

            {
                label.setMaxWidth(Double.MAX_VALUE);
                label.setPadding(UiScale.insets(10, 12, 10, 12));
                label.setTextFill(Color.BLACK);
                label.setStyle(
                    "-fx-background-color: white; " +
                    "-fx-border-color: " + PANEL_BORDER + "; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 4; -fx-background-radius: 4; " +
                    UiScale.uiFontCss(18)
                );

                combo.getItems().addAll("--Select Difficulty--", "Easy", "Medium", "Hard");
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setStyle(
                    UiScale.uiFontCss(18) +
                    "-fx-background-color: white; " +
                    "-fx-border-color: " + PANEL_BORDER + "; " +
                    "-fx-border-radius: 4;"
                );
                combo.setOnAction(e -> {
                    if (boundRow != null) {
                        String val = combo.getValue();
                        boundRow.difficultyProperty().set(
                            "--Select Difficulty--".equals(val) ? null : val);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                CardRowData row = (getTableRow() == null) ? null : getTableRow().getItem();

                if (empty || row == null) {
                    if (boundRow != null) {
                        label.prefHeightProperty().unbind();
                        label.minHeightProperty().unbind();
                        combo.prefHeightProperty().unbind();
                        combo.minHeightProperty().unbind();
                    }
                    boundRow = null;
                    setGraphic(null);
                    setText(null);
                } else {
                    if (boundRow != row) {
                        if (boundRow != null) {
                            label.prefHeightProperty().unbind();
                            label.minHeightProperty().unbind();
                            combo.prefHeightProperty().unbind();
                            combo.minHeightProperty().unbind();
                        }
                        label.prefHeightProperty().bind(row.previewHeightProperty());
                        label.minHeightProperty().bind(row.previewHeightProperty());
                        combo.prefHeightProperty().bind(row.previewHeightProperty());
                        combo.minHeightProperty().bind(row.previewHeightProperty());
                    }
                    boundRow = row;
                    String display = (item == null || item.isBlank())
                        ? "--Select Difficulty--" : item;
                    // Guard against triggering onAction while syncing the displayed value
                    if (!display.equals(combo.getValue())) {
                        combo.setValue(display);
                    }
                    label.setText(display);
                    setGraphic(editPreviewMode.get() ? combo : label);
                    setText(null);
                }
            }
        };
    }

    // ────────────────────────────────────────────────────────────────
    // Preview loading
    // ────────────────────────────────────────────────────────────────

    /**
     * Parses {@code file} via the appropriate service and replaces {@code cardRows}
     * with the resulting preview data.  The {@link ListChangeListener} wired in
     * {@link #buildTable} then attaches selection listeners to each new row.
     *
     * @param file      the file to parse
     * @param format    {@code "CSV"} or {@code "JSON"}
     * @param cardRows  observable list to populate (existing contents are replaced)
     * @param importBtn button whose disabled state depends on row selection
     * @param selectAllCb checkbox reset after each file load
     * @param nameField new-deck name field that deck JSON may prefill
     * @param descField new-deck description field that deck JSON may prefill
     * @param sourceDeckInfo label that displays optional source deck metadata
     * @param mc        controller that delegates to the service layer
     */
    private static void loadPreview(
            File file, String format,
            ObservableList<CardRowData> cardRows,
            Button importBtn,
            CheckBox selectAllCb,
            TextField nameField,
            TextField descField,
            Label sourceDeckInfo,
            MainController mc) {

        try {
            ImportPreview preview = "CSV".equals(format)
                ? new ImportPreview(mc.previewCsvCards(file), null, null, null)
                : mc.previewJsonImport(file);

            List<CardRowData> newRows = new ArrayList<>();
            for (CardJson c : preview.getCards()) {
                newRows.add(new CardRowData(c.getQuestion(), c.getAnswer(), c.getDifficulty()));
            }
            cardRows.setAll(newRows);

            importBtn.setDisable(true);
            selectAllCb.setSelected(false);
            updateSourceDeckInfo(preview, nameField, descField, sourceDeckInfo, cardRows.size());

            if (cardRows.isEmpty()) {
                MainFrame.showErrorDialog(
                    "No valid questions found in the selected file.\n" +
                    "Ensure each row has a non-empty question and answer.");
            }
        } catch (CustomException ex) {
            MainFrame.showErrorDialog("Failed to load file: " + ex.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Small helpers
    // ────────────────────────────────────────────────────────────────

    private static void updateSourceDeckInfo(
            ImportPreview preview,
            TextField nameField,
            TextField descField,
            Label sourceDeckInfo,
            int cardCount) {
        if (preview == null || !preview.hasDeckMetadata()) {
            sourceDeckInfo.setText("");
            sourceDeckInfo.setVisible(false);
            sourceDeckInfo.setManaged(false);
            return;
        }

        String deckName = trimToNull(preview.getDeckName());
        String description = trimToNull(preview.getDescription());
        String exportedAt = trimToNull(preview.getExportedAt());

        if (deckName != null) {
            nameField.setText(deckName);
        }
        if (description != null) {
            descField.setText(description);
        }

        StringBuilder text = new StringBuilder();
        text.append("Loaded deck: ").append(deckName == null ? "(unnamed deck)" : deckName);
        text.append("\nCards found: ").append(cardCount);
        if (description != null) {
            text.append("\nDescription: ").append(description);
        }
        if (exportedAt != null) {
            text.append("\nExported at: ").append(exportedAt);
        }

        sourceDeckInfo.setText(text.toString());
        sourceDeckInfo.setVisible(true);
        sourceDeckInfo.setManaged(true);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Opens a file-chooser dialog and returns the chosen file, or {@code null} if cancelled. */
    private static File chooseFile(Window owner, String title, String filterDesc, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterDesc, ext));
        return fc.showOpenDialog(owner);
    }

    // ── styled popup dialogs (match ExitPanel aesthetic) ─────────────────

    /**
     * Styled confirmation dialog — returns {@code true} if the user clicks Confirm.
     */
    private static boolean confirmCancel(Window owner) {
        return customConfirm(owner,
            "Cancel\nImport?",
            "Any loaded questions will be discarded. Are you sure?",
            "STAY", "YES, LEAVE");
    }

    private static boolean customConfirm(Window owner, String title, String message,
                                         String cancelLabel, String confirmLabel) {
        boolean[] result = {false};

        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox container = popupContainer();
        double[] drag = {0, 0};
        container.setOnMousePressed(ev -> { drag[0] = ev.getSceneX(); drag[1] = ev.getSceneY(); });
        container.setOnMouseDragged(ev -> {
            dialog.setX(ev.getScreenX() - drag[0]);
            dialog.setY(ev.getScreenY() - drag[1]);
        });

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.getChildren().add(popupXButton(() -> dialog.close()));
        VBox.setMargin(topBar, UiScale.insets(5, -25, 0, 0));

        Label titleLbl = new Label(title);
        titleLbl.setFont(UiScale.headingFont(38));
        titleLbl.setTextFill(Color.web(PRIMARY_BLUE));
        titleLbl.setWrapText(true);

        Label msgLbl = new Label(message);
        msgLbl.setFont(UiScale.bodyFont(14));
        msgLbl.setTextFill(Color.web(PRIMARY_BLUE));
        msgLbl.setWrapText(true);
        VBox.setMargin(msgLbl, UiScale.insets(15, 10, 30, 0));

        Button cancelBtn = popupButton(cancelLabel, "#c5cae9", "#b3b9e0");
        cancelBtn.setOnAction(e -> dialog.close());

        Button confirmBtn = popupButton(confirmLabel, "#ff9999", "#ff6666");
        confirmBtn.setOnAction(e -> { result[0] = true; dialog.close(); });

        VBox buttons = new VBox(10, cancelBtn, confirmBtn);
        buttons.setAlignment(Pos.CENTER);

        container.getChildren().addAll(topBar, titleLbl, msgLbl, buttons);

        Scene scene = new Scene(container, UiScale.size(300), UiScale.size(490));
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
        return result[0];
    }

    private static void customWarning(Window owner, String title, String message) {
        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox container = popupContainer();
        double[] drag = {0, 0};
        container.setOnMousePressed(ev -> { drag[0] = ev.getSceneX(); drag[1] = ev.getSceneY(); });
        container.setOnMouseDragged(ev -> {
            dialog.setX(ev.getScreenX() - drag[0]);
            dialog.setY(ev.getScreenY() - drag[1]);
        });

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.getChildren().add(popupXButton(() -> dialog.close()));
        VBox.setMargin(topBar, UiScale.insets(5, -25, 0, 0));

        Label titleLbl = new Label(title);
        titleLbl.setFont(UiScale.headingFont(38));
        titleLbl.setTextFill(Color.web(PRIMARY_BLUE));
        titleLbl.setWrapText(true);

        Label msgLbl = new Label(message);
        msgLbl.setFont(UiScale.bodyFont(14));
        msgLbl.setTextFill(Color.web(PRIMARY_BLUE));
        msgLbl.setWrapText(true);
        VBox.setMargin(msgLbl, UiScale.insets(15, 10, 30, 0));

        Button okBtn = popupButton("GOT IT", "#ff9999", "#ff6666");
        okBtn.setOnAction(e -> dialog.close());

        VBox buttons = new VBox(10, okBtn);
        buttons.setAlignment(Pos.CENTER);

        container.getChildren().addAll(topBar, titleLbl, msgLbl, buttons);

        Scene scene = new Scene(container, UiScale.size(300), UiScale.size(440));
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private static VBox popupContainer() {
        VBox box = new VBox(15);
        box.setSpacing(UiScale.size(15));
        box.setPadding(UiScale.insets(0, 35, 35, 35));
        box.setAlignment(Pos.TOP_LEFT);
        box.setStyle(
            "-fx-background-color: #f8fafc;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + PRIMARY_BLUE + ";" +
            "-fx-border-radius: 12;"
        );
        return box;
    }

    private static Button popupXButton(Runnable onClose) {
        Button btn = new Button("X");
        String normal = "-fx-background-color: transparent; -fx-text-fill: #1A438E;" +
                        " -fx-font-size: " + UiScale.size(18) + "px; -fx-cursor: hand;";
        String hover  = "-fx-background-color: transparent; -fx-text-fill: red;" +
                        " -fx-font-size: " + UiScale.size(18) + "px; -fx-cursor: hand;";
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(normal));
        btn.setOnAction(e -> onClose.run());
        return btn;
    }

    private static Button popupButton(String text, String normalColor, String hoverColor) {
        Button btn = new Button(text);
        btn.setPrefWidth(UiScale.size(230));
        btn.setPrefHeight(UiScale.size(45));
        String normal = "-fx-background-color: " + normalColor + "; -fx-text-fill: " + PRIMARY_BLUE + ";" +
                        " " + UiScale.buttonFontCss(15) +
                        " -fx-background-radius: 25; -fx-cursor: hand;";
        String hover  = "-fx-background-color: " + hoverColor  + "; -fx-text-fill: " + PRIMARY_BLUE + ";" +
                        " " + UiScale.buttonFontCss(15) +
                        " -fx-background-radius: 25; -fx-cursor: hand;";
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(normal));
        return btn;
    }

    /** Converts a list of selected {@link CardRowData} objects to plain {@link CardJson} DTOs. */
    private static List<CardJson> toCardJsonList(List<CardRowData> rows) {
        List<CardJson> out = new ArrayList<>();
        for (CardRowData r : rows) {
            out.add(new CardJson(r.getQuestion(), r.getAnswer(), r.getDifficulty()));
        }
        return out;
    }

    /** Creates a reusable {@link ListCell} that displays a {@link Deck} by name. */
    private static ListCell<Deck> deckCell() {
        return new ListCell<Deck>() {
            @Override
            protected void updateItem(Deck item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        };
    }

    // ── inline CSS string helpers ──────────────────────────────────

    private static String comboStyle() {
        return "-fx-background-color: white; " +
               "-fx-border-color: " + PANEL_BORDER + "; " +
               "-fx-border-width: 1.5; -fx-border-radius: 5; -fx-background-radius: 5; " +
               UiScale.uiFontCss(18);
    }

    private static String inputStyle() {
        return "-fx-background-color: white; " +
               "-fx-border-color: " + PANEL_BORDER + "; " +
               "-fx-border-width: 1.5; -fx-border-radius: 5; -fx-background-radius: 5; " +
               UiScale.uiFontCss(18) + " -fx-padding: " + UiScale.size(8) + " " + UiScale.size(12) + ";";
    }

    private static String previewBoxStyle() {
        return "-fx-background-color: " + PANEL_BG + "; " +
               "-fx-border-color: " + PANEL_BORDER + "; " +
               "-fx-border-width: 2; " +
               "-fx-border-radius: 10; -fx-background-radius: 10;";
    }

    /**
     * Creates a rounded pill-shaped button in the "IMPORT FROM CSV / JSON" style.
     */
    private static Button fileImportButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(UiScale.size(44));
        String normal = "-fx-background-color: #d5d5e8; -fx-text-fill: " + PRIMARY_BLUE + "; " +
                        UiScale.buttonFontCss(18) +
                        " -fx-background-radius: 20; -fx-cursor: hand; -fx-padding: " + UiScale.size(6) + " " + UiScale.size(14) + ";";
        String hover  = "-fx-background-color: #bbbbd8; -fx-text-fill: " + PRIMARY_BLUE + "; " +
                        UiScale.buttonFontCss(18) +
                        " -fx-background-radius: 20; -fx-cursor: hand; -fx-padding: " + UiScale.size(6) + " " + UiScale.size(14) + ";";
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(normal));
        return btn;
    }

    /**
     * Creates a large pill-shaped action button (e.g., "IMPORT TO SYSTEM" / "CANCEL").
     * Automatically applies a greyed-out disabled style when the button is disabled.
     *
     * @param text         button label
     * @param normalColour fill colour (hex) when enabled and not hovered
     * @param hoverColour  fill colour (hex) on hover
     */
    private static Button actionButton(String text, String normalColour, String hoverColour) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(UiScale.size(64));
        String normal   = "-fx-background-color: " + normalColour + "; -fx-text-fill: white; " +
                          UiScale.buttonFontCss(22) +
                          "-fx-background-radius: 28; -fx-cursor: hand;";
        String hover    = "-fx-background-color: " + hoverColour  + "; -fx-text-fill: white; " +
                          UiScale.buttonFontCss(22) +
                          "-fx-background-radius: 28; -fx-cursor: hand;";
        String disabled = "-fx-background-color: #b0b0b0; -fx-text-fill: #e8e8e8; " +
                          UiScale.buttonFontCss(22) + "-fx-background-radius: 28;";
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> { if (!btn.isDisabled()) btn.setStyle(hover);  });
        btn.setOnMouseExited(e  -> { if (!btn.isDisabled()) btn.setStyle(normal); });
        btn.disabledProperty().addListener((obs, old, dis) ->
            btn.setStyle(dis ? disabled : normal));
        return btn;
    }
}
