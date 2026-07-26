/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Panel that renders an HTML/CSS preview using JavaFX WebView.
 */
public class PreviewPanel extends JPanel {

    private final JFXPanel fxPanel;
    private WebView webView;
    private WebEngine webEngine;

    public PreviewPanel() {
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(200, 100));

        fxPanel = new JFXPanel();
        add(fxPanel, BorderLayout.CENTER);

        Platform.runLater(() -> {
            webView = new WebView();
            webEngine = webView.getEngine();
            Scene scene = new Scene(webView);
            fxPanel.setScene(scene);
        });
    }

    /**
     * Updates the preview with HTML content. The base URL is set to the HTML file's
     * directory so relative CSS and image references resolve correctly.
     */
    public void updateContent(File htmlFile, String htmlContent) {
        Platform.runLater(() -> {
            if (webEngine == null) return;
            if (htmlFile != null) {
                String baseUrl = htmlFile.getParentFile().toURI().toString();
                webEngine.loadContent(htmlContent, "text/html");
                // Note: loadContent doesn't support base URL directly,
                // so we inject a <base> tag if not present
                if (!htmlContent.toLowerCase().contains("<base")) {
                    String withBase = htmlContent.replaceFirst("(?i)(<head[^>]*>)",
                        "$1\n<base href=\"" + baseUrl + "\">");
                    webEngine.loadContent(withBase, "text/html");
                }
            } else {
                webEngine.loadContent(htmlContent, "text/html");
            }
        });
    }

    /**
     * Loads a URL directly in the preview.
     */
    public void loadUrl(String url) {
        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.load(url);
            }
        });
    }
}
