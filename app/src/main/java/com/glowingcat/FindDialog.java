/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * FindDialog.java
 *
 * Provides a non-modal Find dialog for the LayoutLynx editor.
 * Supports Find Next, Find All (with a clickable results window), and Count
 * operations with options for case sensitivity, wrap-around, search direction,
 * and searching within a remembered selection.
 * <p>
 * Designed as a base class that can be extended (see {@link ReplaceDialog})
 * via template methods for the top panel, options panel, and button panel.
 */
package com.glowingcat;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A non-modal Find dialog with Find Next, Find All, Count,
 * and search option checkboxes.
 * <p>
 * Subclasses can override {@link #createTopPanel()}, {@link #createOptionsPanel()},
 * and {@link #createButtonPanel()} to customize the dialog layout while reusing
 * the core search logic.
 */
public class FindDialog extends JDialog {

    /** The parent frame, used for positioning and bringing to front. */
    protected final JFrame ownerFrame;

    /** The text area being searched. */
    protected JTextArea textArea;

    /** Text field where the user enters the search query. */
    protected JTextField searchField;

    /** Checkbox to restrict search to the remembered selection range. */
    protected JCheckBox findInSelectionBox;

    /** Checkbox to reverse the search direction (search backwards from caret). */
    protected JCheckBox searchBackwardsBox;

    /** Checkbox for case-sensitive matching. */
    protected JCheckBox matchCaseBox;

    /** Checkbox to wrap around to the start/end of the search region. */
    protected JCheckBox wrapAroundBox;

    /** Checkbox to enable regular expression matching. */
    protected JCheckBox regexBox;

    /** Checkbox to interpret escape sequences (\t, \n, \r, \\, &#92;uXXXX) in find/replace fields. */
    protected JCheckBox escapesBox;

    /** Start offset of the remembered selection for "Find in selection". */
    protected int selectionStart = -1;

    /** End offset of the remembered selection for "Find in selection". */
    protected int selectionEnd = -1;

    /**
     * Creates a Find dialog with the default title "Find".
     *
     * @param owner    the parent frame
     * @param textArea the text area to search within
     */
    public FindDialog(JFrame owner, JTextArea textArea) {
        this(owner, textArea, "Find");
    }

    /**
     * Creates a Find dialog with a custom title. This constructor is used by subclasses
     * to provide their own dialog title (e.g., "Replace").
     *
     * @param owner    the parent frame
     * @param textArea the text area to search within
     * @param title    the dialog window title
     */
    protected FindDialog(JFrame owner, JTextArea textArea, String title) {
        super(owner, title, false);
        this.ownerFrame = owner;
        this.textArea = textArea;

        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: search field(s)
        add(createTopPanel(), BorderLayout.NORTH);

        // Left: checkboxes
        add(createOptionsPanel(), BorderLayout.CENTER);

        // Right: buttons
        add(createButtonPanel(), BorderLayout.EAST);

        // Enter key triggers Find Next
        searchField.addActionListener(e -> findNext());

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /**
     * Updates the text area this dialog searches within.
     *
     * @param textArea the new text area to search
     */
    public void setTextArea(JTextArea textArea) {
        this.textArea = textArea;
    }

    /**
     * Creates the top panel containing the search text field.
     * Subclasses can override this to add additional fields (e.g., a replace field).
     *
     * @return the configured top panel
     */
    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.add(new JLabel("Find:"), BorderLayout.WEST);
        topPanel.add(searchField = new JTextField(24), BorderLayout.CENTER);
        return topPanel;
    }

    /**
     * Creates the options panel with search modifier checkboxes:
     * Find in Selection, Search Backwards, Match Case, and Wrap Around.
     * <p>
     * When "Find in selection" is checked, the current editor selection boundaries
     * are captured and remembered for subsequent searches.
     *
     * @return the configured options panel
     */
    protected JPanel createOptionsPanel() {
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));

        findInSelectionBox = new JCheckBox("Find in selection");
        searchBackwardsBox = new JCheckBox("Search Backwards");
        matchCaseBox = new JCheckBox("Match Case");
        wrapAroundBox = new JCheckBox("Wrap Around");
        wrapAroundBox.setSelected(true);
        regexBox = new JCheckBox("Regular Expression");
        escapesBox = new JCheckBox("Interpret Escapes");

        // When "Find in selection" is checked, capture the current selection
        findInSelectionBox.addActionListener(e -> {
            if (findInSelectionBox.isSelected()) {
                int start = textArea.getSelectionStart();
                int end = textArea.getSelectionEnd();
                if (start != end) {
                    selectionStart = start;
                    selectionEnd = end;
                } else {
                    findInSelectionBox.setSelected(false);
                    JOptionPane.showMessageDialog(this,
                            "Please select text in the editor first.",
                            "Find in Selection", JOptionPane.WARNING_MESSAGE);
                }
            } else {
                selectionStart = -1;
                selectionEnd = -1;
            }
        });

        optionsPanel.add(findInSelectionBox);
        optionsPanel.add(searchBackwardsBox);
        optionsPanel.add(matchCaseBox);
        optionsPanel.add(wrapAroundBox);
        optionsPanel.add(regexBox);
        optionsPanel.add(escapesBox);
        return optionsPanel;
    }

    /**
     * Creates the button panel with Find Next, Find All, and Count buttons.
     * Subclasses can override this to provide different buttons (e.g., Replace).
     *
     * @return the configured button panel
     */
    protected JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        JButton findNextBtn = createButton("Find Next");
        JButton findAllBtn = createButton("Find All");
        JButton countBtn = createButton("Count");

        findNextBtn.addActionListener(e -> findNext());
        findAllBtn.addActionListener(e -> findAll());
        countBtn.addActionListener(e -> count());

        buttonPanel.add(findNextBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(findAllBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(countBtn);
        return buttonPanel;
    }

    /**
     * Creates a button with a standardized size for consistent dialog layout.
     *
     * @param text the button label
     * @return the configured button
     */
    protected JButton createButton(String text) {
        JButton btn = new JButton(text);
        Dimension btnSize = new Dimension(130, 28);
        btn.setMaximumSize(btnSize);
        btn.setPreferredSize(btnSize);
        return btn;
    }

    /**
     * Populates the bounds array with the start and end offsets of the search region.
     * If "Find in selection" is active with a valid remembered range, uses that range;
     * otherwise uses the full document.
     *
     * @param bounds a two-element array where bounds[0] receives the start offset
     *              and bounds[1] receives the end offset
     */
    protected void getSearchBounds(int[] bounds) {
        if (findInSelectionBox.isSelected() && selectionStart >= 0 && selectionEnd > selectionStart) {
            bounds[0] = selectionStart;
            bounds[1] = selectionEnd;
        } else {
            bounds[0] = 0;
            bounds[1] = textArea.getText().length();
        }
    }

    /**
     * Finds and selects the next occurrence of the search text from the current
     * caret position. Respects all search options (case, direction, wrap, selection).
     * Displays a message if no match is found.
     */
    public void findNext() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        // Process escape sequences if enabled (not applicable in regex mode)
        boolean useRegex = regexBox.isSelected();
        if (!useRegex && escapesBox.isSelected()) {
            searchText = processEscapes(searchText);
        }

        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();
        boolean backwards = searchBackwardsBox.isSelected();
        boolean wrapAround = wrapAroundBox.isSelected();

        int[] bounds = new int[2];
        getSearchBounds(bounds);
        int regionStart = bounds[0];
        int regionEnd = bounds[1];

        String searchIn = content.substring(regionStart, regionEnd);

        if (useRegex) {
            Pattern pattern;
            try {
                int flags = Pattern.MULTILINE | (matchCase ? 0 : Pattern.CASE_INSENSITIVE);
                pattern = Pattern.compile(searchText, flags);
            } catch (PatternSyntaxException ex) {
                JOptionPane.showMessageDialog(this, "Invalid regular expression: " + ex.getMessage(),
                        "Regex Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int fromIndex;
            if (backwards) {
                fromIndex = textArea.getSelectionStart() - regionStart;
                if (textArea.getSelectionStart() == textArea.getSelectionEnd()) {
                    fromIndex = textArea.getCaretPosition() - regionStart;
                }
            } else {
                fromIndex = textArea.getSelectionEnd() - regionStart;
                if (textArea.getSelectionStart() == textArea.getSelectionEnd()) {
                    fromIndex = textArea.getCaretPosition() - regionStart;
                }
            }
            fromIndex = Math.max(0, Math.min(fromIndex, searchIn.length()));

            Matcher matcher = pattern.matcher(searchIn);
            int matchStart = -1, matchEnd = -1;

            if (backwards) {
                // Find last match before fromIndex
                int lastStart = -1, lastEnd = -1;
                while (matcher.find()) {
                    if (matcher.start() < fromIndex) {
                        lastStart = matcher.start();
                        lastEnd = matcher.end();
                    } else {
                        break;
                    }
                }
                if (lastStart >= 0) {
                    matchStart = lastStart;
                    matchEnd = lastEnd;
                } else if (wrapAround) {
                    matcher.reset();
                    while (matcher.find()) {
                        lastStart = matcher.start();
                        lastEnd = matcher.end();
                    }
                    if (lastStart >= 0) {
                        matchStart = lastStart;
                        matchEnd = lastEnd;
                    }
                }
            } else {
                if (matcher.find(fromIndex)) {
                    matchStart = matcher.start();
                    matchEnd = matcher.end();
                } else if (wrapAround && matcher.find(0)) {
                    matchStart = matcher.start();
                    matchEnd = matcher.end();
                }
            }

            if (matchStart >= 0) {
                textArea.setSelectionStart(matchStart + regionStart);
                textArea.setSelectionEnd(matchEnd + regionStart);
                textArea.requestFocusInWindow();
            } else {
                JOptionPane.showMessageDialog(this, "Text not found.",
                        "Find", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            // Plain text search (original logic)
            String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
            String compareText = matchCase ? searchText : searchText.toLowerCase();

            int caretPos = textArea.getCaretPosition() - regionStart;
            int index;

            if (backwards) {
                int fromIndex = caretPos - 1;
                if (textArea.getSelectionStart() != textArea.getSelectionEnd()) {
                    fromIndex = textArea.getSelectionStart() - regionStart - 1;
                }
                fromIndex = Math.max(0, fromIndex);
                index = compareIn.lastIndexOf(compareText, fromIndex);
                if (index < 0 && wrapAround) {
                    index = compareIn.lastIndexOf(compareText);
                }
            } else {
                int fromIndex = caretPos;
                if (textArea.getSelectionStart() != textArea.getSelectionEnd()) {
                    fromIndex = textArea.getSelectionEnd() - regionStart;
                }
                fromIndex = Math.max(0, Math.min(fromIndex, compareIn.length()));
                index = compareIn.indexOf(compareText, fromIndex);
                if (index < 0 && wrapAround) {
                    index = compareIn.indexOf(compareText);
                }
            }

            if (index >= 0) {
                int start = index + regionStart;
                int end = start + searchText.length();
                textArea.setSelectionStart(start);
                textArea.setSelectionEnd(end);
                textArea.requestFocusInWindow();
            } else {
                JOptionPane.showMessageDialog(this, "Text not found.",
                        "Find", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * Finds all occurrences of the search text within the search region and
     * displays them in a new results window. Each matching line is shown with
     * its line number and the matched text highlighted in yellow.
     */
    protected void findAll() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();
        boolean useRegex = regexBox.isSelected();

        // Process escape sequences if enabled (not applicable in regex mode)
        if (!useRegex && escapesBox.isSelected()) {
            searchText = processEscapes(searchText);
        }

        int[] bounds = new int[2];
        getSearchBounds(bounds);
        int regionStart = bounds[0];
        int regionEnd = bounds[1];

        String searchIn = content.substring(regionStart, regionEnd);

        List<int[]> matches = new ArrayList<>();

        if (useRegex) {
            Pattern pattern;
            try {
                int flags = Pattern.MULTILINE | (matchCase ? 0 : Pattern.CASE_INSENSITIVE);
                pattern = Pattern.compile(searchText, flags);
            } catch (PatternSyntaxException ex) {
                JOptionPane.showMessageDialog(this, "Invalid regular expression: " + ex.getMessage(),
                        "Regex Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Matcher matcher = pattern.matcher(searchIn);
            while (matcher.find()) {
                matches.add(new int[]{matcher.start() + regionStart, matcher.end() + regionStart});
            }
        } else {
            String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
            String compareText = matchCase ? searchText : searchText.toLowerCase();
            int idx = 0;
            while ((idx = compareIn.indexOf(compareText, idx)) >= 0) {
                matches.add(new int[]{idx + regionStart, idx + regionStart + searchText.length()});
                idx += compareText.length();
            }
        }

        if (matches.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Text not found.",
                    "Find All", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        showFindAllResults(searchText, matches, content);
    }

    /**
     * Displays a results window containing all matching lines with highlighted
     * match text. Clicking a highlighted match selects the corresponding text
     * in the editor and brings the main window to the front.
     *
     * @param searchText the search query (used in the window title)
     * @param matches    list of [start, end] offset pairs for each match
     * @param content    the full document text (used for line extraction)
     */
    protected void showFindAllResults(String searchText, List<int[]> matches, String content) {
        JFrame resultsFrame = new JFrame("Find All Results - \"" + searchText + "\" (" + matches.size() + " matches)");
        resultsFrame.setSize(600, 400);

        JTextPane resultsPane = new JTextPane();
        resultsPane.setEditable(false);
        resultsPane.setFont(new Font("Monospaced", Font.PLAIN, 13));

        StyledDocument doc = resultsPane.getStyledDocument();

        Style normalStyle = doc.addStyle("normal", null);
        StyleConstants.setFontFamily(normalStyle, "Monospaced");
        StyleConstants.setFontSize(normalStyle, 13);

        Style lineNumStyle = doc.addStyle("lineNum", null);
        StyleConstants.setFontFamily(lineNumStyle, "Monospaced");
        StyleConstants.setFontSize(lineNumStyle, 13);
        StyleConstants.setForeground(lineNumStyle, new Color(100, 100, 100));

        Style highlightStyle = doc.addStyle("highlight", null);
        StyleConstants.setFontFamily(highlightStyle, "Monospaced");
        StyleConstants.setFontSize(highlightStyle, 13);
        StyleConstants.setBackground(highlightStyle, new Color(255, 255, 0));
        StyleConstants.setForeground(highlightStyle, Color.BLACK);

        String[] lines = content.split("\n", -1);
        int[] lineStartOffsets = new int[lines.length];
        lineStartOffsets[0] = 0;
        for (int i = 1; i < lines.length; i++) {
            lineStartOffsets[i] = lineStartOffsets[i - 1] + lines[i - 1].length() + 1;
        }

        List<int[]> clickableRanges = new ArrayList<>();

        Map<Integer, List<int[]>> matchesByLine = new LinkedHashMap<>();
        for (int[] match : matches) {
            int lineIdx = getLineForOffset(lineStartOffsets, match[0]);
            matchesByLine.computeIfAbsent(lineIdx, k -> new ArrayList<>()).add(match);
        }

        try {
            for (Map.Entry<Integer, List<int[]>> entry : matchesByLine.entrySet()) {
                int lineIdx = entry.getKey();
                List<int[]> lineMatches = entry.getValue();
                String line = lines[lineIdx];
                int lineStart = lineStartOffsets[lineIdx];

                String prefix = String.format("%4d: ", lineIdx + 1);
                doc.insertString(doc.getLength(), prefix, lineNumStyle);

                int pos = 0;
                for (int[] match : lineMatches) {
                    int matchStartInLine = match[0] - lineStart;
                    int matchEndInLine = match[1] - lineStart;

                    if (matchStartInLine > pos) {
                        doc.insertString(doc.getLength(), line.substring(pos, matchStartInLine), normalStyle);
                    }

                    int highlightStart = doc.getLength();
                    doc.insertString(doc.getLength(), line.substring(matchStartInLine, matchEndInLine), highlightStyle);
                    int highlightEnd = doc.getLength();

                    clickableRanges.add(new int[]{highlightStart, highlightEnd, match[0], match[1]});
                    pos = matchEndInLine;
                }

                if (pos < line.length()) {
                    doc.insertString(doc.getLength(), line.substring(pos), normalStyle);
                }
                doc.insertString(doc.getLength(), "\n", normalStyle);
            }
        } catch (BadLocationException ex) {
            // Should not happen
        }

        resultsPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int clickPos = resultsPane.viewToModel2D(e.getPoint());
                for (int[] range : clickableRanges) {
                    if (clickPos >= range[0] && clickPos < range[1]) {
                        textArea.setSelectionStart(range[2]);
                        textArea.setSelectionEnd(range[3]);
                        textArea.requestFocusInWindow();
                        ownerFrame.toFront();
                        break;
                    }
                }
            }
        });

        resultsPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int hoverPos = resultsPane.viewToModel2D(e.getPoint());
                boolean overMatch = false;
                for (int[] range : clickableRanges) {
                    if (hoverPos >= range[0] && hoverPos < range[1]) {
                        overMatch = true;
                        break;
                    }
                }
                resultsPane.setCursor(overMatch
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsPane);
        resultsFrame.add(scrollPane);
        resultsFrame.setLocationRelativeTo(ownerFrame);
        resultsFrame.setVisible(true);
    }

    /**
     * Determines which line a given character offset falls on using binary-style
     * reverse scan of the line start offsets array.
     *
     * @param lineStartOffsets array of character offsets where each line begins
     * @param offset           the character offset to locate
     * @return the zero-based line index
     */
    protected int getLineForOffset(int[] lineStartOffsets, int offset) {
        for (int i = lineStartOffsets.length - 1; i >= 0; i--) {
            if (offset >= lineStartOffsets[i]) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Counts and displays the number of occurrences of the search text within
     * the current search region. Shows the result in a message dialog.
     */
    protected void count() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();
        boolean useRegex = regexBox.isSelected();

        // Process escape sequences if enabled (not applicable in regex mode)
        if (!useRegex && escapesBox.isSelected()) {
            searchText = processEscapes(searchText);
        }

        int[] bounds = new int[2];
        getSearchBounds(bounds);
        int regionStart = bounds[0];
        int regionEnd = bounds[1];

        String searchIn = content.substring(regionStart, regionEnd);

        int count = 0;

        if (useRegex) {
            Pattern pattern;
            try {
                int flags = Pattern.MULTILINE | (matchCase ? 0 : Pattern.CASE_INSENSITIVE);
                pattern = Pattern.compile(searchText, flags);
            } catch (PatternSyntaxException ex) {
                JOptionPane.showMessageDialog(this, "Invalid regular expression: " + ex.getMessage(),
                        "Regex Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Matcher matcher = pattern.matcher(searchIn);
            while (matcher.find()) {
                count++;
            }
        } else {
            String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
            String compareText = matchCase ? searchText : searchText.toLowerCase();
            int idx = 0;
            while ((idx = compareIn.indexOf(compareText, idx)) >= 0) {
                count++;
                idx += compareText.length();
            }
        }

        JOptionPane.showMessageDialog(this,
                count + " match" + (count != 1 ? "es" : "") + " found.",
                "Count", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Processes escape sequences in the given string, replacing recognized
     * sequences with their corresponding characters.
     * <p>
     * Supported escapes:
     * <ul>
     *   <li>{@code \t} — tab</li>
     *   <li>{@code \n} — newline (line feed)</li>
     *   <li>{@code \r} — carriage return</li>
     *   <li>{@code \\} — literal backslash</li>
     *   <li>{@code \}uXXXX — Unicode code point (four hex digits)</li>
     * </ul>
     * Unrecognized escape sequences are left as literal text (backslash preserved).
     *
     * @param input the string potentially containing escape sequences
     * @return the string with recognized escapes replaced by their character equivalents
     */
    protected static String processEscapes(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case 't' -> { sb.append('\t'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'u' -> {
                        if (i + 5 < input.length()) {
                            String hex = input.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append('\\');
                            }
                        } else {
                            sb.append('\\');
                        }
                    }
                    default -> sb.append('\\');
                }
            } else {
                sb.append(input.charAt(i));
            }
        }
        return sb.toString();
    }
}
