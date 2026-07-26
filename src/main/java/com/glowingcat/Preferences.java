/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * Preferences.java
 *
 * Manages user preferences for the LayoutLynx application. Preferences are
 * persisted as a JSON file ({@code .layoutlynx-settings.json}) in the user's home directory.
 */
package com.glowingcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Holds and persists user preferences for LayoutLynx.
 */
public class Preferences {

    private static final String PREFS_FILENAME = ".layoutlynx-settings.json";

    /** Font family for the CSS editor pane. */
    private String editorFontFamily = "Monospaced";

    /** Font size for the CSS editor pane. */
    private int editorFontSize = 14;

    /** Font family for the HTML preview pane. */
    private String previewFontFamily = "SansSerif";

    /** Font size for the HTML preview pane. */
    private int previewFontSize = 14;

    // --- LLM / AI Chat settings ---

    /** LLM vendor name. */
    private String llmVendor = "OpenAI";

    /** LLM model identifier. */
    private String llmModel = "gpt-4o";

    /** LLM API key (null means not configured). */
    private String llmApiKey = null;

    /** Font family for the AI chat panel. */
    private String aiFontFamily = detectAIFont();

    /** Font size for the AI chat panel. */
    private int aiFontSize = 14;

    /** Background color for user prompt chat bubbles (hex string). */
    private String userPromptColor = "#C8823C";

    /** Background color for AI response chat bubbles (hex string). */
    private String aiResponseColor = "#8B5A2B";

    // --- Window state ---

    /** Window width. */
    private int windowWidth = 1400;

    /** Window height. */
    private int windowHeight = 800;

    /** Editor/preview split pane divider location. */
    private int editorPreviewDivider = 700;

    /** Main split pane divider (content vs AI panel). */
    private int mainDivider = 1000;

    /** Whether the preview pane is visible. */
    private boolean previewVisible = true;

    /** Whether the AI chat pane is visible. */
    private boolean aiVisible = true;

    // --- License ---

    /** License email address. */
    private String licenseEmail = null;

    /** License key (16 hex chars). */
    private String licenseKey = null;

    private static String detectAIFont() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] candidates;
        if (os.contains("linux")) candidates = new String[]{"DejaVu Sans", "Arial", "Helvetica", "SansSerif"};
        else if (os.contains("win")) candidates = new String[]{"Calibri", "Arial", "Helvetica", "SansSerif"};
        else candidates = new String[]{"Arial", "Helvetica", "SansSerif"};
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, 14);
            if (!f.getFamily().equals("Dialog")) return name;
        }
        return "SansSerif";
    }

    // --- Getters/Setters ---

    public String getEditorFontFamily() { return editorFontFamily; }
    public void setEditorFontFamily(String v) { this.editorFontFamily = v; }
    public int getEditorFontSize() { return editorFontSize; }
    public void setEditorFontSize(int v) { this.editorFontSize = v; }
    public String getPreviewFontFamily() { return previewFontFamily; }
    public void setPreviewFontFamily(String v) { this.previewFontFamily = v; }
    public int getPreviewFontSize() { return previewFontSize; }
    public void setPreviewFontSize(int v) { this.previewFontSize = v; }

    public String getLlmVendor() { return llmVendor; }
    public void setLlmVendor(String v) { this.llmVendor = v; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String v) { this.llmModel = v; }
    public String getLlmApiKey() { return llmApiKey; }
    public void setLlmApiKey(String v) { this.llmApiKey = v; }
    public String getAiFontFamily() { return aiFontFamily; }
    public void setAiFontFamily(String v) { this.aiFontFamily = v; }
    public int getAiFontSize() { return aiFontSize; }
    public void setAiFontSize(int v) { this.aiFontSize = v; }

    public String getUserPromptColor() { return userPromptColor; }
    public void setUserPromptColor(String hex) { this.userPromptColor = hex; }
    public Color getUserPromptColorObj() { return Color.decode(userPromptColor); }
    public void setUserPromptColor(Color color) { this.userPromptColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }

    public String getAiResponseColor() { return aiResponseColor; }
    public void setAiResponseColor(String hex) { this.aiResponseColor = hex; }
    public Color getAiResponseColorObj() { return Color.decode(aiResponseColor); }
    public void setAiResponseColor(Color color) { this.aiResponseColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int v) { this.windowWidth = v; }
    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int v) { this.windowHeight = v; }
    public int getEditorPreviewDivider() { return editorPreviewDivider; }
    public void setEditorPreviewDivider(int v) { this.editorPreviewDivider = v; }
    public int getMainDivider() { return mainDivider; }
    public void setMainDivider(int v) { this.mainDivider = v; }
    public boolean isPreviewVisible() { return previewVisible; }
    public void setPreviewVisible(boolean v) { this.previewVisible = v; }
    public boolean isAiVisible() { return aiVisible; }
    public void setAiVisible(boolean v) { this.aiVisible = v; }

    public String getLicenseEmail() { return licenseEmail; }
    public void setLicenseEmail(String v) { this.licenseEmail = v; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String v) { this.licenseKey = v; }

    private static Path getPrefsPath() {
        return Paths.get(System.getProperty("user.home"), PREFS_FILENAME);
    }

    public static Preferences load() {
        Path path = getPrefsPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                Preferences prefs = gson.fromJson(reader, Preferences.class);
                if (prefs != null) return prefs;
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                // Fall through to return defaults
            }
        }
        return new Preferences();
    }

    public void save() {
        Path path = getPrefsPath();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            // Silently fail - preferences are non-critical
        }
    }
}
