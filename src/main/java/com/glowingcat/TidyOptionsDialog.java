/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;

/**
 * A modal dialog that presents js-beautify formatting options before tidying a document.
 * Shows different options depending on whether the document is HTML or CSS.
 */
public class TidyOptionsDialog extends JDialog {

    private boolean confirmed = false;

    // Common options
    private final JSpinner indentSpinner;
    private final JCheckBox useTabsBox;

    // HTML options
    private final JSpinner wrapLineLengthSpinner;
    private final JCheckBox preserveNewlinesBox;
    private final JSpinner maxPreserveNewlinesSpinner;
    private final JCheckBox indentInnerHtmlBox;
    private final JCheckBox indentHeadBodyBox;

    // CSS options
    private final JCheckBox selectorNewlineBox;
    private final JCheckBox newlineBetweenRulesBox;

    private final boolean isCss;
    private final Preferences prefs;

    public TidyOptionsDialog(JFrame owner, Preferences prefs, boolean isCss) {
        super(owner, "Tidy Options", true);
        this.isCss = isCss;
        this.prefs = prefs;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // Indentation section
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel header = new JLabel("Indentation");
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        panel.add(header, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0;
        panel.add(new JLabel("Indent size:"), gbc);
        gbc.gridx = 1;
        indentSpinner = new JSpinner(new SpinnerNumberModel(prefs.getTidyIndentSize(), 1, 8, 1));
        panel.add(indentSpinner, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        useTabsBox = new JCheckBox("Use tabs", prefs.isTidyUseTabs());
        panel.add(useTabsBox, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        panel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.NONE;

        if (isCss) {
            // CSS-specific options
            gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
            JLabel cssHeader = new JLabel("CSS Options");
            cssHeader.setFont(cssHeader.getFont().deriveFont(Font.BOLD));
            panel.add(cssHeader, gbc);

            gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
            selectorNewlineBox = new JCheckBox("Each selector on new line", prefs.isTidyCssSelectorNewline());
            panel.add(selectorNewlineBox, gbc);

            gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
            newlineBetweenRulesBox = new JCheckBox("Blank line between rules", prefs.isTidyCssNewlineBetweenRules());
            panel.add(newlineBetweenRulesBox, gbc);

            // Not shown for CSS
            wrapLineLengthSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 300, 10));
            preserveNewlinesBox = new JCheckBox();
            maxPreserveNewlinesSpinner = new JSpinner();
            indentInnerHtmlBox = new JCheckBox();
            indentHeadBodyBox = new JCheckBox();
        } else {
            // HTML-specific options
            gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
            JLabel htmlHeader = new JLabel("HTML Options");
            htmlHeader.setFont(htmlHeader.getFont().deriveFont(Font.BOLD));
            panel.add(htmlHeader, gbc);

            gbc.gridwidth = 1;
            gbc.gridy = ++row; gbc.gridx = 0;
            panel.add(new JLabel("Wrap line length (0=no wrap):"), gbc);
            gbc.gridx = 1;
            wrapLineLengthSpinner = new JSpinner(new SpinnerNumberModel(prefs.getTidyHtmlWrapLineLength(), 0, 300, 10));
            panel.add(wrapLineLengthSpinner, gbc);

            gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
            preserveNewlinesBox = new JCheckBox("Preserve existing newlines", prefs.isTidyHtmlPreserveNewlines());
            panel.add(preserveNewlinesBox, gbc);

            gbc.gridwidth = 1;
            gbc.gridy = ++row; gbc.gridx = 0;
            panel.add(new JLabel("Max consecutive blank lines:"), gbc);
            gbc.gridx = 1;
            maxPreserveNewlinesSpinner = new JSpinner(new SpinnerNumberModel(prefs.getTidyHtmlMaxPreserveNewlines(), 0, 10, 1));
            panel.add(maxPreserveNewlinesSpinner, gbc);

            gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
            indentInnerHtmlBox = new JCheckBox("Indent <head> and <body> contents", prefs.isTidyHtmlIndentInnerHtml());
            panel.add(indentInnerHtmlBox, gbc);

            gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
            indentHeadBodyBox = new JCheckBox("Indent <head> and <body> tags", prefs.isTidyHtmlIndentHeadBody());
            panel.add(indentHeadBodyBox, gbc);

            // Not shown for HTML
            selectorNewlineBox = new JCheckBox();
            newlineBetweenRulesBox = new JCheckBox();
        }

        // Buttons
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 6, 4, 6);
        gbc.anchor = GridBagConstraints.EAST;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton okBtn = new JButton("Tidy");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> { confirmed = true; saveToPreferences(); dispose(); });
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(cancelBtn);
        buttonPanel.add(okBtn);
        panel.add(buttonPanel, gbc);

        getRootPane().setDefaultButton(okBtn);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /**
     * Persists the current dialog settings back to the preferences file.
     */
    private void saveToPreferences() {
        prefs.setTidyIndentSize((Integer) indentSpinner.getValue());
        prefs.setTidyUseTabs(useTabsBox.isSelected());
        if (isCss) {
            prefs.setTidyCssSelectorNewline(selectorNewlineBox.isSelected());
            prefs.setTidyCssNewlineBetweenRules(newlineBetweenRulesBox.isSelected());
        } else {
            prefs.setTidyHtmlWrapLineLength((Integer) wrapLineLengthSpinner.getValue());
            prefs.setTidyHtmlPreserveNewlines(preserveNewlinesBox.isSelected());
            prefs.setTidyHtmlMaxPreserveNewlines((Integer) maxPreserveNewlinesSpinner.getValue());
            prefs.setTidyHtmlIndentInnerHtml(indentInnerHtmlBox.isSelected());
            prefs.setTidyHtmlIndentHeadBody(indentHeadBodyBox.isSelected());
        }
        prefs.save();
    }

    public boolean isConfirmed() { return confirmed; }

    public int getIndentAmount() { return (Integer) indentSpinner.getValue(); }
    public boolean isUseTabs() { return useTabsBox.isSelected(); }

    /**
     * Returns the js-beautify options as a JSON string for HTML formatting.
     */
    public String getHtmlOptionsJson() {
        return "{"
            + "\"indent_size\":" + getIndentAmount() + ","
            + "\"indent_char\":\"" + (isUseTabs() ? "\\t" : " ") + "\","
            + "\"indent_with_tabs\":" + isUseTabs() + ","
            + "\"wrap_line_length\":" + (Integer) wrapLineLengthSpinner.getValue() + ","
            + "\"preserve_newlines\":" + preserveNewlinesBox.isSelected() + ","
            + "\"max_preserve_newlines\":" + (Integer) maxPreserveNewlinesSpinner.getValue() + ","
            + "\"indent_inner_html\":" + indentInnerHtmlBox.isSelected() + ","
            + "\"indent_head_inner_html\":" + indentHeadBodyBox.isSelected() + ","
            + "\"indent_body_inner_html\":" + indentInnerHtmlBox.isSelected() + ","
            + "\"extra_liners\":[\"head\",\"body\",\"/html\"],"
            + "\"end_with_newline\":true"
            + "}";
    }

    /**
     * Returns the js-beautify options as a JSON string for CSS formatting.
     */
    public String getCssOptionsJson() {
        return "{"
            + "\"indent_size\":" + getIndentAmount() + ","
            + "\"indent_char\":\"" + (isUseTabs() ? "\\t" : " ") + "\","
            + "\"indent_with_tabs\":" + isUseTabs() + ","
            + "\"selector_separator_newline\":" + selectorNewlineBox.isSelected() + ","
            + "\"newline_between_rules\":" + newlineBetweenRulesBox.isSelected() + ","
            + "\"end_with_newline\":true"
            + "}";
    }

    /**
     * Returns the js-beautify options as a JSON string for JavaScript formatting.
     */
    public String getJsOptionsJson() {
        return "{"
            + "\"indent_size\":" + getIndentAmount() + ","
            + "\"indent_char\":\"" + (isUseTabs() ? "\\t" : " ") + "\","
            + "\"indent_with_tabs\":" + isUseTabs() + ","
            + "\"preserve_newlines\":true,"
            + "\"max_preserve_newlines\":2,"
            + "\"brace_style\":\"collapse\","
            + "\"end_with_newline\":true"
            + "}";
    }
}
