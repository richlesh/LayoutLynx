/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * A minimap panel that displays a zoomed-out view of an RSyntaxTextArea.
 * Shows the full document with a viewport indicator (highlighted rectangle)
 * showing which portion is currently visible. Click or drag to scroll.
 */
class MinimapPanel extends JPanel {

    private static final int MINIMAP_WIDTH = 80;
    private static final float SCALE = 0.15f;
    private static final int LINE_HEIGHT = 2;

    private final RSyntaxTextArea textArea;
    private RTextScrollPane scrollPane;
    private BufferedImage minimapImage;
    private boolean needsRepaint = true;

    MinimapPanel(RSyntaxTextArea textArea) {
        this.textArea = textArea;
        setPreferredSize(new Dimension(MINIMAP_WIDTH, 0));
        setMinimumSize(new Dimension(MINIMAP_WIDTH, 0));
        setBackground(new Color(230, 230, 230));

        // Rebuild minimap when document changes
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private final Timer debounce = new Timer(300, e -> {
                needsRepaint = true;
                minimapImage = null; // Force full re-render
                repaint();
            });
            { debounce.setRepeats(false); }
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });

        // Click/drag to scroll
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                scrollToY(e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                scrollToY(e.getY());
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    void setScrollPane(RTextScrollPane scrollPane) {
        this.scrollPane = scrollPane;
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> repaint());
    }

    /**
     * Scrolls the editor so that the clicked Y position in the minimap
     * corresponds to the center of the viewport.
     */
    private void scrollToY(int mouseY) {
        if (scrollPane == null) return;
        int totalLines = textArea.getLineCount();
        int minimapHeight = getHeight();
        int docHeight = totalLines * LINE_HEIGHT;

        // Map mouse Y to a line number
        float ratio = (float) mouseY / Math.min(minimapHeight, docHeight);
        int targetLine = (int) (ratio * totalLines);

        // Scroll so this line is at the center of the viewport
        try {
            int offset = textArea.getLineStartOffset(Math.max(0, Math.min(targetLine, totalLines - 1)));
            var rect = textArea.modelToView2D(offset);
            if (rect != null) {
                int viewportHeight = scrollPane.getViewport().getHeight();
                int scrollY = (int) rect.getY() - viewportHeight / 2;
                scrollPane.getVerticalScrollBar().setValue(Math.max(0, scrollY));
            }
        } catch (Exception e) {
            // Best effort
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        int width = getWidth();
        int height = getHeight();
        int totalLines = textArea.getLineCount();
        int docHeight = totalLines * LINE_HEIGHT;

        // Draw background
        boolean dark = textArea.getBackground().getRed() < 100;
        g2.setColor(dark ? new Color(30, 30, 30) : new Color(240, 240, 240));
        g2.fillRect(0, 0, width, height);

        // Draw minimap lines (colored by content density)
        int renderHeight = Math.min(docHeight, height);
        if (needsRepaint || minimapImage == null
                || minimapImage.getHeight() != renderHeight
                || minimapImage.getWidth() != width) {
            renderMinimap(width, renderHeight, dark);
            needsRepaint = false;
        }

        if (minimapImage != null) {
            g2.drawImage(minimapImage, 0, 0, null);
        }

        // Draw viewport indicator
        if (scrollPane != null) {
            JViewport viewport = scrollPane.getViewport();
            int viewY = viewport.getViewPosition().y;
            int viewH = viewport.getHeight();
            int textHeight = textArea.getHeight();

            if (textHeight > 0) {
                // Scale the viewport indicator to match the minimap's rendering.
                // The minimap renders totalLines * LINE_HEIGHT pixels of content.
                int renderedHeight = Math.min(docHeight, height);
                float scale = (float) renderedHeight / textHeight;
                int indicatorY = (int) (viewY * scale);
                int indicatorH = Math.max(12, (int) (viewH * scale));
                // Clamp to panel bounds
                indicatorY = Math.min(indicatorY, height - indicatorH);
                indicatorY = Math.max(0, indicatorY);

                g2.setColor(dark ? new Color(255, 255, 255, 40) : new Color(0, 0, 0, 30));
                g2.fillRect(0, indicatorY, width, indicatorH);
                g2.setColor(dark ? new Color(255, 255, 255, 80) : new Color(0, 0, 0, 60));
                g2.drawRect(0, indicatorY, width - 1, indicatorH);
            }
        }

        g2.dispose();
    }

    /**
     * Renders the minimap image by scanning each line for content and drawing
     * thin colored lines representing code density.
     */
    private void renderMinimap(int width, int height, boolean dark) {
        if (height <= 0 || width <= 0) return;
        minimapImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = minimapImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        int totalLines = textArea.getLineCount();
        Color codeColor = dark ? new Color(150, 150, 150) : new Color(100, 100, 100);
        Color commentColor = dark ? new Color(80, 120, 80) : new Color(60, 140, 60);
        Color stringColor = dark ? new Color(120, 100, 80) : new Color(160, 100, 40);

        try {
            int docLength = textArea.getDocument().getLength();
            for (int line = 0; line < totalLines && line * LINE_HEIGHT < height; line++) {
                try {
                    int start = textArea.getLineStartOffset(line);
                    int end = textArea.getLineEndOffset(line) - 1;
                    if (end <= start || start >= docLength) continue;
                    end = Math.min(end, docLength);

                    String lineText = textArea.getText(start, end - start);
                    String trimmed = lineText.stripLeading();
                    if (trimmed.isEmpty()) continue;

                    int indent = lineText.length() - trimmed.length();
                    int x = (int) (indent * SCALE * 6);
                    int lineWidth = (int) (trimmed.length() * SCALE * 6);
                    lineWidth = Math.min(lineWidth, width - x);

                    // Simple heuristic coloring
                    Color c;
                    if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                        c = commentColor;
                    } else if (trimmed.contains("\"") || trimmed.contains("'")) {
                        c = stringColor;
                    } else {
                        c = codeColor;
                    }

                    g.setColor(c);
                    int y = line * LINE_HEIGHT;
                    g.fillRect(x, y, Math.max(1, lineWidth), LINE_HEIGHT);
                } catch (Exception ex) {
                    // Skip this line if offsets are stale
                }
            }
        } catch (Exception e) {
            // Best effort
        }

        g.dispose();
    }
}
