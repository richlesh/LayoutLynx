/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * PreferencesDialog.java
 *
 * A modal dialog for editing LayoutLynx user preferences. Font settings on
 * the left, AI/LLM settings on the right.
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;

public class PreferencesDialog extends JDialog {

    private final JComboBox<String> editorFontCombo;
    private final JComboBox<Integer> editorSizeCombo;
    private final JComboBox<String> previewFontCombo;
    private final JComboBox<Integer> previewSizeCombo;
    private final JComboBox<String> llmVendorCombo;
    private final JComboBox<String> llmModelCombo;
    private final JPasswordField llmApiKeyField;
    private final JComboBox<String> aiFontCombo;
    private final JComboBox<Integer> aiFontSizeCombo;
    private final Color[] userPromptColor;
    private final Color[] aiResponseColor;
    private boolean confirmed = false;
    private final Preferences prefs;

    private static final Integer[] FONT_SIZES = {8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32, 36};

    private static final String[][] VENDOR_DATA = {
        {"Alibaba", "https://www.alibabacloud.com/help/en/model-studio/get-api-key"},
        {"Anthropic", "https://console.anthropic.com/settings/keys"},
        {"Cerebras", "https://cloud.cerebras.ai"},
        {"DeepSeek", "https://platform.deepseek.com/api_keys"},
        {"Google", "https://aistudio.google.com/apikey"},
        {"Groq", "https://console.groq.com/keys"},
        {"Mistral", "https://console.mistral.ai/api-keys"},
        {"Ollama", "https://ollama.com"},
        {"OpenAI", "https://platform.openai.com/api-keys"},
        {"Perplexity", "https://www.perplexity.ai/settings/api"},
        {"xAI", "https://console.x.ai"},
    };

    public PreferencesDialog(JFrame owner, Preferences prefs) {
        super(owner, "Preferences", true);
        this.prefs = prefs;

        String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        // Initialize font combos
        editorFontCombo = new JComboBox<>(fontFamilies);
        editorFontCombo.setSelectedItem(prefs.getEditorFontFamily());
        editorSizeCombo = new JComboBox<>(FONT_SIZES);
        editorSizeCombo.setSelectedItem(prefs.getEditorFontSize());

        previewFontCombo = new JComboBox<>(fontFamilies);
        previewFontCombo.setSelectedItem(prefs.getPreviewFontFamily());
        previewSizeCombo = new JComboBox<>(FONT_SIZES);
        previewSizeCombo.setSelectedItem(prefs.getPreviewFontSize());

        // Initialize LLM combos
        String[] vendorNames = new String[VENDOR_DATA.length];
        for (int i = 0; i < VENDOR_DATA.length; i++) vendorNames[i] = VENDOR_DATA[i][0];
        llmVendorCombo = new JComboBox<>(vendorNames);
        if (prefs.getLlmVendor() != null) llmVendorCombo.setSelectedItem(prefs.getLlmVendor());

        llmModelCombo = new JComboBox<>();
        llmModelCombo.setEditable(true);
        if (prefs.getLlmModel() != null) llmModelCombo.setSelectedItem(prefs.getLlmModel());

        llmApiKeyField = new JPasswordField(prefs.getLlmApiKey() != null ? prefs.getLlmApiKey() : "", 20);

        aiFontCombo = new JComboBox<>(fontFamilies);
        aiFontCombo.setSelectedItem(prefs.getAiFontFamily());
        aiFontSizeCombo = new JComboBox<>(FONT_SIZES);
        aiFontSizeCombo.setSelectedItem(prefs.getAiFontSize());

        userPromptColor = new Color[]{prefs.getUserPromptColorObj()};
        aiResponseColor = new Color[]{prefs.getAiResponseColorObj()};

        buildUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(16, 16));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Left: Font settings
        JPanel fontPanel = new JPanel(new GridBagLayout());
        fontPanel.setBorder(BorderFactory.createTitledBorder("Fonts"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addLabeledRow(fontPanel, gbc, row++, "Editor Font:", editorFontCombo, editorSizeCombo);
        addLabeledRow(fontPanel, gbc, row++, "Preview Font:", previewFontCombo, previewSizeCombo);
        addLabeledRow(fontPanel, gbc, row++, "AI Chat Font:", aiFontCombo, aiFontSizeCombo);

        // Right: AI settings
        JPanel aiPanel = new JPanel(new GridBagLayout());
        aiPanel.setBorder(BorderFactory.createTitledBorder("AI Assistant"));
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(4, 4, 4, 4);
        gbc2.anchor = GridBagConstraints.WEST;

        row = 0;
        gbc2.gridx = 0; gbc2.gridy = row;
        aiPanel.add(new JLabel("Vendor:"), gbc2);
        gbc2.gridx = 1; gbc2.gridwidth = 2;
        aiPanel.add(llmVendorCombo, gbc2);
        gbc2.gridwidth = 1;

        row++;
        gbc2.gridx = 0; gbc2.gridy = row;
        aiPanel.add(new JLabel("Model:"), gbc2);
        gbc2.gridx = 1; gbc2.gridwidth = 2;
        aiPanel.add(llmModelCombo, gbc2);
        gbc2.gridwidth = 1;

        row++;
        gbc2.gridx = 0; gbc2.gridy = row;
        aiPanel.add(new JLabel("API Key:"), gbc2);
        gbc2.gridx = 1; gbc2.gridwidth = 2; gbc2.fill = GridBagConstraints.HORIZONTAL;
        aiPanel.add(llmApiKeyField, gbc2);
        gbc2.fill = GridBagConstraints.NONE; gbc2.gridwidth = 1;

        row++;
        gbc2.gridx = 0; gbc2.gridy = row;
        aiPanel.add(new JLabel("User Bubble:"), gbc2);
        gbc2.gridx = 1;
        JButton userColorBtn = createColorButton(userPromptColor);
        aiPanel.add(userColorBtn, gbc2);

        row++;
        gbc2.gridx = 0; gbc2.gridy = row;
        aiPanel.add(new JLabel("AI Bubble:"), gbc2);
        gbc2.gridx = 1;
        JButton aiColorBtn = createColorButton(aiResponseColor);
        aiPanel.add(aiColorBtn, gbc2);

        // Combine panels
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        centerPanel.add(fontPanel);
        centerPanel.add(aiPanel);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> { confirmed = true; dispose(); });
        buttonPanel.add(cancelBtn);
        buttonPanel.add(okBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void addLabeledRow(JPanel panel, GridBagConstraints gbc, int row,
                               String label, JComboBox<?> fontCombo, JComboBox<?> sizeCombo) {
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(fontCombo, gbc);
        gbc.gridx = 2;
        panel.add(sizeCombo, gbc);
    }

    private JButton createColorButton(Color[] colorHolder) {
        JButton btn = new JButton("  ");
        btn.setBackground(colorHolder[0]);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(40, 25));
        btn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Choose Color", colorHolder[0]);
            if (chosen != null) {
                colorHolder[0] = chosen;
                btn.setBackground(chosen);
            }
        });
        return btn;
    }

    public boolean isConfirmed() { return confirmed; }

    public Preferences getUpdatedPreferences() {
        prefs.setEditorFontFamily((String) editorFontCombo.getSelectedItem());
        prefs.setEditorFontSize((Integer) editorSizeCombo.getSelectedItem());
        prefs.setPreviewFontFamily((String) previewFontCombo.getSelectedItem());
        prefs.setPreviewFontSize((Integer) previewSizeCombo.getSelectedItem());
        prefs.setLlmVendor((String) llmVendorCombo.getSelectedItem());
        prefs.setLlmModel((String) llmModelCombo.getSelectedItem());
        prefs.setLlmApiKey(new String(llmApiKeyField.getPassword()));
        prefs.setAiFontFamily((String) aiFontCombo.getSelectedItem());
        prefs.setAiFontSize((Integer) aiFontSizeCombo.getSelectedItem());
        prefs.setUserPromptColor(userPromptColor[0]);
        prefs.setAiResponseColor(aiResponseColor[0]);
        return prefs;
    }
}
