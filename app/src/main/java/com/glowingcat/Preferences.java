/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * Preferences.java
 *
 * Manages user preferences for the LayoutLynx application. Preferences are
 * persisted as a JSON file ({@code .layoutlynx-settings.json}) in the user's home directory.
 * Includes font family and font size settings for both the editor and preview panes.
 */
package com.glowingcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Holds and persists user preferences for LayoutLynx.
 * <p>
 * Preferences are loaded from and saved to {@code ~/.layoutlynx-settings.json}.
 * If the file does not exist or cannot be read, sensible defaults are used.
 */
public class Preferences {

    private static final String PREFS_FILENAME = ".layoutlynx-settings.json";

    /** Font family for the code editor pane. */
    private String editorFontFamily = "Monospaced";

    /** Font size for the code editor pane. */
    private int editorFontSize = 14;

    /** Font family for the HTML preview pane. */
    private String previewFontFamily = "SansSerif";

    /** Font size for the HTML preview pane. */
    private int previewFontSize = 14;

    /** Font family for code in the HTML preview pane. */
    private String previewCodeFontFamily = "Monospaced";

    /** Font size for code in the HTML preview pane. */
    private int previewCodeFontSize = 13;

    // --- Editor settings ---

    /** UI theme: "light" or "dark". */
    private String theme = "light";

    /** Button highlight color (hex string). */
    private String buttonHighlightColor = "#DAA520";

    /** Editor highlight/caret line color (hex string). */
    private String highlightColor = "#E8F2FE";

    /** Whether to use real tabs (true) or spaces (false). */
    private boolean useTabs = false;

    /** Number of spaces per tab stop. */
    private int tabSize = 4;

    // --- AI Chat color settings (stored in app prefs, passed to aichat module via ChatColors) ---

    /** Background color for user prompt chat bubbles (hex string for Gson). */
    private String userPromptColor = "#ffcc33";

    /** Text color for user prompt chat bubbles (hex string for Gson). */
    private String userTextColor = "#000000";

    /** Background color for AI response chat bubbles (hex string for Gson). */
    private String aiResponseColor = "#c8823c";

    /** Text color for AI response chat bubbles (hex string for Gson). */
    private String aiTextColor = "#FFFFFF";

    // --- Window state (not shown in preferences dialog) ---

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

    // --- Tidy (js-beautify) settings ---

    /** Tidy: indent size. */
    private int tidyIndentSize = 4;

    /** Tidy: use tabs for indentation. */
    private boolean tidyUseTabs = false;

    /** HTML tidy: wrap line length (0 = no wrap). */
    private int tidyHtmlWrapLineLength = 0;

    /** HTML tidy: preserve existing newlines. */
    private boolean tidyHtmlPreserveNewlines = true;

    /** HTML tidy: max consecutive blank lines to preserve. */
    private int tidyHtmlMaxPreserveNewlines = 2;

    /** HTML tidy: indent inner HTML content. */
    private boolean tidyHtmlIndentInnerHtml = true;

    /** HTML tidy: indent head and body tags themselves. */
    private boolean tidyHtmlIndentHeadBody = false;

    /** CSS tidy: each selector on a new line. */
    private boolean tidyCssSelectorNewline = true;

    /** CSS tidy: blank line between rules. */
    private boolean tidyCssNewlineBetweenRules = true;

    // --- License ---

    /** License email address. */
    private String licenseEmail = null;

    /** License key (16 hex chars). */
    private String licenseKey = null;

    // --- AI Chat Color Getters/Setters ---

    public Color getUserPromptColorObj() { return Color.decode(userPromptColor); }
    public void setUserPromptColor(Color color) { this.userPromptColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getUserPromptColor() { return userPromptColor; }
    public void setUserPromptColor(String hex) { this.userPromptColor = hex; }

    public Color getUserTextColorObj() { return Color.decode(userTextColor); }
    public void setUserTextColor(Color color) { this.userTextColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getUserTextColor() { return userTextColor; }
    public void setUserTextColor(String hex) { this.userTextColor = hex; }

    public Color getAiResponseColorObj() { return Color.decode(aiResponseColor); }
    public void setAiResponseColor(Color color) { this.aiResponseColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getAiResponseColor() { return aiResponseColor; }
    public void setAiResponseColor(String hex) { this.aiResponseColor = hex; }

    public Color getAiTextColorObj() { return Color.decode(aiTextColor); }
    public void setAiTextColor(Color color) { this.aiTextColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getAiTextColor() { return aiTextColor; }
    public void setAiTextColor(String hex) { this.aiTextColor = hex; }

    public String getEditorFontFamily() { return editorFontFamily; }
    public void setEditorFontFamily(String editorFontFamily) { this.editorFontFamily = editorFontFamily; }
    public int getEditorFontSize() { return editorFontSize; }
    public void setEditorFontSize(int editorFontSize) { this.editorFontSize = editorFontSize; }
    public String getPreviewFontFamily() { return previewFontFamily; }
    public void setPreviewFontFamily(String previewFontFamily) { this.previewFontFamily = previewFontFamily; }
    public int getPreviewFontSize() { return previewFontSize; }
    public void setPreviewFontSize(int previewFontSize) { this.previewFontSize = previewFontSize; }
    public String getPreviewCodeFontFamily() { return previewCodeFontFamily; }
    public void setPreviewCodeFontFamily(String previewCodeFontFamily) { this.previewCodeFontFamily = previewCodeFontFamily; }
    public int getPreviewCodeFontSize() { return previewCodeFontSize; }
    public void setPreviewCodeFontSize(int previewCodeFontSize) { this.previewCodeFontSize = previewCodeFontSize; }

    // --- Editor settings getters/setters ---

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public boolean isDarkMode() { return "dark".equals(theme); }

    public String getButtonHighlightColor() { return buttonHighlightColor; }
    public void setButtonHighlightColor(String hex) { this.buttonHighlightColor = hex; }
    public Color getButtonHighlightColorObj() { return Color.decode(buttonHighlightColor); }
    public void setButtonHighlightColor(Color color) { this.buttonHighlightColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }

    public String getHighlightColor() { return highlightColor; }
    public void setHighlightColor(String hex) { this.highlightColor = hex; }
    public Color getHighlightColorObj() { return Color.decode(highlightColor); }
    public void setHighlightColor(Color color) { this.highlightColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public boolean isUseTabs() { return useTabs; }
    public void setUseTabs(boolean useTabs) { this.useTabs = useTabs; }
    public int getTabSize() { return tabSize; }
    public void setTabSize(int tabSize) { this.tabSize = Math.max(1, Math.min(8, tabSize)); }

    // --- Window state getters/setters ---

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }
    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }
    public int getEditorPreviewDivider() { return editorPreviewDivider; }
    public void setEditorPreviewDivider(int editorPreviewDivider) { this.editorPreviewDivider = editorPreviewDivider; }
    public int getMainDivider() { return mainDivider; }
    public void setMainDivider(int mainDivider) { this.mainDivider = mainDivider; }
    public boolean isPreviewVisible() { return previewVisible; }
    public void setPreviewVisible(boolean previewVisible) { this.previewVisible = previewVisible; }
    public boolean isAiVisible() { return aiVisible; }
    public void setAiVisible(boolean aiVisible) { this.aiVisible = aiVisible; }

    // --- Tidy settings getters/setters ---

    public int getTidyIndentSize() { return tidyIndentSize; }
    public void setTidyIndentSize(int v) { this.tidyIndentSize = Math.max(1, Math.min(8, v)); }
    public boolean isTidyUseTabs() { return tidyUseTabs; }
    public void setTidyUseTabs(boolean v) { this.tidyUseTabs = v; }
    public int getTidyHtmlWrapLineLength() { return tidyHtmlWrapLineLength; }
    public void setTidyHtmlWrapLineLength(int v) { this.tidyHtmlWrapLineLength = v; }
    public boolean isTidyHtmlPreserveNewlines() { return tidyHtmlPreserveNewlines; }
    public void setTidyHtmlPreserveNewlines(boolean v) { this.tidyHtmlPreserveNewlines = v; }
    public int getTidyHtmlMaxPreserveNewlines() { return tidyHtmlMaxPreserveNewlines; }
    public void setTidyHtmlMaxPreserveNewlines(int v) { this.tidyHtmlMaxPreserveNewlines = v; }
    public boolean isTidyHtmlIndentInnerHtml() { return tidyHtmlIndentInnerHtml; }
    public void setTidyHtmlIndentInnerHtml(boolean v) { this.tidyHtmlIndentInnerHtml = v; }
    public boolean isTidyHtmlIndentHeadBody() { return tidyHtmlIndentHeadBody; }
    public void setTidyHtmlIndentHeadBody(boolean v) { this.tidyHtmlIndentHeadBody = v; }
    public boolean isTidyCssSelectorNewline() { return tidyCssSelectorNewline; }
    public void setTidyCssSelectorNewline(boolean v) { this.tidyCssSelectorNewline = v; }
    public boolean isTidyCssNewlineBetweenRules() { return tidyCssNewlineBetweenRules; }
    public void setTidyCssNewlineBetweenRules(boolean v) { this.tidyCssNewlineBetweenRules = v; }

    // --- License getters/setters ---

    public String getLicenseEmail() { return licenseEmail; }
    public void setLicenseEmail(String licenseEmail) { this.licenseEmail = licenseEmail; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }

    private static Path getPrefsPath() {
        return Paths.get(System.getProperty("user.home"), PREFS_FILENAME);
    }

    public static Preferences load() {
        Path path = getPrefsPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                Preferences prefs = gson.fromJson(reader, Preferences.class);
                if (prefs != null) {
                    return prefs;
                }
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
