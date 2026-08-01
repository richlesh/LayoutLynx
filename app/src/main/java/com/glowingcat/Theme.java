/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import java.awt.Color;

/**
 * Defines color themes for the application UI (light and dark modes).
 */
public class Theme {

    public static final Theme LIGHT = new Theme(
        new Color(255, 255, 255),   // editorBackground
        new Color(0, 0, 0),         // editorForeground
        new Color(245, 245, 245),   // toolbarBackground
        new Color(50, 50, 50),      // toolbarForeground
        new Color(240, 240, 240),   // panelBackground
        new Color(50, 50, 50),      // panelForeground
        new Color(220, 220, 220),   // borderColor
        new Color(245, 245, 245),   // lineNumberBackground
        new Color(128, 128, 128),   // lineNumberForeground
        "default"                    // rsyntaxTheme
    );

    public static final Theme DARK = new Theme(
        new Color(30, 30, 30),         // editorBackground
        new Color(212, 212, 212),   // editorForeground
        new Color(37, 37, 38),      // toolbarBackground
        new Color(204, 204, 204),   // toolbarForeground
        new Color(37, 37, 38),      // panelBackground
        new Color(204, 204, 204),   // panelForeground
        new Color(60, 60, 60),      // borderColor
        new Color(37, 37, 38),      // lineNumberBackground
        new Color(133, 133, 133),   // lineNumberForeground
        "dark"                       // rsyntaxTheme
    );

    public final Color editorBackground;
    public final Color editorForeground;
    public final Color toolbarBackground;
    public final Color toolbarForeground;
    public final Color panelBackground;
    public final Color panelForeground;
    public final Color borderColor;
    public final Color lineNumberBackground;
    public final Color lineNumberForeground;
    public final String rsyntaxTheme;

    private Theme(Color editorBackground, Color editorForeground,
                  Color toolbarBackground, Color toolbarForeground,
                  Color panelBackground, Color panelForeground,
                  Color borderColor,
                  Color lineNumberBackground, Color lineNumberForeground,
                  String rsyntaxTheme) {
        this.editorBackground = editorBackground;
        this.editorForeground = editorForeground;
        this.toolbarBackground = toolbarBackground;
        this.toolbarForeground = toolbarForeground;
        this.panelBackground = panelBackground;
        this.panelForeground = panelForeground;
        this.borderColor = borderColor;
        this.lineNumberBackground = lineNumberBackground;
        this.lineNumberForeground = lineNumberForeground;
        this.rsyntaxTheme = rsyntaxTheme;
    }
}
