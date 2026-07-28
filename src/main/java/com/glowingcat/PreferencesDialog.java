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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreferencesDialog extends JDialog {

    private final JComboBox<String> editorFontCombo;
    private final JComboBox<Integer> editorSizeCombo;
    private final JComboBox<String> themeCombo;
    private final JComboBox<String> previewFontCombo;
    private final JComboBox<Integer> previewSizeCombo;
    private final JComboBox<String> previewCodeFontCombo;
    private final JComboBox<Integer> previewCodeSizeCombo;
    private final Color[] highlightColor;
    private final JCheckBox useTabsBox;
    private final JSpinner tabSizeSpinner;
    private final JComboBox<String> llmVendorCombo;
    private final JComboBox<String> llmModelCombo;
    private final JPasswordField llmApiKeyField;
    private final JComboBox<String> aiFontCombo;
    private final JComboBox<Integer> aiFontSizeCombo;
    private final Color[] userPromptColor;
    private final Color[] aiResponseColor;
    private boolean confirmed = false;

    private static final Integer[] FONT_SIZES = {8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32, 36};

    private static final String[][] VENDOR_DATA = {
        {"Alibaba", "https://www.alibabacloud.com/help/en/model-studio/get-api-key", "https://dashscope-us.aliyuncs.com/compatible-mode/v1"},
        {"Anthropic", "https://console.anthropic.com/settings/keys", "https://api.anthropic.com/v1"},
        {"Cerebras", "https://cloud.cerebras.ai", "https://api.cerebras.ai/v1"},
        {"DeepSeek", "https://platform.deepseek.com/api_keys", "https://api.deepseek.com/v1"},
        {"Google", "https://aistudio.google.com/apikey", "https://generativelanguage.googleapis.com/v1beta/openai"},
        {"Groq", "https://console.groq.com/keys", "https://api.groq.com/openai/v1"},
        {"Meta", "https://developer.meta.com/ai/", "https://api.meta.ai/v1"},
        {"Mistral", "https://console.mistral.ai/api-keys", "https://api.mistral.ai/v1"},
        {"Moonshot AI", "https://platform.kimi.ai", "https://api.moonshot.ai/v1"},
        {"Ollama", "https://ollama.com", "http://localhost:11434/v1"},
        {"OpenAI", "https://platform.openai.com/api-keys", "https://api.openai.com/v1"},
        {"Perplexity", "https://www.perplexity.ai/settings/api", "https://api.perplexity.ai"},
        {"xAI", "https://console.x.ai", "https://api.x.ai/v1"},
    };

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
        useTabsBox = new JCheckBox("Use Tabs", prefs.isUseTabs());
        tabSizeSpinner = new JSpinner(new SpinnerNumberModel(prefs.getTabSize(), 1, 8, 1));

        // Initialize LLM combos
        String[] vendorNames = new String[VENDOR_DATA.length];
        for (int i = 0; i < VENDOR_DATA.length; i++) vendorNames[i] = VENDOR_DATA[i][0];
        llmVendorCombo = new JComboBox<>(vendorNames);
        if (prefs.getLlmVendor() != null) llmVendorCombo.setSelectedItem(prefs.getLlmVendor());

        llmModelCombo = new JComboBox<>();
        llmModelCombo.setEditable(true);

        llmApiKeyField = new JPasswordField(prefs.getLlmApiKey() != null ? prefs.getLlmApiKey() : "", 20);

        aiFontCombo = new JComboBox<>(fontFamilies);
        aiFontCombo.setSelectedItem(prefs.getAiFontFamily());
        aiFontSizeCombo = new JComboBox<>(FONT_SIZES);
        aiFontSizeCombo.setSelectedItem(prefs.getAiFontSize());

        userPromptColor = new Color[]{prefs.getUserPromptColorObj()};
        aiResponseColor = new Color[]{prefs.getAiResponseColorObj()};

        // === LEFT PANEL: Font Settings ===
        JPanel leftPanel = buildFontPanel();

        // === RIGHT PANEL: LLM / AI Settings ===
        JPanel rightPanel = buildLlmPanel(prefs);

        // === Main layout: left | right ===
        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        mainPanel.add(leftPanel);
        mainPanel.add(rightPanel);

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

        // Wire up model fetching
        Runnable fetchModels = () -> {
            int vi = llmVendorCombo.getSelectedIndex();
            String apiKey = new String(llmApiKeyField.getPassword()).trim();
            String baseUrl = VENDOR_DATA[vi][2];
            llmModelCombo.removeAllItems();
            if (apiKey.isEmpty() && !"Ollama".equals(VENDOR_DATA[vi][0])) {
                return;
            }
            new Thread(() -> {
                try {
                    String modelsUrl = "Perplexity".equals(VENDOR_DATA[vi][0])
                        ? baseUrl + "/v1/models" : baseUrl + "/models";
                    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(modelsUrl))
                        .header("Content-Type", "application/json")
                        .GET();
                    if ("Anthropic".equals(VENDOR_DATA[vi][0])) {
                        reqBuilder.header("x-api-key", apiKey);
                        reqBuilder.header("anthropic-version", "2023-06-01");
                    } else if (!apiKey.isEmpty()) {
                        reqBuilder.header("Authorization", "Bearer " + apiKey);
                    }
                    HttpResponse<String> resp = HttpClient.newHttpClient()
                        .send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();
                    List<String> models = new ArrayList<>();
                    Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                    while (m.find()) {
                        String id = m.group(1);
                        if ("Perplexity".equals(VENDOR_DATA[vi][0]) && id.contains("/")) {
                            id = id.substring(id.indexOf('/') + 1);
                        }
                        models.add(id);
                    }
                    SwingUtilities.invokeLater(() -> {
                        llmModelCombo.removeAllItems();
                        for (String mod : models) llmModelCombo.addItem(mod);
                        if (prefs.getLlmModel() != null) llmModelCombo.setSelectedItem(prefs.getLlmModel());
                    });
                } catch (Exception ex) {
                    // leave model combo empty on failure
                }
            }).start();
        };
        if (prefs.getLlmModel() != null) llmModelCombo.addItem(prefs.getLlmModel());
        fetchModels.run();
        llmVendorCombo.addActionListener(e -> {
            llmApiKeyField.setText("");
            llmModelCombo.removeAllItems();
            fetchModels.run();
        });
        llmApiKeyField.getDocument().addDocumentListener(new DocumentListener() {
            private final Timer debounce = new Timer(500, e -> {
                llmModelCombo.removeAllItems();
                fetchModels.run();
            });
            { debounce.setRepeats(false); }
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });

        getRootPane().setDefaultButton(okButton);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private JPanel buildFontPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Fonts"));
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

        // Vertical glue to push content to top
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private JPanel buildLlmPanel(Preferences prefs) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("AI Chat"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // LLM Connection
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel llmHeader = new JLabel("LLM Connection");
        llmHeader.setFont(llmHeader.getFont().deriveFont(Font.BOLD));
        panel.add(llmHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Vendor:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(llmVendorCombo, gbc);
        gbc.weightx = 0;

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(llmModelCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(llmApiKeyField, gbc);

        gbc.gridy = ++row; gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        JLabel apiKeyLink = new JLabel("<html><nobr><a href=''>Get API key...</a></nobr></html>");
        apiKeyLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        apiKeyLink.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try { Desktop.getDesktop().browse(URI.create(VENDOR_DATA[llmVendorCombo.getSelectedIndex()][1])); }
                catch (Exception ignored) {}
            }
        });
        panel.add(apiKeyLink, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        panel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(4, 6, 4, 6);

        // Appearance
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel appearHeader = new JLabel("Appearance");
        appearHeader.setFont(appearHeader.getFont().deriveFont(Font.BOLD));
        panel.add(appearHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(aiFontCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(aiFontSizeCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("User Color:"), gbc);
        JPanel userSwatch = new JPanel();
        userSwatch.setBackground(userPromptColor[0]);
        userSwatch.setPreferredSize(new Dimension(60, 24));
        userSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        userSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        userSwatch.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Color c = JColorChooser.showDialog(PreferencesDialog.this, "User Prompt Color", userPromptColor[0]);
                if (c != null) { userPromptColor[0] = c; userSwatch.setBackground(c); }
            }
        });
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(userSwatch, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("AI Color:"), gbc);
        JPanel aiSwatch = new JPanel();
        aiSwatch.setBackground(aiResponseColor[0]);
        aiSwatch.setPreferredSize(new Dimension(60, 24));
        aiSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        aiSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        aiSwatch.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Color c = JColorChooser.showDialog(PreferencesDialog.this, "AI Response Color", aiResponseColor[0]);
                if (c != null) { aiResponseColor[0] = c; aiSwatch.setBackground(c); }
            }
        });
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(aiSwatch, gbc);

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
        prefs.setPreviewFontFamily((String) previewFontCombo.getSelectedItem());
        prefs.setPreviewFontSize((Integer) previewSizeCombo.getSelectedItem());
        prefs.setPreviewCodeFontFamily((String) previewCodeFontCombo.getSelectedItem());
        prefs.setPreviewCodeFontSize((Integer) previewCodeSizeCombo.getSelectedItem());
        prefs.setHighlightColor(highlightColor[0]);
        prefs.setUseTabs(useTabsBox.isSelected());
        prefs.setTabSize((Integer) tabSizeSpinner.getValue());
        prefs.setLlmVendor((String) llmVendorCombo.getSelectedItem());
        Object modelItem = llmModelCombo.getSelectedItem();
        prefs.setLlmModel(modelItem != null ? modelItem.toString() : null);
        String key = new String(llmApiKeyField.getPassword()).trim();
        prefs.setLlmApiKey(key.isEmpty() ? null : key);
        prefs.setAiFontFamily((String) aiFontCombo.getSelectedItem());
        prefs.setAiFontSize((Integer) aiFontSizeCombo.getSelectedItem());
        prefs.setUserPromptColor(userPromptColor[0]);
        prefs.setAiResponseColor(aiResponseColor[0]);
    }
}
