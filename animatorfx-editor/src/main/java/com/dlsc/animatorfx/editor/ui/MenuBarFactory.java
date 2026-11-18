package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the menu bar. Every item has an id ({@code menu-file-save}, {@code menu-edit-duplicate},
 * …) for {@link MainWindow#menuItem}. Items whose feature does not exist yet are present but
 * disabled and have no action.
 */
final class MenuBarFactory {

    static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private final MainWindow window;
    private final Workspace workspace;
    private final FileActions files;

    MenuBarFactory(MainWindow window) {
        this.window = window;
        this.workspace = window.getWorkspace();
        this.files = window.getFileActions();
    }

    MenuBar build() {
        MenuBar bar = new MenuBar(fileMenu(), editMenu(), viewMenu(), helpMenu());
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    private Menu fileMenu() {
        MenuItem revert = item("menu-file-revert", "Revert", null, files::revert);
        bindToDocument(doc -> revert.disableProperty().bind(
                        Bindings.createBooleanBinding(() -> doc.getPath() == null || !doc.isDirty(),
                                doc.pathProperty(), doc.dirtyProperty())),
                () -> unbindDisabled(revert));

        Menu imports = new Menu("Import");
        imports.setId("menu-file-import");
        imports.getItems().addAll(
                later("menu-file-import-svg", "SVG…", null),
                later("menu-file-import-lottie", "Lottie…", null),
                later("menu-file-import-fxml", "FXML…", null),
                later("menu-file-import-image", "Image…", null));

        // macOS has Quit in the application menu, where it ends the application the same way
        MenuItem quit = item("menu-file-quit", "Quit", shortcut(KeyCode.Q), Platform::exit);
        quit.setVisible(!MAC);
        SeparatorMenuItem quitSeparator = new SeparatorMenuItem();
        quitSeparator.setVisible(!MAC);

        Menu menu = new Menu("File");
        menu.setId("menu-file");
        menu.getItems().addAll(
                item("menu-file-new", "New", shortcut(KeyCode.N), files::newDocument),
                item("menu-file-open", "Open…", shortcut(KeyCode.O), files::open),
                recentMenu(),
                new SeparatorMenuItem(),
                item("menu-file-save", "Save", shortcut(KeyCode.S), files::save),
                item("menu-file-save-as", "Save As…", shortcut(KeyCode.S, KeyCombination.SHIFT_DOWN), files::saveAs),
                revert,
                new SeparatorMenuItem(),
                imports,
                new SeparatorMenuItem(),
                item("menu-file-close", "Close", shortcut(KeyCode.W), files::close),
                quitSeparator,
                quit);
        return menu;
    }

    private Menu recentMenu() {
        Menu menu = new Menu("Open Recent");
        menu.setId("menu-file-open-recent");
        InvalidationListener rebuild = obs -> {
            List<MenuItem> items = new ArrayList<>();
            for (Path file : workspace.getRecentFiles()) {
                MenuItem item = new MenuItem(file.getFileName() + "  —  " + file.toAbsolutePath().getParent());
                item.setMnemonicParsing(false);
                item.setOnAction(e -> files.open(file));
                items.add(item);
            }
            if (items.isEmpty()) {
                MenuItem none = new MenuItem("No Recent Files");
                none.setDisable(true);
                items.add(none);
            } else {
                items.add(new SeparatorMenuItem());
                items.add(item("menu-file-clear-recent", "Clear Menu", null, workspace.getRecentFiles()::clear));
            }
            menu.getItems().setAll(items);
        };
        workspace.getRecentFiles().addListener(rebuild);
        rebuild.invalidated(null);
        return menu;
    }

    private Menu editMenu() {
        MenuItem undo = item("menu-edit-undo", "Undo", shortcut(KeyCode.Z), () -> workspace.getActiveDocument().undo());
        MenuItem redo = item("menu-edit-redo", "Redo", shortcut(KeyCode.Z, KeyCombination.SHIFT_DOWN),
                () -> workspace.getActiveDocument().redo());
        bindToDocument(doc -> {
            undo.textProperty().bind(named("Undo", doc.undoNameProperty()));
            undo.disableProperty().bind(doc.canUndoProperty().not());
            redo.textProperty().bind(named("Redo", doc.redoNameProperty()));
            redo.disableProperty().bind(doc.canRedoProperty().not());
        }, () -> {
            for (MenuItem item : List.of(undo, redo)) {
                item.textProperty().unbind();
                unbindDisabled(item);
            }
            undo.setText("Undo");
            redo.setText("Redo");
        });

        Menu menu = new Menu("Edit");
        menu.setId("menu-edit");
        menu.getItems().addAll(undo, redo, new SeparatorMenuItem(),
                later("menu-edit-cut", "Cut", shortcut(KeyCode.X)),
                later("menu-edit-copy", "Copy", shortcut(KeyCode.C)),
                later("menu-edit-paste", "Paste", shortcut(KeyCode.V)),
                later("menu-edit-duplicate", "Duplicate", shortcut(KeyCode.D)),
                later("menu-edit-delete", "Delete", null),
                new SeparatorMenuItem(),
                later("menu-edit-select-all", "Select All", shortcut(KeyCode.A)),
                new SeparatorMenuItem(),
                later("menu-edit-group", "Group", shortcut(KeyCode.G)),
                later("menu-edit-ungroup", "Ungroup", shortcut(KeyCode.G, KeyCombination.SHIFT_DOWN)));
        return menu;
    }

    private Menu viewMenu() {
        Theme theme = window.getTheme();
        ToggleGroup group = new ToggleGroup();
        Menu themes = new Menu("Theme");
        themes.setId("menu-view-theme");
        for (Theme.Mode mode : Theme.Mode.values()) {
            String name = mode.name().charAt(0) + mode.name().substring(1).toLowerCase(Locale.ROOT);
            RadioMenuItem item = new RadioMenuItem(name);
            item.setId("menu-view-theme-" + mode.name().toLowerCase(Locale.ROOT));
            item.setToggleGroup(group);
            item.setSelected(theme.getMode() == mode);
            item.setOnAction(e -> theme.setMode(mode));
            theme.modeProperty().addListener((obs, oldMode, newMode) -> item.setSelected(newMode == mode));
            themes.getItems().add(item);
        }

        CheckMenuItem sidePanel = new CheckMenuItem("Side Panel");
        sidePanel.setId("menu-view-side-panel");
        sidePanel.setAccelerator(shortcut(KeyCode.I, KeyCombination.ALT_DOWN));
        sidePanel.selectedProperty().bindBidirectional(window.sidePanelVisibleProperty());
        CheckMenuItem timeline = new CheckMenuItem("Timeline");
        timeline.setId("menu-view-timeline");
        timeline.setAccelerator(shortcut(KeyCode.T, KeyCombination.ALT_DOWN));
        timeline.selectedProperty().bindBidirectional(window.timelineVisibleProperty());

        Menu menu = new Menu("View");
        menu.setId("menu-view");
        menu.getItems().addAll(
                later("menu-view-zoom-in", "Zoom In", shortcut(KeyCode.EQUALS)),
                later("menu-view-zoom-out", "Zoom Out", shortcut(KeyCode.MINUS)),
                later("menu-view-zoom-fit", "Zoom to Fit", shortcut(KeyCode.DIGIT0)),
                later("menu-view-actual-size", "Actual Size", shortcut(KeyCode.DIGIT1)),
                new SeparatorMenuItem(), themes, new SeparatorMenuItem(), sidePanel, timeline);
        return menu;
    }

    private Menu helpMenu() {
        Menu menu = new Menu("Help");
        menu.setId("menu-help");
        menu.getItems().add(item("menu-help-about", "About AnimatorFX", null,
                () -> window.getDialogs().showAbout(window.getStage())));
        return menu;
    }

    private interface DocumentBinding {
        void bind(EditorDocument document);
    }

    /** Runs {@code unbind} and then, for a non-null document, {@code bind} now and whenever the active document changes. */
    private void bindToDocument(DocumentBinding bind, Runnable unbind) {
        InvalidationListener rebind = obs -> {
            unbind.run();
            EditorDocument document = workspace.getActiveDocument();
            if (document != null) {
                bind.bind(document);
            }
        };
        workspace.activeDocumentProperty().addListener(rebind);
        rebind.invalidated(null);
    }

    private static void unbindDisabled(MenuItem item) {
        item.disableProperty().unbind();
        item.setDisable(true);
    }

    private static ObservableValue<String> named(String verb, ObservableValue<String> commandName) {
        return Bindings.createStringBinding(
                () -> commandName.getValue() == null ? verb : verb + " " + commandName.getValue(), commandName);
    }

    private static MenuItem item(String id, String text, KeyCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setId(id);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.setOnAction(e -> action.run());
        return item;
    }

    /** An item for a feature of a later task: present, disabled, without an action. */
    private static MenuItem later(String id, String text, KeyCombination accelerator) {
        MenuItem item = new MenuItem(text);
        item.setId(id);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.setDisable(true);
        return item;
    }

    private static KeyCombination shortcut(KeyCode code, KeyCombination.Modifier... modifiers) {
        KeyCombination.Modifier[] all = new KeyCombination.Modifier[modifiers.length + 1];
        all[0] = KeyCombination.SHORTCUT_DOWN;
        System.arraycopy(modifiers, 0, all, 1, modifiers.length);
        return new KeyCodeCombination(code, all);
    }
}
