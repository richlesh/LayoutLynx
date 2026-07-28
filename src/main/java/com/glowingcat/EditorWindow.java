/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the main editor window with four panes:
 * 1. File tree (left) - hierarchical list of HTML file and its CSS references
 * 2. Tabbed editor (center-left) - CSS/HTML editor with tabs
 * 3. Preview (center-right) - HTML/CSS live preview
 * 4. AI Chat (right) - AI assistant panel
 */
public class EditorWindow {

    /** Tracks the number of open windows so the app exits when the last one closes. */
    static final AtomicInteger windowCount = new AtomicInteger(0);

    /** Tracks all open EditorWindow instances. */
    static final List<EditorWindow> openInstances = new ArrayList<>();

    private final JFrame frame;
    private final JTabbedPane editorTabs;
    private final PreviewPanel previewPanel;
    private AIChatPanel aiChatPanel;
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JSplitPane treeEditorSplit;
    private JSplitPane editorPreviewSplit;
    private JSplitPane mainSplit;
    private JLabel filePathLabel;
    private JToggleButton previewToggle;
    private JToggleButton aiToggle;
    private JToggleButton hiddenCharsToggle;
    private boolean previewVisible = true;
    private boolean aiVisible = true;
    private boolean hiddenCharsVisible = false;
    private int lastPreviewDivider = -1;
    private int lastAiDivider = -1;
    private Preferences preferences;
    private File htmlFile;
    private final Map<File, TabInfo> openTabs = new LinkedHashMap<>();

    private FindDialog findDialog;
    private ReplaceDialog replaceDialog;
    private JMenuItem saveItem;

    /** Info about an open tab. */
    private static class TabInfo {
        RSyntaxTextArea textArea;
        RTextScrollPane scrollPane;
        JComponent tabComponent; // the component added to the tab pane
        UndoManager undoManager;
        File file;
        boolean dirty;
        boolean windowsLineEndings;
        long lastModifiedOnDisk;

        TabInfo(RSyntaxTextArea textArea, RTextScrollPane scrollPane, File file) {
            this.textArea = textArea;
            this.scrollPane = scrollPane;
            this.tabComponent = scrollPane; // default: scrollPane is the tab component
            this.file = file;
            this.undoManager = new UndoManager();
            this.dirty = false;
            this.windowsLineEndings = false;
            this.lastModifiedOnDisk = file != null ? file.lastModified() : 0;
        }
    }

    public EditorWindow() {
        preferences = Preferences.load();
        previewPanel = new PreviewPanel();
        editorTabs = new JTabbedPane(JTabbedPane.TOP);

        frame = new JFrame("LayoutLynx");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                windowCount.incrementAndGet();
                openInstances.add(EditorWindow.this);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmClose()) {
                    saveWindowState();
                    frame.dispose();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                openInstances.remove(EditorWindow.this);
                if (windowCount.decrementAndGet() == 0) {
                    System.exit(0);
                } else {
                    for (EditorWindow instance : openInstances) {
                        if (instance.frame.isDisplayable()) {
                            instance.frame.toFront();
                            instance.frame.requestFocus();
                            break;
                        }
                    }
                }
            }
        });
        frame.setSize(preferences.getWindowWidth(), preferences.getWindowHeight());
        frame.setAutoRequestFocus(true);

        // macOS + JFXPanel workaround: clicking on a background window may not
        // bring it to front because JFXPanel captures mouse events at the native level.
        // This listener detects when the window becomes active and ensures it's raised.
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                frame.toFront();
            }
        });

        // Application icon
        java.net.URL iconUrl = getClass().getClassLoader().getResource("app_icon_256.png");
        if (iconUrl != null) {
            frame.setIconImage(new ImageIcon(iconUrl).getImage());
        }

        buildMenuBar();
        buildLayout();

        // Create an initial empty CSS tab
        createNewTab("Untitled.css", SyntaxConstants.SYNTAX_STYLE_CSS, null);

        // Apply initial theme
        applyPreferences();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    public JFrame getFrame() { return frame; }

    public static EditorWindow getActiveInstance() {
        for (EditorWindow instance : openInstances) {
            if (instance.frame.isActive()) return instance;
        }
        return openInstances.isEmpty() ? null : openInstances.get(openInstances.size() - 1);
    }

    public static void openFileInWindow(File file) {
        // If there's an empty untitled window, use it
        for (EditorWindow instance : openInstances) {
            if (instance.htmlFile == null && instance.openTabs.isEmpty()) {
                instance.openHtmlFile(file);
                instance.frame.toFront();
                return;
            }
        }
        EditorWindow w = new EditorWindow();
        w.openHtmlFile(file);
    }

    // =========================================================================
    // Menu Bar
    // =========================================================================

    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");

        // On non-macOS, add an application menu with About, Settings, License Key, Quit
        if (!isMac) {
            JMenu appMenu = new JMenu("LayoutLynx");
            JMenuItem aboutItem = new JMenuItem("About LayoutLynx");
            aboutItem.addActionListener(e -> showAboutDialog());
            JMenuItem prefsItem = new JMenuItem("Settings...");
            prefsItem.addActionListener(e -> showPreferencesDialog());
            JMenuItem licenseItem = new JMenuItem("License Key...");
            licenseItem.addActionListener(e -> showLicenseDialog());
            JMenuItem quitItem = new JMenuItem("Quit LayoutLynx");
            quitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcutMask));
            quitItem.addActionListener(e -> exitApplication());
            appMenu.add(aboutItem);
            appMenu.addSeparator();
            appMenu.add(prefsItem);
            appMenu.add(licenseItem);
            appMenu.addSeparator();
            appMenu.add(quitItem);
            menuBar.add(appMenu);
        }

        // --- File menu ---
        JMenu fileMenu = new JMenu("File");
        JMenuItem newItem = new JMenuItem("New");
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask));
        newItem.addActionListener(e -> newFile());

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcutMask));
        openItem.addActionListener(e -> openFile());

        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask));
        closeItem.addActionListener(e -> { if (confirmClose()) frame.dispose(); });

        saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask));
        saveItem.addActionListener(e -> saveFile());

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveFileAs());

        JMenuItem pageSetupItem = new JMenuItem("Page Setup...");
        pageSetupItem.addActionListener(e -> showPageSetup());

        JMenuItem printItem = new JMenuItem("Print...");
        printItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, shortcutMask));
        printItem.addActionListener(e -> printDocument());

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(closeItem);
        fileMenu.addSeparator();
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(pageSetupItem);
        fileMenu.add(printItem);
        if (isMac) {
            fileMenu.addSeparator();
            JMenuItem licenseItem = new JMenuItem("License Key...");
            licenseItem.addActionListener(e -> showLicenseDialog());
            fileMenu.add(licenseItem);
        }
        menuBar.add(fileMenu);

        // --- Edit menu ---
        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask));
        undoItem.addActionListener(e -> {
            TabInfo tab = getActiveTab();
            if (tab != null && tab.undoManager.canUndo()) tab.undoManager.undo();
        });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcutMask));
        redoItem.addActionListener(e -> {
            TabInfo tab = getActiveTab();
            if (tab != null && tab.undoManager.canRedo()) tab.undoManager.redo();
        });

        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutMask));
        cutItem.addActionListener(e -> {
            TabInfo tab = getActiveTab();
            if (tab != null) tab.textArea.cut();
        });

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask));
        copyItem.addActionListener(e -> {
            TabInfo tab = getActiveTab();
            if (tab != null) tab.textArea.copy();
        });

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask));
        pasteItem.addActionListener(e -> {
            TabInfo tab = getActiveTab();
            if (tab != null) tab.textArea.paste();
        });

        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcutMask));
        selectAllItem.addActionListener(e -> {
            TabInfo tab = getActiveTab();
            if (tab != null) tab.textArea.selectAll();
        });

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.addSeparator();
        editMenu.add(selectAllItem);
        editMenu.addSeparator();
        JMenuItem tabsToSpacesItem = new JMenuItem("Convert Tabs to Spaces");
        tabsToSpacesItem.addActionListener(e -> convertTabsToSpaces());
        JMenuItem spacesToTabsItem = new JMenuItem("Convert Spaces to Tabs");
        spacesToTabsItem.addActionListener(e -> convertSpacesToTabs());
        editMenu.add(tabsToSpacesItem);
        editMenu.add(spacesToTabsItem);
        editMenu.addSeparator();
        JMenuItem tidyItem = new JMenuItem("Tidy Document");
        tidyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, shortcutMask | KeyEvent.SHIFT_DOWN_MASK));
        tidyItem.addActionListener(e -> tidyDocument());
        editMenu.add(tidyItem);
        menuBar.add(editMenu);

        // --- Search menu ---
        JMenu searchMenu = new JMenu("Search");
        JMenuItem findItem = new JMenuItem("Find...");
        findItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask));
        findItem.addActionListener(e -> showFindDialog());

        JMenuItem replaceItem = new JMenuItem("Replace...");
        replaceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcutMask));
        replaceItem.addActionListener(e -> showReplaceDialog());

        searchMenu.add(findItem);
        searchMenu.add(replaceItem);
        menuBar.add(searchMenu);

        // --- CSS menu ---
        JMenu cssMenu = new JMenu("CSS");
        buildCSSMenu(cssMenu);
        menuBar.add(cssMenu);

        // --- Window menu ---
        JMenu windowMenu = new JMenu("Window");
        windowMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                windowMenu.removeAll();
                JMenuItem minimizeItem = new JMenuItem("Minimize");
                minimizeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, shortcutMask));
                minimizeItem.addActionListener(ev -> frame.setState(Frame.ICONIFIED));
                windowMenu.add(minimizeItem);

                JMenuItem zoomItem = new JMenuItem("Zoom");
                zoomItem.addActionListener(ev -> {
                    if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                        frame.setExtendedState(Frame.NORMAL);
                    } else {
                        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
                    }
                });
                windowMenu.add(zoomItem);
                windowMenu.addSeparator();

                for (EditorWindow instance : openInstances) {
                    String title = instance.frame.getTitle();
                    JCheckBoxMenuItem windowItem = new JCheckBoxMenuItem(title);
                    windowItem.setSelected(instance == EditorWindow.this && frame.isFocused());
                    windowItem.addActionListener(ev -> {
                        instance.frame.toFront();
                        instance.frame.requestFocus();
                    });
                    windowMenu.add(windowItem);
                }
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
        menuBar.add(windowMenu);

        frame.setJMenuBar(menuBar);
    }

    private void buildCSSMenu(JMenu cssMenu) {
        // --- Selectors section ---
        JMenuItem universalSel = new JMenuItem("Universal (*)");
        universalSel.addActionListener(e -> insertCSS("* {\n    \n}\n"));
        JMenuItem elementSel = new JMenuItem("Element");
        elementSel.addActionListener(e -> insertCSS("element {\n    \n}\n"));
        JMenuItem classSel = new JMenuItem("Class (.)");
        classSel.addActionListener(e -> insertCSS(".classname {\n    \n}\n"));
        JMenuItem idSel = new JMenuItem("ID (#)");
        idSel.addActionListener(e -> insertCSS("#idname {\n    \n}\n"));
        JMenuItem attrSel = new JMenuItem("Attribute ([])");
        attrSel.addActionListener(e -> insertCSS("[attribute] {\n    \n}\n"));
        JMenuItem descendantSel = new JMenuItem("Descendant (A B)");
        descendantSel.addActionListener(e -> insertCSS("parent child {\n    \n}\n"));
        JMenuItem childSel = new JMenuItem("Child (A > B)");
        childSel.addActionListener(e -> insertCSS("parent > child {\n    \n}\n"));
        JMenuItem sibSel = new JMenuItem("Adjacent Sibling (A + B)");
        sibSel.addActionListener(e -> insertCSS("element + sibling {\n    \n}\n"));
        JMenuItem genSibSel = new JMenuItem("General Sibling (A ~ B)");
        genSibSel.addActionListener(e -> insertCSS("element ~ sibling {\n    \n}\n"));
        JMenuItem pseudoClassSel = new JMenuItem("Pseudo-class (:)");
        pseudoClassSel.addActionListener(e -> insertCSS("element:hover {\n    \n}\n"));
        JMenuItem pseudoElSel = new JMenuItem("Pseudo-element (::)");
        pseudoElSel.addActionListener(e -> insertCSS("element::before {\n    content: \"\";\n}\n"));
        JMenuItem mediaSel = new JMenuItem("@media Query");
        mediaSel.addActionListener(e -> insertCSS("@media (max-width: 768px) {\n    \n}\n"));
        JMenuItem keyframesSel = new JMenuItem("@keyframes");
        keyframesSel.addActionListener(e -> insertCSS("@keyframes animationName {\n    from {\n        \n    }\n    to {\n        \n    }\n}\n"));

        cssMenu.add(universalSel);
        cssMenu.add(elementSel);
        cssMenu.add(classSel);
        cssMenu.add(idSel);
        cssMenu.add(attrSel);
        cssMenu.addSeparator();
        cssMenu.add(descendantSel);
        cssMenu.add(childSel);
        cssMenu.add(sibSel);
        cssMenu.add(genSibSel);
        cssMenu.addSeparator();
        cssMenu.add(pseudoClassSel);
        cssMenu.add(pseudoElSel);
        cssMenu.addSeparator();
        cssMenu.add(mediaSel);
        cssMenu.add(keyframesSel);
        cssMenu.addSeparator();

        // --- Properties section ---
        JMenu layoutProps = new JMenu("Layout");
        addPropertyItem(layoutProps, "display", "display: flex;");
        addPropertyItem(layoutProps, "position", "position: relative;");
        addPropertyItem(layoutProps, "float", "float: left;");
        addPropertyItem(layoutProps, "clear", "clear: both;");
        addPropertyItem(layoutProps, "overflow", "overflow: hidden;");
        addPropertyItem(layoutProps, "z-index", "z-index: 1;");
        cssMenu.add(layoutProps);

        JMenu boxProps = new JMenu("Box Model");
        addPropertyItem(boxProps, "width", "width: 100%;");
        addPropertyItem(boxProps, "height", "height: auto;");
        addPropertyItem(boxProps, "margin", "margin: 0;");
        addPropertyItem(boxProps, "padding", "padding: 0;");
        addPropertyItem(boxProps, "border", "border: 1px solid #000;");
        addPropertyItem(boxProps, "border-radius", "border-radius: 4px;");
        addPropertyItem(boxProps, "box-sizing", "box-sizing: border-box;");
        cssMenu.add(boxProps);

        JMenu flexProps = new JMenu("Flexbox");
        addPropertyItem(flexProps, "flex-direction", "flex-direction: row;");
        addPropertyItem(flexProps, "flex-wrap", "flex-wrap: wrap;");
        addPropertyItem(flexProps, "justify-content", "justify-content: center;");
        addPropertyItem(flexProps, "align-items", "align-items: center;");
        addPropertyItem(flexProps, "align-self", "align-self: flex-start;");
        addPropertyItem(flexProps, "flex", "flex: 1;");
        addPropertyItem(flexProps, "gap", "gap: 8px;");
        cssMenu.add(flexProps);

        JMenu gridProps = new JMenu("Grid");
        addPropertyItem(gridProps, "grid-template-columns", "grid-template-columns: 1fr 1fr;");
        addPropertyItem(gridProps, "grid-template-rows", "grid-template-rows: auto;");
        addPropertyItem(gridProps, "grid-gap", "grid-gap: 16px;");
        addPropertyItem(gridProps, "grid-column", "grid-column: 1 / 3;");
        addPropertyItem(gridProps, "grid-row", "grid-row: 1 / 2;");
        cssMenu.add(gridProps);

        JMenu typographyProps = new JMenu("Typography");
        addPropertyItem(typographyProps, "font-family", "font-family: sans-serif;");
        addPropertyItem(typographyProps, "font-size", "font-size: 16px;");
        addPropertyItem(typographyProps, "font-weight", "font-weight: bold;");
        addPropertyItem(typographyProps, "font-style", "font-style: italic;");
        addPropertyItem(typographyProps, "line-height", "line-height: 1.5;");
        addPropertyItem(typographyProps, "letter-spacing", "letter-spacing: 0.5px;");
        addPropertyItem(typographyProps, "text-align", "text-align: center;");
        addPropertyItem(typographyProps, "text-decoration", "text-decoration: none;");
        addPropertyItem(typographyProps, "text-transform", "text-transform: uppercase;");
        cssMenu.add(typographyProps);

        JMenu colorProps = new JMenu("Colors & Backgrounds");
        addPropertyItem(colorProps, "color", "color: #333;");
        addPropertyItem(colorProps, "background-color", "background-color: #fff;");
        addPropertyItem(colorProps, "background-image", "background-image: url('');");
        addPropertyItem(colorProps, "background-size", "background-size: cover;");
        addPropertyItem(colorProps, "background-position", "background-position: center;");
        addPropertyItem(colorProps, "opacity", "opacity: 1;");
        cssMenu.add(colorProps);

        JMenu transformProps = new JMenu("Transforms & Transitions");
        addPropertyItem(transformProps, "transform", "transform: translateX(0);");
        addPropertyItem(transformProps, "transition", "transition: all 0.3s ease;");
        addPropertyItem(transformProps, "animation", "animation: name 1s ease infinite;");
        cssMenu.add(transformProps);
    }

    private void addPropertyItem(JMenu menu, String name, String value) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> insertCSSProperty(value));
        menu.add(item);
    }

    private void insertCSS(String text) {
        TabInfo tab = getActiveTab();
        if (tab != null) {
            tab.textArea.replaceSelection(text);
            tab.textArea.requestFocusInWindow();
        }
    }

    private void insertCSSProperty(String property) {
        TabInfo tab = getActiveTab();
        if (tab != null) {
            int pos = tab.textArea.getCaretPosition();
            String text = tab.textArea.getText();
            // If we're inside a rule block, just insert with indentation
            String insertion = "    " + property + "\n";
            tab.textArea.replaceSelection(insertion);
            tab.textArea.requestFocusInWindow();
        }
    }

    // =========================================================================
    // Layout
    // =========================================================================

    private void buildLayout() {
        // --- Toolbar ---
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        filePathLabel = new JLabel(" ");
        filePathLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolbar.add(filePathLabel, BorderLayout.CENTER);

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        togglePanel.setOpaque(false);

        // Hidden characters toggle button
        ImageIcon hiddenCharsIcon = null;
        var hiddenCharsUrl = getClass().getClassLoader().getResource("hidden_chars.png");
        if (hiddenCharsUrl != null) {
            hiddenCharsIcon = new ImageIcon(new ImageIcon(hiddenCharsUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        hiddenCharsToggle = new JToggleButton(hiddenCharsIcon != null ? hiddenCharsIcon : null, false);
        if (hiddenCharsIcon == null) hiddenCharsToggle.setText("¶");
        hiddenCharsToggle.setToolTipText("Show/Hide Invisible Characters");
        hiddenCharsToggle.setFocusPainted(false);
        hiddenCharsToggle.setBorderPainted(false);
        hiddenCharsToggle.setContentAreaFilled(false);
        hiddenCharsToggle.setOpaque(false);
        hiddenCharsToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hiddenCharsToggle.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        hiddenCharsToggle.addActionListener(e -> {
            hiddenCharsVisible = hiddenCharsToggle.isSelected();
            hiddenCharsToggle.setBackground(hiddenCharsVisible ? preferences.getButtonHighlightColorObj() : null);
            hiddenCharsToggle.setContentAreaFilled(hiddenCharsVisible);
            hiddenCharsToggle.setOpaque(hiddenCharsVisible);
            // Apply to all open tabs
            for (TabInfo tab : openTabs.values()) {
                tab.textArea.setWhitespaceVisible(hiddenCharsVisible);
                tab.textArea.setEOLMarkersVisible(hiddenCharsVisible);
            }
        });
        togglePanel.add(hiddenCharsToggle);

        // Preview toggle button
        ImageIcon eyeIconFull = null;
        var eyeUrl = getClass().getClassLoader().getResource("eye.png");
        if (eyeUrl != null) {
            eyeIconFull = new ImageIcon(new ImageIcon(eyeUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        previewToggle = new JToggleButton(eyeIconFull != null ? eyeIconFull : null, true);
        if (eyeIconFull == null) previewToggle.setText("Preview");
        previewToggle.setToolTipText("Show/Hide Preview");
        previewToggle.setFocusPainted(false);
        previewToggle.setBorderPainted(false);
        previewToggle.setContentAreaFilled(true);
        previewToggle.setOpaque(true);
        previewToggle.setBackground(preferences.getButtonHighlightColorObj());
        previewToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        previewToggle.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        previewToggle.addActionListener(e -> togglePreview());
        togglePanel.add(previewToggle);

        // AI toggle button
        ImageIcon aiIconFull = null;
        var aiUrl = getClass().getClassLoader().getResource("AI.png");
        if (aiUrl != null) {
            aiIconFull = new ImageIcon(new ImageIcon(aiUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        aiToggle = new JToggleButton(aiIconFull != null ? aiIconFull : null, true);
        if (aiIconFull == null) aiToggle.setText("AI");
        aiToggle.setToolTipText("Show/Hide AI Assistant");
        aiToggle.setFocusPainted(false);
        aiToggle.setBorderPainted(false);
        aiToggle.setContentAreaFilled(true);
        aiToggle.setOpaque(true);
        aiToggle.setBackground(preferences.getButtonHighlightColorObj());
        aiToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        aiToggle.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        aiToggle.addActionListener(e -> toggleAI());
        togglePanel.add(aiToggle);

        toolbar.add(togglePanel, BorderLayout.EAST);
        frame.add(toolbar, BorderLayout.NORTH);

        // --- File Tree (Pane 1) ---
        rootNode = new DefaultMutableTreeNode("No file open");
        treeModel = new DefaultTreeModel(rootNode);
        fileTree = new JTree(treeModel);
        fileTree.setRootVisible(true);
        fileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // Use document icon for all nodes (no folder icons)
        javax.swing.tree.DefaultTreeCellRenderer treeRenderer = new javax.swing.tree.DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                // Always use the leaf (document) icon regardless of whether the node has children
                setIcon(getLeafIcon());
                return this;
            }
        };
        fileTree.setCellRenderer(treeRenderer);
        fileTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
            if (node == null || node.getUserObject() == null) return;
            Object userObj = node.getUserObject();
            if (userObj instanceof FileNodeData data) {
                openFileInTab(data.file);
            }
        });
        JScrollPane treeScroll = new JScrollPane(fileTree);
        treeScroll.setMinimumSize(new Dimension(150, 100));
        treeScroll.setPreferredSize(new Dimension(200, 400));

        // --- Editor Tabs (Pane 2) ---
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(editorTabs, BorderLayout.CENTER);
        editorPanel.setMinimumSize(new Dimension(200, 100));

        // --- Split: Tree | Editor ---
        treeEditorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, editorPanel);
        treeEditorSplit.setDividerLocation(200);
        treeEditorSplit.setResizeWeight(0.0);

        // --- Split: (Tree+Editor) | Preview ---
        editorPreviewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeEditorSplit, previewPanel);
        editorPreviewSplit.setDividerLocation(preferences.getEditorPreviewDivider());
        editorPreviewSplit.setResizeWeight(0.5);

        // --- Split: (Tree+Editor+Preview) | AI ---
        aiChatPanel = new AIChatPanel(this::getActiveTextArea, preferences);
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPreviewSplit, aiChatPanel);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setDividerLocation(frame.getWidth() - 380);

        frame.add(mainSplit, BorderLayout.CENTER);

        // Restore visibility state
        SwingUtilities.invokeLater(() -> {
            if (!preferences.isPreviewVisible()) {
                previewToggle.setSelected(false);
                previewVisible = false;
                lastPreviewDivider = preferences.getEditorPreviewDivider();
                editorPreviewSplit.setRightComponent(null);
                editorPreviewSplit.setDividerSize(0);
                previewToggle.setBackground(null);
                previewToggle.setContentAreaFilled(false);
                previewToggle.setOpaque(false);
            }
            if (!preferences.isAiVisible()) {
                aiToggle.setSelected(false);
                aiVisible = false;
                lastAiDivider = preferences.getMainDivider();
                mainSplit.setRightComponent(null);
                mainSplit.setDividerSize(0);
                aiToggle.setBackground(null);
                aiToggle.setContentAreaFilled(false);
                aiToggle.setOpaque(false);
            }
        });
    }

    // =========================================================================
    // Toggle Panels
    // =========================================================================

    private void togglePreview() {
        previewVisible = previewToggle.isSelected();
        if (previewVisible) {
            editorPreviewSplit.setRightComponent(previewPanel);
            editorPreviewSplit.setDividerSize(UIManager.getInt("SplitPane.dividerSize"));
            if (lastPreviewDivider > 0) {
                editorPreviewSplit.setDividerLocation(lastPreviewDivider);
            } else {
                editorPreviewSplit.setDividerLocation(editorPreviewSplit.getWidth() / 2);
            }
        } else {
            lastPreviewDivider = editorPreviewSplit.getDividerLocation();
            editorPreviewSplit.setRightComponent(null);
            editorPreviewSplit.setDividerSize(0);
        }
        editorPreviewSplit.revalidate();
        editorPreviewSplit.repaint();
        previewToggle.setBackground(previewVisible ? preferences.getButtonHighlightColorObj() : null);
        previewToggle.setContentAreaFilled(previewVisible);
        previewToggle.setOpaque(previewVisible);
    }

    private void toggleAI() {
        aiVisible = aiToggle.isSelected();
        if (aiVisible) {
            mainSplit.setRightComponent(aiChatPanel);
            mainSplit.setDividerSize(UIManager.getInt("SplitPane.dividerSize"));
            if (lastAiDivider > 0) {
                mainSplit.setDividerLocation(lastAiDivider);
            } else {
                mainSplit.setDividerLocation(mainSplit.getWidth() - 380);
            }
        } else {
            lastAiDivider = mainSplit.getDividerLocation();
            mainSplit.setRightComponent(null);
            mainSplit.setDividerSize(0);
        }
        mainSplit.revalidate();
        mainSplit.repaint();
        aiToggle.setBackground(aiVisible ? preferences.getButtonHighlightColorObj() : null);
        aiToggle.setContentAreaFilled(aiVisible);
        aiToggle.setOpaque(aiVisible);
    }

    // =========================================================================
    // Tab Management
    // =========================================================================

    private TabInfo getActiveTab() {
        int idx = editorTabs.getSelectedIndex();
        if (idx < 0) return null;
        Component comp = editorTabs.getComponentAt(idx);
        for (TabInfo tab : openTabs.values()) {
            if (tab.tabComponent == comp) return tab;
        }
        return null;
    }

    private RSyntaxTextArea getActiveTextArea() {
        TabInfo tab = getActiveTab();
        return tab != null ? tab.textArea : null;
    }

    private void createNewTab(String title, String syntaxStyle, File file) {
        RSyntaxTextArea textArea = new RSyntaxTextArea(25, 80);
        textArea.setSyntaxEditingStyle(syntaxStyle);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
        textArea.setHighlightCurrentLine(false);
        textArea.setSelectionColor(preferences.getHighlightColorObj());
        textArea.setTabSize(preferences.getTabSize());
        textArea.setTabsEmulated(!preferences.isUseTabs());
        textArea.setTokenPainterFactory(new CustomTokenPainterFactory());

        // Disable colored backgrounds for embedded CSS/JS in HTML files
        if (SyntaxConstants.SYNTAX_STYLE_HTML.equals(syntaxStyle)) {
            textArea.setSecondaryLanguageBackground(1, null);
            textArea.setSecondaryLanguageBackground(2, null);
            textArea.setSecondaryLanguageBackground(3, null);
        }

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setFoldIndicatorEnabled(true);

        TabInfo tabInfo = new TabInfo(textArea, scrollPane, file);

        // Build the editor panel with optional color swatches (left) and minimap (right)
        JPanel editorPanel = new JPanel(new BorderLayout());

        if (SyntaxConstants.SYNTAX_STYLE_CSS.equals(syntaxStyle)) {
            CssSpecificityTooltip.install(textArea);
            CssCompletionProvider.install(textArea);
            ColorSwatchPanel swatchPanel = new ColorSwatchPanel(textArea);
            swatchPanel.setEditorScrollPane(scrollPane);
            editorPanel.add(swatchPanel, BorderLayout.WEST);
        }

        if (SyntaxConstants.SYNTAX_STYLE_HTML.equals(syntaxStyle)) {
            ColorSwatchPanel swatchPanel = new ColorSwatchPanel(textArea);
            swatchPanel.setEditorScrollPane(scrollPane);
            editorPanel.add(swatchPanel, BorderLayout.WEST);
        }

        editorPanel.add(scrollPane, BorderLayout.CENTER);

        MinimapPanel minimap = new MinimapPanel(textArea);
        minimap.setScrollPane(scrollPane);
        editorPanel.add(minimap, BorderLayout.EAST);

        tabInfo.tabComponent = editorPanel;

        // Wire up undo and document change listener
        textArea.getDocument().addUndoableEditListener(tabInfo.undoManager);
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onTabContentChanged(tabInfo); }
            @Override public void removeUpdate(DocumentEvent e) { onTabContentChanged(tabInfo); }
            @Override public void changedUpdate(DocumentEvent e) { onTabContentChanged(tabInfo); }
        });

        // Use a dummy key if no file (for untitled tabs)
        File key = file != null ? file : new File(title + "_" + System.nanoTime());
        openTabs.put(key, tabInfo);

        editorTabs.addTab(title, tabInfo.tabComponent);
        SwingUtilities.invokeLater(() -> editorTabs.setSelectedComponent(tabInfo.tabComponent));
    }

    private void openFileInTab(File file) {
        // Check if already open
        if (openTabs.containsKey(file)) {
            TabInfo tab = openTabs.get(file);
            editorTabs.setSelectedComponent(tab.tabComponent);
            return;
        }

        // Determine syntax style
        String syntaxStyle = SyntaxConstants.SYNTAX_STYLE_CSS;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            syntaxStyle = SyntaxConstants.SYNTAX_STYLE_HTML;
        }

        RSyntaxTextArea textArea = new RSyntaxTextArea(25, 80);
        textArea.setSyntaxEditingStyle(syntaxStyle);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
        textArea.setHighlightCurrentLine(false);
        textArea.setSelectionColor(preferences.getHighlightColorObj());
        textArea.setTabSize(preferences.getTabSize());
        textArea.setTabsEmulated(!preferences.isUseTabs());
        textArea.setTokenPainterFactory(new CustomTokenPainterFactory());

        // Disable colored backgrounds for embedded CSS/JS in HTML files
        if (SyntaxConstants.SYNTAX_STYLE_HTML.equals(syntaxStyle)) {
            textArea.setSecondaryLanguageBackground(1, null);
            textArea.setSecondaryLanguageBackground(2, null);
            textArea.setSecondaryLanguageBackground(3, null);
        }

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setFoldIndicatorEnabled(true);

        TabInfo tabInfo = new TabInfo(textArea, scrollPane, file);

        // Build the editor panel with optional color swatches (left) and minimap (right)
        JPanel editorPanel = new JPanel(new BorderLayout());

        if (SyntaxConstants.SYNTAX_STYLE_CSS.equals(syntaxStyle)) {
            CssSpecificityTooltip.install(textArea);
            CssCompletionProvider.install(textArea);
            ColorSwatchPanel swatchPanel = new ColorSwatchPanel(textArea);
            swatchPanel.setEditorScrollPane(scrollPane);
            editorPanel.add(swatchPanel, BorderLayout.WEST);
        }

        if (SyntaxConstants.SYNTAX_STYLE_HTML.equals(syntaxStyle)) {
            ColorSwatchPanel swatchPanel = new ColorSwatchPanel(textArea);
            swatchPanel.setEditorScrollPane(scrollPane);
            editorPanel.add(swatchPanel, BorderLayout.WEST);
        }

        editorPanel.add(scrollPane, BorderLayout.CENTER);

        MinimapPanel minimap = new MinimapPanel(textArea);
        minimap.setScrollPane(scrollPane);
        editorPanel.add(minimap, BorderLayout.EAST);

        tabInfo.tabComponent = editorPanel;

        // Load file content
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.contains("\r\n")) {
                tabInfo.windowsLineEndings = true;
                content = content.replace("\r\n", "\n");
            }
            textArea.setText(content);
            textArea.setCaretPosition(0);
            tabInfo.dirty = false;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Error reading file: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Wire up undo and document change listener
        textArea.getDocument().addUndoableEditListener(tabInfo.undoManager);
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onTabContentChanged(tabInfo); }
            @Override public void removeUpdate(DocumentEvent e) { onTabContentChanged(tabInfo); }
            @Override public void changedUpdate(DocumentEvent e) { onTabContentChanged(tabInfo); }
        });

        openTabs.put(file, tabInfo);
        editorTabs.addTab(file.getName(), tabInfo.tabComponent);
        SwingUtilities.invokeLater(() -> editorTabs.setSelectedComponent(tabInfo.tabComponent));
    }

    /**
     * Opens a file in a tab (if not already open) and highlights the given selector text.
     * Called from the computed styles dialog to navigate to the source of a property.
     *
     * @param fileName the CSS filename (e.g. "styles1.css"), or null to target the HTML file
     * @param selector the CSS selector text to find and highlight (e.g. "h1", ".container")
     */
    public void navigateToSelector(String fileName, String selector) {
        if (htmlFile == null || selector == null) return;

        // Determine target file: null fileName means the HTML file itself (inline <style>)
        File targetFile;
        if (fileName == null) {
            targetFile = htmlFile;
        } else {
            targetFile = new File(htmlFile.getParentFile(), fileName);
            if (!targetFile.isFile()) return;
        }

        // Open the file in a tab (switches to it if already open)
        openFileInTab(targetFile);

        // Bring editor frame to front so selection shows in active color
        frame.toFront();

        // Find and highlight the selector in the text area
        TabInfo tab = openTabs.get(targetFile);
        if (tab == null) return;

        SwingUtilities.invokeLater(() -> {
            RSyntaxTextArea textArea = tab.textArea;
            String text = textArea.getText();
            String searchPattern = selector.trim();

            int index = -1;
            int matchLength = searchPattern.length();

            // WebKit normalizes selectors (e.g., "*::before" -> "::before",
            // "*, *::before" -> "*, ::before"). Build alternate patterns to
            // match the original source which may differ from what WebKit reports.
            List<String> searchVariants = new ArrayList<>();
            searchVariants.add(searchPattern);
            // Try with * re-inserted before pseudo-elements
            String withStar = searchPattern.replaceAll("(?<=,\\s*)(::)", "*$1")
                .replaceAll("^(::)", "*$1");
            if (!withStar.equals(searchPattern)) {
                searchVariants.add(withStar);
            }
            // Try with * removed from before pseudo-elements (opposite direction)
            String withoutStar = searchPattern.replaceAll("\\*(::|:)", "$1");
            if (!withoutStar.equals(searchPattern)) {
                searchVariants.add(withoutStar);
            }

            // For HTML files, constrain search to within <style> blocks
            boolean isHtml = targetFile.getName().toLowerCase().matches(".*\\.html?$");

            if (isHtml) {
                // Find the selector only within <style>...</style> regions
                String lower = text.toLowerCase();
                int styleStart = 0;
                outer:
                while (styleStart < lower.length()) {
                    int openTag = lower.indexOf("<style", styleStart);
                    if (openTag < 0) break;
                    int openEnd = lower.indexOf(">", openTag);
                    if (openEnd < 0) break;
                    int closeTag = lower.indexOf("</style", openEnd);
                    if (closeTag < 0) closeTag = text.length();

                    // Search within this <style> block
                    String block = text.substring(openEnd + 1, closeTag);
                    for (String variant : searchVariants) {
                        java.util.regex.Pattern variantRegex = null;
                        try {
                            variantRegex = java.util.regex.Pattern.compile(buildFlexibleSelectorPattern(variant));
                        } catch (java.util.regex.PatternSyntaxException e) { /* ignore */ }
                        int found = findSelectorInBlock(block, variant, variantRegex);
                        if (found >= 0) {
                            index = openEnd + 1 + found;
                            matchLength = calcMatchLength(block, found, variant, variantRegex);
                            break outer;
                        }
                    }
                    styleStart = closeTag + 8;
                }
            } else {
                // For CSS files, search the entire file
                for (String variant : searchVariants) {
                    java.util.regex.Pattern variantRegex = null;
                    try {
                        variantRegex = java.util.regex.Pattern.compile(buildFlexibleSelectorPattern(variant));
                    } catch (java.util.regex.PatternSyntaxException e) { /* ignore */ }
                    int found = findSelectorInBlock(text, variant, variantRegex);
                    if (found >= 0) {
                        index = found;
                        matchLength = calcMatchLength(text, found, variant, variantRegex);
                        break;
                    }
                }
            }

            if (index >= 0) {
                textArea.setCaretPosition(index);
                textArea.select(index, index + matchLength);
                // Ensure the text area has focus so the selection renders in active color
                frame.toFront();
                frame.requestFocus();
                textArea.requestFocusInWindow();
            }
        });
    }

    /**
     * Builds a regex pattern that matches a CSS selector with flexible whitespace.
     * Allows newlines and varying whitespace between selector parts, especially
     * around commas (e.g., "html, body" matches "html,\n  body").
     */
    private String buildFlexibleSelectorPattern(String selector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selector.length(); i++) {
            char c = selector.charAt(i);
            if (c == ',') {
                // Allow optional whitespace around commas (including newlines)
                sb.append("\\s*,\\s*");
            } else if (Character.isWhitespace(c)) {
                // Collapse whitespace runs into \s+
                while (i + 1 < selector.length() && Character.isWhitespace(selector.charAt(i + 1))) {
                    i++;
                }
                sb.append("\\s+");
            } else if ("[](){}.*+?^$|\\".indexOf(c) >= 0) {
                // Escape regex special characters
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Finds a selector in a text block using flexible regex matching,
     * falling back to literal search. Returns the offset within the block, or -1.
     */
    private int findSelectorInBlock(String block, String literal, java.util.regex.Pattern flexPattern) {
        // Try literal search first (exact match, won't false-match inside comments)
        int pos = 0;
        while (pos < block.length()) {
            int found = block.indexOf(literal, pos);
            if (found < 0) break;
            int end = found + literal.length();
            if (end <= block.length()) {
                String after = block.substring(end).stripLeading();
                if (after.startsWith("{") || after.startsWith(",")) {
                    return found;
                }
            }
            pos = found + 1;
        }

        // Fall back to flexible regex (handles reformatted whitespace)
        if (flexPattern != null) {
            java.util.regex.Matcher m = flexPattern.matcher(block);
            while (m.find()) {
                int end = m.end();
                String after = block.substring(end).stripLeading();
                if (after.startsWith("{") || after.startsWith(",")) {
                    return m.start();
                }
            }
        }

        return -1;
    }

    /**
     * Calculates the actual match length at the given position (may differ from
     * the literal selector length if matched via flexible whitespace regex).
     */
    private int calcMatchLength(String block, int start, String literal, java.util.regex.Pattern flexPattern) {
        if (flexPattern != null) {
            java.util.regex.Matcher m = flexPattern.matcher(block);
            if (m.find(start) && m.start() == start) {
                return m.end() - m.start();
            }
        }
        return literal.length();
    }

    private void onTabContentChanged(TabInfo tab) {
        if (!tab.dirty) {
            tab.dirty = true;
            // Mark tab title with asterisk
            for (int i = 0; i < editorTabs.getTabCount(); i++) {
                if (editorTabs.getComponentAt(i) == tab.tabComponent) {
                    String title = editorTabs.getTitleAt(i);
                    if (!title.startsWith("*")) {
                        editorTabs.setTitleAt(i, "*" + title);
                    }
                    break;
                }
            }
        }
        // Only live-reload preview for HTML changes, not CSS
        if (tab.file != null && tab.file.equals(htmlFile)) {
            updatePreview();
        }
    }

    // =========================================================================
    // File Operations
    // =========================================================================

    private void newFile() {
        new EditorWindow();
    }

    private void openFile() {
        FileDialog fd = new FileDialog(frame, "Open HTML File", FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".html") || lower.endsWith(".htm");
        });
        fd.setVisible(true);
        if (fd.getFile() != null) {
            File file = new File(fd.getDirectory(), fd.getFile());
            // If this window already has an HTML file open, open in a new window
            if (htmlFile != null) {
                openFileInWindow(file);
            } else {
                openHtmlFile(file);
            }
        }
    }

    /**
     * Opens an HTML file, parses it for CSS references, builds the file tree,
     * and opens the HTML in a tab.
     */
    void openHtmlFile(File file) {
        if (!file.exists()) return;
        this.htmlFile = file;
        frame.setTitle("LayoutLynx - " + file.getName());
        filePathLabel.setText(file.getAbsolutePath());

        // Clear existing tabs
        editorTabs.removeAll();
        openTabs.clear();

        // Build the file tree
        rootNode = new DefaultMutableTreeNode(new FileNodeData(file.getName(), file));
        treeModel.setRoot(rootNode);

        // Parse HTML for linked CSS files
        List<File> cssFiles = parseCSSReferences(file);

        // Add "Local" node first (most recently applied / highest specificity)
        DefaultMutableTreeNode localNode = new DefaultMutableTreeNode(new FileNodeData("Local (inline styles)", file));
        rootNode.add(localNode);

        // Add CSS files in reverse order (last linked = applied later = higher priority)
        for (int i = cssFiles.size() - 1; i >= 0; i--) {
            File css = cssFiles.get(i);
            DefaultMutableTreeNode cssNode = new DefaultMutableTreeNode(new FileNodeData(css.getName(), css));
            rootNode.add(cssNode);
            // Recursively add @import children
            addCssImportNodes(cssNode, css, new java.util.HashSet<>());
        }

        treeModel.reload();
        fileTree.expandRow(0);

        // Open the HTML file in a tab
        openFileInTab(file);

        // Open each CSS file and its @imports in tabs
        java.util.Set<String> openedCss = new java.util.HashSet<>();
        for (File css : cssFiles) {
            openCssAndImports(css, openedCss);
        }

        updatePreview();
    }

    /**
     * Recursively opens a CSS file and all its @import dependencies in editor tabs.
     */
    private void openCssAndImports(File cssFile, java.util.Set<String> opened) {
        String path;
        try { path = cssFile.getCanonicalPath(); } catch (IOException e) { path = cssFile.getAbsolutePath(); }
        if (!opened.add(path)) return;
        openFileInTab(cssFile);
        for (File imported : parseCssImports(cssFile)) {
            openCssAndImports(imported, opened);
        }
    }

    /**
     * Parses an HTML file to find referenced CSS files (link elements with rel="stylesheet").
     */
    private List<File> parseCSSReferences(File htmlFile) {
        List<File> cssFiles = new ArrayList<>();
        try {
            String content = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
            // Match <link ... href="..." ... rel="stylesheet" ...> or rel before href
            Pattern pattern = Pattern.compile(
                "<link[^>]*\\shref=[\"']([^\"']+)[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String fullTag = matcher.group(0);
                if (fullTag.toLowerCase().contains("stylesheet") ||
                    fullTag.toLowerCase().contains("text/css")) {
                    String href = matcher.group(1);
                    // Resolve relative to HTML file's directory
                    File cssFile = new File(htmlFile.getParentFile(), href);
                    if (cssFile.exists()) {
                        cssFiles.add(cssFile);
                    }
                }
            }
        } catch (IOException e) {
            // Best effort
        }
        return cssFiles;
    }

    /**
     * Recursively adds @import references from a CSS file as child nodes in the tree.
     * Uses a visited set to prevent infinite loops from circular imports.
     */
    private void addCssImportNodes(DefaultMutableTreeNode parentNode, File cssFile, java.util.Set<String> visited) {
        String canonicalPath;
        try {
            canonicalPath = cssFile.getCanonicalPath();
        } catch (IOException e) {
            canonicalPath = cssFile.getAbsolutePath();
        }
        if (!visited.add(canonicalPath)) return; // already visited, prevent cycles

        List<File> imports = parseCssImports(cssFile);
        // Reverse order: last @import = applied later = higher priority = shown first
        for (int i = imports.size() - 1; i >= 0; i--) {
            File imported = imports.get(i);
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new FileNodeData(imported.getName(), imported));
            parentNode.add(childNode);
            addCssImportNodes(childNode, imported, visited);
        }
    }

    /**
     * Parses a CSS file to find @import rules and returns the referenced files.
     * Supports: @import url("file.css"), @import url('file.css'), @import "file.css", @import 'file.css'
     */
    private List<File> parseCssImports(File cssFile) {
        List<File> imports = new ArrayList<>();
        try {
            String content = Files.readString(cssFile.toPath(), StandardCharsets.UTF_8);
            // Match @import url("...") or @import url('...') or @import "..." or @import '...'
            Pattern pattern = Pattern.compile(
                "@import\\s+(?:url\\s*\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)|[\"']([^\"']+)[\"'])",
                Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String path = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (path != null && !path.startsWith("http://") && !path.startsWith("https://")) {
                    File importedFile = new File(cssFile.getParentFile(), path);
                    if (importedFile.exists()) {
                        imports.add(importedFile);
                    }
                }
            }
        } catch (IOException e) {
            // Best effort
        }
        return imports;
    }

    private void saveFile() {
        TabInfo tab = getActiveTab();
        if (tab == null) return;
        if (tab.file == null || !tab.file.exists()) {
            saveFileAs();
            return;
        }
        writeFile(tab, tab.file);
    }

    private void saveFileAs() {
        TabInfo tab = getActiveTab();
        if (tab == null) return;
        FileDialog fd = new FileDialog(frame, "Save As", FileDialog.SAVE);
        fd.setVisible(true);
        if (fd.getFile() != null) {
            File file = new File(fd.getDirectory(), fd.getFile());
            writeFile(tab, file);
            tab.file = file;
            // Update tab title
            for (int i = 0; i < editorTabs.getTabCount(); i++) {
                if (editorTabs.getComponentAt(i) == tab.tabComponent) {
                    editorTabs.setTitleAt(i, file.getName());
                    break;
                }
            }
        }
    }

    private void writeFile(TabInfo tab, File file) {
        try {
            String content = tab.textArea.getText();
            if (tab.windowsLineEndings) {
                content = content.replace("\n", "\r\n");
            }
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            tab.dirty = false;
            tab.lastModifiedOnDisk = file.lastModified();
            // Remove asterisk from tab title
            for (int i = 0; i < editorTabs.getTabCount(); i++) {
                if (editorTabs.getComponentAt(i) == tab.tabComponent) {
                    String title = editorTabs.getTitleAt(i);
                    if (title.startsWith("*")) {
                        editorTabs.setTitleAt(i, title.substring(1));
                    }
                    break;
                }
            }
            // Force preview reload when saving CSS files
            String name = file.getName().toLowerCase();
            if (name.endsWith(".css")) {
                forcePreviewReload();
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Error saving file: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    boolean confirmClose() {
        for (TabInfo tab : openTabs.values()) {
            if (tab.dirty) {
                String name = tab.file != null ? tab.file.getName() : "Untitled";
                int result = JOptionPane.showConfirmDialog(frame,
                    "Save changes to " + name + "?",
                    "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    if (tab.file != null) writeFile(tab, tab.file);
                    else saveFileAs();
                } else if (result == JOptionPane.CANCEL_OPTION) {
                    return false;
                }
            }
        }
        return true;
    }

    // =========================================================================
    // Preview
    // =========================================================================

    private void updatePreview() {
        if (!previewVisible || htmlFile == null) return;
        try {
            TabInfo htmlTab = openTabs.get(htmlFile);
            String content = htmlTab != null ? htmlTab.textArea.getText()
                : Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
            previewPanel.updateContent(htmlFile, content);
        } catch (IOException e) {
            TabInfo htmlTab = openTabs.get(htmlFile);
            if (htmlTab != null) {
                previewPanel.updateContent(htmlFile, htmlTab.textArea.getText());
            }
        }
    }

    /**
     * Forces a full preview reload with cache-busting on CSS references.
     * Called after saving a CSS file to ensure the preview picks up the new styles.
     */
    private void forcePreviewReload() {
        if (!previewVisible || htmlFile == null) return;
        try {
            TabInfo htmlTab = openTabs.get(htmlFile);
            String content = htmlTab != null ? htmlTab.textArea.getText()
                : Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
            String cacheBust = "?_t=" + System.currentTimeMillis();
            content = content.replaceAll("(\\.css)([\"'])", "$1" + cacheBust + "$2");
            previewPanel.updateContent(htmlFile, content);
        } catch (IOException e) {
            TabInfo htmlTab = openTabs.get(htmlFile);
            if (htmlTab != null) {
                String content = htmlTab.textArea.getText();
                String cacheBust = "?_t=" + System.currentTimeMillis();
                content = content.replaceAll("(\\.css)([\"'])", "$1" + cacheBust + "$2");
                previewPanel.updateContent(htmlFile, content);
            }
        }
    }

    // =========================================================================
    // Window State
    // =========================================================================

    private void saveWindowState() {
        preferences.setWindowWidth(frame.getWidth());
        preferences.setWindowHeight(frame.getHeight());
        if (previewVisible) {
            preferences.setEditorPreviewDivider(editorPreviewSplit.getDividerLocation());
        } else if (lastPreviewDivider > 0) {
            preferences.setEditorPreviewDivider(lastPreviewDivider);
        }
        if (aiVisible) {
            preferences.setMainDivider(mainSplit.getDividerLocation());
        } else if (lastAiDivider > 0) {
            preferences.setMainDivider(lastAiDivider);
        }
        preferences.setPreviewVisible(previewVisible);
        preferences.setAiVisible(aiVisible);
        preferences.save();
    }

    // =========================================================================
    // Dialogs
    // =========================================================================

    void showAboutDialog() {
        AboutDialog.show(frame, preferences);
    }

    void showPreferencesDialog() {
        PreferencesDialog dialog = new PreferencesDialog(frame, preferences);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            dialog.applyTo(preferences);
            preferences.save();
            applyPreferences();
        }
    }

    void showLicenseDialog() {
        LicenseDialog.show(frame, preferences);
    }

    private void showFindDialog() {
        RSyntaxTextArea textArea = getActiveTextArea();
        if (textArea == null) return;
        if (findDialog == null) {
            findDialog = new FindDialog(frame, textArea);
        } else {
            findDialog.setTextArea(textArea);
        }
        findDialog.setVisible(true);
    }

    private void showReplaceDialog() {
        RSyntaxTextArea textArea = getActiveTextArea();
        if (textArea == null) return;
        if (replaceDialog == null) {
            replaceDialog = new ReplaceDialog(frame, textArea);
        } else {
            replaceDialog.setTextArea(textArea);
        }
        replaceDialog.setVisible(true);
    }

    private void showPageSetup() {
        // TODO: Implement page setup
    }

    private void printDocument() {
        TabInfo tab = getActiveTab();
        if (tab == null) return;
        try {
            tab.textArea.print();
        } catch (java.awt.print.PrinterException ex) {
            JOptionPane.showMessageDialog(frame, "Print error: " + ex.getMessage(),
                "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exitApplication() {
        for (EditorWindow instance : new ArrayList<>(openInstances)) {
            if (!instance.confirmClose()) return;
        }
        System.exit(0);
    }

    private void applyPreferences() {
        boolean dark = preferences.isDarkMode();

        // Apply theme to editor text areas, scrollpanes, and minimaps
        for (TabInfo tab : openTabs.values()) {
            tab.textArea.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
            tab.textArea.setTabSize(preferences.getTabSize());
            tab.textArea.setTabsEmulated(!preferences.isUseTabs());
            tab.textArea.setSelectionColor(preferences.getHighlightColorObj());
            applyEditorTheme(tab.textArea, dark);

            // Theme the scrollpane gutter
            if (tab.scrollPane != null) {
                tab.scrollPane.setBackground(dark ? new Color(50, 50, 50) : Color.WHITE);
                tab.scrollPane.getGutter().setBackground(dark ? new Color(50, 50, 50) : new Color(240, 240, 240));
                tab.scrollPane.getGutter().setLineNumberColor(dark ? new Color(130, 130, 130) : Color.GRAY);
                tab.scrollPane.getGutter().setBorderColor(dark ? new Color(60, 60, 60) : new Color(200, 200, 200));
            }

            // Theme minimap and color swatch panels within the tab component
            if (tab.tabComponent instanceof JPanel) {
                for (Component c : ((JPanel) tab.tabComponent).getComponents()) {
                    if (c instanceof MinimapPanel) {
                        c.setBackground(dark ? new Color(30, 30, 30) : new Color(240, 240, 240));
                        c.repaint();
                    } else if (c instanceof ColorSwatchPanel) {
                        c.setBackground(dark ? new Color(50, 50, 50) : new Color(240, 240, 240));
                    }
                }
            }
        }

        // Apply theme to UI chrome
        Color bgColor = dark ? new Color(43, 43, 43) : UIManager.getColor("Panel.background");
        Color fgColor = dark ? new Color(187, 187, 187) : UIManager.getColor("Panel.foreground");
        Color darkBg = new Color(43, 43, 43);
        Color darkBgAlt = new Color(50, 50, 50);
        Color darkBorder = new Color(60, 60, 60);

        // Set global UI defaults for scrollbars and controls in dark mode
        if (dark) {
            UIManager.put("ScrollBar.thumb", new Color(80, 80, 80));
            UIManager.put("ScrollBar.track", darkBg);
            UIManager.put("ScrollBar.thumbDarkShadow", darkBorder);
            UIManager.put("ScrollBar.thumbHighlight", new Color(90, 90, 90));
            UIManager.put("ScrollBar.thumbShadow", new Color(60, 60, 60));
            UIManager.put("SplitPane.background", darkBg);
            UIManager.put("TabbedPane.background", darkBg);
            UIManager.put("TabbedPane.foreground", fgColor);
            UIManager.put("Panel.background", darkBg);
            UIManager.put("Panel.foreground", fgColor);
        } else {
            // Reset to system defaults
            UIManager.put("ScrollBar.thumb", null);
            UIManager.put("ScrollBar.track", null);
            UIManager.put("ScrollBar.thumbDarkShadow", null);
            UIManager.put("ScrollBar.thumbHighlight", null);
            UIManager.put("ScrollBar.thumbShadow", null);
            UIManager.put("SplitPane.background", null);
            UIManager.put("TabbedPane.background", null);
            UIManager.put("TabbedPane.foreground", null);
            UIManager.put("Panel.background", null);
            UIManager.put("Panel.foreground", null);
        }

        // Frame and content pane
        frame.getContentPane().setBackground(bgColor);

        // Toolbar
        Component toolbar = ((BorderLayout) frame.getContentPane().getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (toolbar != null) {
            toolbar.setBackground(bgColor);
            toolbar.setForeground(fgColor);
            if (toolbar instanceof JPanel) {
                for (Component c : ((JPanel) toolbar).getComponents()) {
                    c.setBackground(bgColor);
                    c.setForeground(fgColor);
                    if (c instanceof JPanel) {
                        for (Component cc : ((JPanel) c).getComponents()) {
                            if (cc instanceof JToggleButton) {
                                // Don't override toggle button colors (they have active state)
                            } else {
                                cc.setForeground(fgColor);
                            }
                        }
                    }
                }
            }
        }

        // File path label
        if (filePathLabel != null) {
            filePathLabel.setForeground(fgColor);
        }

        // File tree
        if (fileTree != null) {
            fileTree.setBackground(dark ? darkBgAlt : Color.WHITE);
            fileTree.setForeground(fgColor);
            // Update tree cell renderer colors
            javax.swing.tree.DefaultTreeCellRenderer renderer =
                (javax.swing.tree.DefaultTreeCellRenderer) fileTree.getCellRenderer();
            renderer.setBackgroundNonSelectionColor(dark ? darkBgAlt : Color.WHITE);
            renderer.setTextNonSelectionColor(fgColor);
            renderer.setBackgroundSelectionColor(dark ? new Color(70, 70, 100) : UIManager.getColor("Tree.selectionBackground"));
            renderer.setTextSelectionColor(dark ? Color.WHITE : UIManager.getColor("Tree.selectionForeground"));
            fileTree.getParent().setBackground(dark ? darkBgAlt : Color.WHITE);
        }

        // Tabbed pane
        if (editorTabs != null) {
            editorTabs.setBackground(bgColor);
            editorTabs.setForeground(fgColor);
        }

        // Split panes
        if (treeEditorSplit != null) {
            treeEditorSplit.setBackground(bgColor);
            treeEditorSplit.setBorder(null);
        }
        if (editorPreviewSplit != null) {
            editorPreviewSplit.setBackground(bgColor);
            editorPreviewSplit.setBorder(null);
        }
        if (mainSplit != null) {
            mainSplit.setBackground(bgColor);
            mainSplit.setBorder(null);
        }

        // Preview panel
        if (previewPanel != null) {
            previewPanel.setBackground(bgColor);
            // Update breakpoint toolbar buttons
            for (Component c : previewPanel.getComponents()) {
                if (c instanceof JPanel) {
                    c.setBackground(bgColor);
                    c.setForeground(fgColor);
                    for (Component cc : ((JPanel) c).getComponents()) {
                        if (cc instanceof JLabel) cc.setForeground(fgColor);
                        if (cc instanceof JToggleButton) {
                            JToggleButton btn = (JToggleButton) cc;
                            btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
                            btn.setForeground(fgColor);
                            btn.setOpaque(true);
                            if (!btn.isSelected()) {
                                btn.setBackground(bgColor);
                            }
                        }
                    }
                }
            }
        }

        // AI chat panel
        if (aiChatPanel != null) {
            aiChatPanel.applyTheme(dark);
            aiChatPanel.updateFont();
        }

        // Update all scrollbars in the frame (this resets component UIs to system LAF)
        SwingUtilities.updateComponentTreeUI(frame);

        // Re-apply custom tree cell renderer (updateComponentTreeUI may reset it)
        if (fileTree != null) {
            fileTree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
                @Override
                public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                        boolean expanded, boolean leaf, int row, boolean hasFocus) {
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                    setIcon(getLeafIcon());
                    return this;
                }
            });
            // Re-apply dark mode colors to the new renderer
            javax.swing.tree.DefaultTreeCellRenderer renderer =
                (javax.swing.tree.DefaultTreeCellRenderer) fileTree.getCellRenderer();
            renderer.setBackgroundNonSelectionColor(dark ? darkBgAlt : Color.WHITE);
            renderer.setTextNonSelectionColor(fgColor);
            renderer.setBackgroundSelectionColor(dark ? new Color(70, 70, 100) : UIManager.getColor("Tree.selectionBackground"));
            renderer.setTextSelectionColor(dark ? Color.WHITE : UIManager.getColor("Tree.selectionForeground"));
        }

        // Re-apply BasicToggleButtonUI AFTER updateComponentTreeUI since it resets to Aqua
        Color btnHighlight = preferences.getButtonHighlightColorObj();
        if (hiddenCharsToggle != null) {
            hiddenCharsToggle.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            hiddenCharsToggle.setForeground(fgColor);
            if (hiddenCharsVisible) {
                hiddenCharsToggle.setBackground(btnHighlight);
                hiddenCharsToggle.setContentAreaFilled(true);
                hiddenCharsToggle.setOpaque(true);
            }
        }
        if (previewToggle != null) {
            previewToggle.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            previewToggle.setForeground(fgColor);
            if (previewVisible) {
                previewToggle.setBackground(btnHighlight);
                previewToggle.setContentAreaFilled(true);
                previewToggle.setOpaque(true);
            }
        }
        if (aiToggle != null) {
            aiToggle.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            aiToggle.setForeground(fgColor);
            if (aiVisible) {
                aiToggle.setBackground(btnHighlight);
                aiToggle.setContentAreaFilled(true);
                aiToggle.setOpaque(true);
            }
        }

        // Re-apply to responsive breakpoint buttons
        if (previewPanel != null) {
            Color selectedColor = preferences.getButtonHighlightColorObj();
            Color unselectedBg = dark ? new Color(60, 60, 60) : UIManager.getColor("Button.background");
            for (Component c : previewPanel.getComponents()) {
                if (c instanceof JPanel) {
                    for (Component cc : ((JPanel) c).getComponents()) {
                        if (cc instanceof JToggleButton) {
                            JToggleButton btn = (JToggleButton) cc;
                            btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
                            btn.setForeground(fgColor);
                            btn.setOpaque(true);
                            btn.setContentAreaFilled(true);
                            btn.setBackground(btn.isSelected() ? selectedColor : unselectedBg);
                            // Remove ALL existing item listeners (we'll re-add ours)
                            for (ItemListener il : btn.getItemListeners()) {
                                btn.removeItemListener(il);
                            }
                            // Use preferences reference so color is always current
                            btn.addItemListener(e -> {
                                Color sel = preferences.getButtonHighlightColorObj();
                                Color unsel = preferences.isDarkMode() ? new Color(60, 60, 60) : UIManager.getColor("Button.background");
                                btn.setBackground(btn.isSelected() ? sel : unsel);
                            });
                        }
                    }
                }
            }
        }

        // Re-apply to AI panel buttons
        if (aiChatPanel != null) {
            aiChatPanel.applyTheme(dark);
        }

        frame.repaint();
    }

    /**
     * Applies light or dark theme to an RSyntaxTextArea.
     */
    private void applyEditorTheme(RSyntaxTextArea textArea, boolean dark) {
        if (dark) {
            try {
                org.fife.ui.rsyntaxtextarea.Theme theme =
                    org.fife.ui.rsyntaxtextarea.Theme.load(
                        getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
                theme.apply(textArea);
            } catch (Exception e) {
                // Fallback: manually set dark colors
                textArea.setBackground(new Color(43, 43, 43));
                textArea.setForeground(new Color(187, 187, 187));
                textArea.setCurrentLineHighlightColor(new Color(50, 50, 50));
                textArea.setCaretColor(Color.WHITE);
            }
        } else {
            try {
                org.fife.ui.rsyntaxtextarea.Theme theme =
                    org.fife.ui.rsyntaxtextarea.Theme.load(
                        getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/default.xml"));
                theme.apply(textArea);
            } catch (Exception e) {
                // Fallback: standard light colors
                textArea.setBackground(Color.WHITE);
                textArea.setForeground(Color.BLACK);
                textArea.setCurrentLineHighlightColor(new Color(232, 242, 254));
                textArea.setCaretColor(Color.BLACK);
            }
        }
        // Re-apply user's font after theme (theme may override it)
        textArea.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
    }

    private void convertTabsToSpaces() {
        TabInfo tab = getActiveTab();
        if (tab == null) return;
        int tabSize = preferences.getTabSize();
        String spaces = " ".repeat(tabSize);
        String text = tab.textArea.getText();
        String converted = text.replace("\t", spaces);
        if (!converted.equals(text)) {
            int caret = tab.textArea.getCaretPosition();
            tab.textArea.setText(converted);
            tab.textArea.setCaretPosition(Math.min(caret, converted.length()));
        }
    }

    private void convertSpacesToTabs() {
        TabInfo tab = getActiveTab();
        if (tab == null) return;
        int tabSize = preferences.getTabSize();
        String spaces = " ".repeat(tabSize);
        String text = tab.textArea.getText();
        // Only convert leading spaces on each line to preserve alignment intent
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            int i = 0;
            while (i + tabSize <= line.length() && line.substring(i, i + tabSize).equals(spaces)) {
                sb.append('\t');
                i += tabSize;
            }
            sb.append(line.substring(i));
            sb.append('\n');
        }
        // Remove trailing newline added by split processing
        if (!text.endsWith("\n") && sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        String converted = sb.toString();
        if (!converted.equals(text)) {
            int caret = tab.textArea.getCaretPosition();
            tab.textArea.setText(converted);
            tab.textArea.setCaretPosition(Math.min(caret, converted.length()));
        }
    }

    private void tidyDocument() {
        TabInfo tab = getActiveTab();
        if (tab == null) return;

        String input = tab.textArea.getText();
        String syntax = tab.textArea.getSyntaxEditingStyle();
        boolean isCss = SyntaxConstants.SYNTAX_STYLE_CSS.equals(syntax);

        // Show options dialog
        TidyOptionsDialog options = new TidyOptionsDialog(frame, preferences, isCss);
        options.setVisible(true);
        if (!options.isConfirmed()) return;

        // Format using js-beautify via GraalJS
        String formatted;
        if (isCss) {
            formatted = JsBeautifyFormatter.formatCss(input, options.getCssOptionsJson());
        } else {
            formatted = JsBeautifyFormatter.formatHtml(input, options.getHtmlOptionsJson());
        }

        if (formatted != null && !formatted.equals(input)) {
            int caret = tab.textArea.getCaretPosition();
            tab.textArea.setText(formatted);
            tab.textArea.setCaretPosition(Math.min(caret, formatted.length()));
        }
    }

    // =========================================================================
    // Inner Classes
    // =========================================================================

    /** Data stored in tree nodes. */
    static class FileNodeData {
        final String displayName;
        final File file;

        FileNodeData(String displayName, File file) {
            this.displayName = displayName;
            this.file = file;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
