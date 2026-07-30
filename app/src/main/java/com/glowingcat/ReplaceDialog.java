/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * ReplaceDialog.java
 *
 * Provides a Find and Replace dialog for the LayoutLynx editor.
 * Extends {@link FindDialog} to add a replacement text field and replace
 * operations (Replace, Replace and Find, Replace All) while reusing the
 * search logic and option checkboxes from the parent class.
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A Replace dialog that extends {@link FindDialog}, adding a replacement text field
 * and buttons for Replace, Replace and Find, and Replace All operations.
 * <p>
 * Inherits all search options (Match Case, Wrap Around, Search Backwards,
 * Find in Selection) from FindDialog.
 */
public class ReplaceDialog extends FindDialog {

    /** Text field where the user enters the replacement text. */
    private JTextField replaceField;

    /**
     * Creates a Replace dialog attached to the given owner frame and text area.
     *
     * @param owner    the parent frame
     * @param textArea the text area to perform replacements on
     */
    public ReplaceDialog(JFrame owner, JTextArea textArea) {
        super(owner, textArea, "Replace");
    }

    /**
     * Creates the top panel with both a "Find:" and a "Replace:" text field,
     * arranged in a two-row grid layout.
     *
     * @return the configured top panel with find and replace fields
     */
    @Override
    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Find label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        topPanel.add(new JLabel("Find:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        topPanel.add(searchField = new JTextField(24), gbc);

        // Replace label and field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        topPanel.add(new JLabel("Replace:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        replaceField = new JTextField(24);
        topPanel.add(replaceField, gbc);

        return topPanel;
    }

    /**
     * Creates the button panel with Find, Replace, Replace and Find, and Replace All buttons.
     *
     * @return the configured button panel
     */
    @Override
    protected JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        JButton findBtn = createButton("Find");
        JButton replaceBtn = createButton("Replace");
        JButton replaceAndFindBtn = createButton("Replace and Find");
        JButton replaceAllBtn = createButton("Replace All");

        findBtn.addActionListener(e -> findNext());
        replaceBtn.addActionListener(e -> replace());
        replaceAndFindBtn.addActionListener(e -> replaceAndFind());
        replaceAllBtn.addActionListener(e -> replaceAll());

        buttonPanel.add(findBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(replaceBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(replaceAndFindBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(replaceAllBtn);

        return buttonPanel;
    }

    /**
     * Replaces the currently selected text if it matches the search text.
     * Respects the Match Case option. If "Find in selection" is active,
     * adjusts the remembered selection end boundary to account for the
     * length difference between the search and replacement text.
     *
     * @return {@code true} if a replacement was made, {@code false} otherwise
     */
    private boolean replace() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return false;

        String selectedText = textArea.getSelectedText();
        if (selectedText == null) return false;

        boolean matchCase = matchCaseBox.isSelected();
        boolean useRegex = regexBox.isSelected();
        String replaceText = replaceField.getText();

        // Process escape sequences if enabled (not applicable in regex mode)
        if (!useRegex && escapesBox.isSelected()) {
            searchText = processEscapes(searchText);
            replaceText = processEscapes(replaceText);
        }

        if (useRegex) {
            try {
                int flags = Pattern.MULTILINE | (matchCase ? 0 : Pattern.CASE_INSENSITIVE);
                Pattern pattern = Pattern.compile(searchText, flags);
                Matcher matcher = pattern.matcher(selectedText);
                if (matcher.matches()) {
                    // Use replaceFirst to support capture group references ($1, $2, etc.)
                    String replacement = matcher.replaceFirst(replaceText);
                    textArea.replaceSelection(replacement);
                    if (findInSelectionBox.isSelected() && selectionStart >= 0) {
                        int lengthDiff = replacement.length() - selectedText.length();
                        selectionEnd += lengthDiff;
                    }
                    return true;
                }
            } catch (PatternSyntaxException ex) {
                JOptionPane.showMessageDialog(this, "Invalid regular expression: " + ex.getMessage(),
                        "Regex Error", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        } else {
            boolean matches;
            if (matchCase) {
                matches = selectedText.equals(searchText);
            } else {
                matches = selectedText.equalsIgnoreCase(searchText);
            }

            if (matches) {
                textArea.replaceSelection(replaceText);
                if (findInSelectionBox.isSelected() && selectionStart >= 0) {
                    int lengthDiff = replaceText.length() - searchText.length();
                    selectionEnd += lengthDiff;
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Replaces the current match (if selected and matching) then finds the
     * next occurrence of the search text. This allows iterative replace-then-advance
     * workflows.
     */
    private void replaceAndFind() {
        replace();
        findNext();
    }

    /**
     * Replaces all occurrences of the search text within the current search region
     * with the replacement text. Rebuilds the affected region of the document in
     * a single operation and updates the remembered selection bounds if applicable.
     * Displays a count of replacements made.
     */
    private void replaceAll() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        String replaceText = replaceField.getText();
        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();
        boolean useRegex = regexBox.isSelected();

        // Process escape sequences if enabled (not applicable in regex mode)
        if (!useRegex && escapesBox.isSelected()) {
            searchText = processEscapes(searchText);
            replaceText = processEscapes(replaceText);
        }

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

            Matcher matcher = pattern.matcher(searchIn);
            int count = 0;
            // Count matches first
            while (matcher.find()) count++;
            if (count == 0) {
                JOptionPane.showMessageDialog(this, "Text not found.",
                        "Replace All", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Perform replacement (supports $1, $2 capture group references)
            matcher.reset();
            String result = matcher.replaceAll(replaceText);

            String newContent = content.substring(0, regionStart) + result + content.substring(regionEnd);
            textArea.setText(newContent);
            textArea.setCaretPosition(regionStart);

            if (findInSelectionBox.isSelected() && selectionStart >= 0) {
                selectionEnd = regionStart + result.length();
            }

            JOptionPane.showMessageDialog(this,
                    count + " replacement" + (count != 1 ? "s" : "") + " made.",
                    "Replace All", JOptionPane.INFORMATION_MESSAGE);
        } else {
            String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
            String compareText = matchCase ? searchText : searchText.toLowerCase();

            StringBuilder result = new StringBuilder();
            int count = 0;
            int idx = 0;
            while (true) {
                int found = compareIn.indexOf(compareText, idx);
                if (found < 0) {
                    result.append(searchIn.substring(idx));
                    break;
                }
                result.append(searchIn, idx, found);
                result.append(replaceText);
                count++;
                idx = found + searchText.length();
            }

            if (count == 0) {
                JOptionPane.showMessageDialog(this, "Text not found.",
                        "Replace All", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String newContent = content.substring(0, regionStart) + result + content.substring(regionEnd);
            textArea.setText(newContent);
            textArea.setCaretPosition(regionStart);

            if (findInSelectionBox.isSelected() && selectionStart >= 0) {
                selectionEnd = regionStart + result.length();
            }

            JOptionPane.showMessageDialog(this,
                    count + " replacement" + (count != 1 ? "s" : "") + " made.",
                    "Replace All", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
