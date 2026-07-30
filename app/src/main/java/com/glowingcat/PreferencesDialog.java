/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * PreferencesDialog.java
 *
 * A modal dialog for editing LayoutLynx user preferences. Font settings,
 * editor settings, and appearance options.
 * AI/LLM settings are handled by AIChatPreferencesDialog from the aichat module.
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;

public class PreferencesDialog extends JDialog {

    private final JComboBox<String> editorFontCombo;
    private final JComboBox<Integer> editorSizeCombo;
    private final JComboBox<String> themeCombo;
    private final JComboBox<String> previewFontCombo;
    private final JComboBox<Integer> previewSizeCombo;
    private final JComboBox<String> previewCodeFontCombo;
    private final JComboBox<Integer> previewCodeSizeCombo;
    private final Color[] highlightColor;
    private final Color[] buttonHighlightColor;
    private final JCheckBox useTabsBox;
    private final JSpinner tabSizeSpinner;
    private boolean confirmed = false;

    private static final Integer[] FONT_SIZES = {8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32, 36};

    public PreferencesDialog(JFrame owner, Preferences prefs) {
        super(owner, "Preferences", true);

        String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        // Initialize font combos
        editorFontCombo = new JComboBox<>(fontFamilies);
        editorFontCombo.setSelectedItem(prefs.getEditorFontFamily());
        editorSizeCombo = new JComboBox<>(FONT_SIZES);
        editorSizeCombo.setSelectedItem(prefs.getEditorFontSize());

        // Initialize theme combo
        themeCombo = new JComboBox<>(new String[]{"Light", "Dark"});
        themeCombo.setSelectedItem(prefs.isDarkMode() ? "Dark" : "Light");

        previewFontCombo = new JComboBox<>(fontFamilies);
        previewFontCombo.setSelectedItem(prefs.getPreviewFontFamily());
        previewSizeCombo = new JComboBox<>(FONT_SIZES);
        previewSizeCombo.setSelectedItem(prefs.getPreviewFontSize());

        previewCodeFontCombo = new JComboBox<>(fontFamilies);
        previewCodeFontCombo.setSelectedItem(prefs.getPreviewCodeFontFamily());
        previewCodeSizeCombo = new JComboBox<>(FONT_SIZES);
        previewCodeSizeCombo.setSelectedItem(prefs.getPreviewCodeFontSize());

        // Initialize editor settings
        highlightColor = new Color[]{prefs.getHighlightColorObj()};
        buttonHighlightColor = new Color[]{prefs.getButtonHighlightColorObj()};
        useTabsBox = new JCheckBox("Use Tabs", prefs.isUseTabs());
        tabSizeSpinner = new JSpinner(new SpinnerNumberModel(prefs.getTabSize(), 1, 8, 1));

        // === Main Panel ===
        JPanel mainPanel = buildPanel();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(mainPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> { confirmed = true; dispose(); });
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private JPanel buildPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // Editor section
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel editorHeader = new JLabel("Editor Source");
        editorHeader.setFont(editorHeader.getFont().deriveFont(Font.BOLD));
        panel.add(editorHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(editorFontCombo, gbc);
        gbc.weightx = 0;

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(editorSizeCombo, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        panel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(4, 6, 4, 6);

        // Preview Text section
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel previewHeader = new JLabel("Preview Text");
        previewHeader.setFont(previewHeader.getFont().deriveFont(Font.BOLD));
        panel.add(previewHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(previewFontCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(previewSizeCombo, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        panel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(4, 6, 4, 6);

        // Preview Code section
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel previewCodeHeader = new JLabel("Preview Code");
        previewCodeHeader.setFont(previewCodeHeader.getFont().deriveFont(Font.BOLD));
        panel.add(previewCodeHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(previewCodeFontCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(previewCodeSizeCombo, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        panel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(4, 6, 4, 6);

        // Editor section
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel editorSettingsHeader = new JLabel("Editor");
        editorSettingsHeader.setFont(editorSettingsHeader.getFont().deriveFont(Font.BOLD));
        panel.add(editorSettingsHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Highlight Color:"), gbc);
        JPanel hlSwatch = new JPanel();
        hlSwatch.setBackground(highlightColor[0]);
        hlSwatch.setPreferredSize(new Dimension(60, 24));
        hlSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        hlSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hlSwatch.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Color c = JColorChooser.showDialog(PreferencesDialog.this, "Highlight Color", highlightColor[0]);
                if (c != null) { highlightColor[0] = c; hlSwatch.setBackground(c); }
            }
        });
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(hlSwatch, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(useTabsBox, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Spaces for tab:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(tabSizeSpinner, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        panel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(4, 6, 4, 6);

        // Appearance section
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel themeHeader = new JLabel("Appearance");
        themeHeader.setFont(themeHeader.getFont().deriveFont(Font.BOLD));
        panel.add(themeHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Theme:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(themeCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Button Highlight:"), gbc);
        JPanel btnHlSwatch = new JPanel();
        btnHlSwatch.setBackground(buttonHighlightColor[0]);
        btnHlSwatch.setPreferredSize(new Dimension(60, 24));
        btnHlSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        btnHlSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnHlSwatch.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Color c = JColorChooser.showDialog(PreferencesDialog.this, "Button Highlight Color", buttonHighlightColor[0]);
                if (c != null) { buttonHighlightColor[0] = c; btnHlSwatch.setBackground(c); }
            }
        });
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(btnHlSwatch, gbc);

        // Vertical glue to push content to top
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    public boolean isConfirmed() { return confirmed; }

    public void applyTo(Preferences prefs) {
        prefs.setEditorFontFamily((String) editorFontCombo.getSelectedItem());
        prefs.setEditorFontSize((Integer) editorSizeCombo.getSelectedItem());
        prefs.setTheme("Dark".equals(themeCombo.getSelectedItem()) ? "dark" : "light");
        prefs.setButtonHighlightColor(buttonHighlightColor[0]);
        prefs.setPreviewFontFamily((String) previewFontCombo.getSelectedItem());
        prefs.setPreviewFontSize((Integer) previewSizeCombo.getSelectedItem());
        prefs.setPreviewCodeFontFamily((String) previewCodeFontCombo.getSelectedItem());
        prefs.setPreviewCodeFontSize((Integer) previewCodeSizeCombo.getSelectedItem());
        prefs.setHighlightColor(highlightColor[0]);
        prefs.setUseTabs(useTabsBox.isSelected());
        prefs.setTabSize((Integer) tabSizeSpinner.getValue());
    }
}
