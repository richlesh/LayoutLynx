/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import java.awt.*;

/**
 * Find and Replace dialog for the editor.
 */
public class ReplaceDialog extends JDialog {

    private final JTextField searchField;
    private final JTextField replaceField;
    private final JCheckBox caseSensitiveBox;
    private final JCheckBox wrapAroundBox;
    private RSyntaxTextArea textArea;

    public ReplaceDialog(JFrame parent, RSyntaxTextArea textArea) {
        super(parent, "Replace", false);
        this.textArea = textArea;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Find:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        searchField = new JTextField(20);
        panel.add(searchField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Replace:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        replaceField = new JTextField(20);
        panel.add(replaceField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        caseSensitiveBox = new JCheckBox("Case Sensitive");
        panel.add(caseSensitiveBox, gbc);

        gbc.gridy = 3;
        wrapAroundBox = new JCheckBox("Wrap Around", true);
        panel.add(wrapAroundBox, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton findNextBtn = new JButton("Find Next");
        findNextBtn.addActionListener(e -> findNext());
        JButton replaceBtn = new JButton("Replace");
        replaceBtn.addActionListener(e -> replaceNext());
        JButton replaceAllBtn = new JButton("Replace All");
        replaceAllBtn.addActionListener(e -> replaceAll());
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> setVisible(false));
        buttonPanel.add(findNextBtn);
        buttonPanel.add(replaceBtn);
        buttonPanel.add(replaceAllBtn);
        buttonPanel.add(closeBtn);

        gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);

        setContentPane(panel);
        pack();
        setLocationRelativeTo(parent);
    }

    public void setTextArea(RSyntaxTextArea textArea) {
        this.textArea = textArea;
    }

    private void findNext() {
        String search = searchField.getText();
        if (search.isEmpty()) return;

        String text = textArea.getText();
        boolean caseSensitive = caseSensitiveBox.isSelected();
        int startPos = textArea.getCaretPosition();

        if (!caseSensitive) {
            text = text.toLowerCase();
            search = search.toLowerCase();
        }

        int idx = text.indexOf(search, startPos);
        if (idx < 0 && wrapAroundBox.isSelected()) {
            idx = text.indexOf(search, 0);
        }

        if (idx >= 0) {
            textArea.setCaretPosition(idx);
            textArea.setSelectionStart(idx);
            textArea.setSelectionEnd(idx + searchField.getText().length());
            textArea.requestFocusInWindow();
        } else {
            JOptionPane.showMessageDialog(this, "Text not found.", "Replace", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void replaceNext() {
        if (textArea.getSelectedText() != null && textArea.getSelectedText().length() > 0) {
            textArea.replaceSelection(replaceField.getText());
        }
        findNext();
    }

    private void replaceAll() {
        String search = searchField.getText();
        String replace = replaceField.getText();
        if (search.isEmpty()) return;

        String text = textArea.getText();
        String newText;
        if (caseSensitiveBox.isSelected()) {
            newText = text.replace(search, replace);
        } else {
            newText = text.replaceAll("(?i)" + java.util.regex.Pattern.quote(search),
                java.util.regex.Matcher.quoteReplacement(replace));
        }

        if (!newText.equals(text)) {
            int caret = textArea.getCaretPosition();
            textArea.setText(newText);
            textArea.setCaretPosition(Math.min(caret, newText.length()));
        }
    }
}
