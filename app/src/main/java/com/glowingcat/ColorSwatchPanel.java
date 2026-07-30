/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A gutter panel that displays color swatches next to lines containing CSS color values.
 * Clicking a swatch opens a color picker to edit the color inline.
 * Supports multiple swatches per line (e.g., gradients with multiple color stops).
 * 
 * This panel is completely locked to the editor's scroll position — it reads the
 * editor viewport offset directly and paints swatches at viewport-relative positions.
 * Mouse wheel events are forwarded to the editor's scroll pane.
 */
public class ColorSwatchPanel extends JPanel {

    private static final int SWATCH_SIZE = 10;
    private static final int SWATCH_GAP = 2;
    private static final int MAX_SWATCHES_PER_LINE = 2;
    private static final int PANEL_WIDTH = (SWATCH_SIZE + SWATCH_GAP) * MAX_SWATCHES_PER_LINE + 4;

    /** Matches #rgb, #rrggbb, #rgba, #rrggbbaa */
    private static final Pattern HEX_COLOR = Pattern.compile(
        "#([0-9a-fA-F]{3,8})\\b");

    /** Matches rgb(...) and rgba(...) */
    private static final Pattern RGB_COLOR = Pattern.compile(
        "rgba?\\s*\\(\\s*(\\d{1,3})\\s*[,\\s]\\s*(\\d{1,3})\\s*[,\\s]\\s*(\\d{1,3})(?:\\s*[,/\\s]\\s*([\\d.]+%?))?\\s*\\)");

    private final RSyntaxTextArea textArea;
    private final List<SwatchInfo> swatches = new ArrayList<>();
    private RTextScrollPane editorScrollPane;

    private record SwatchInfo(int line, int textAreaY, int xIndex, Color color, int startOffset, int endOffset) {}

    public ColorSwatchPanel(RSyntaxTextArea textArea) {
        this.textArea = textArea;
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setMinimumSize(new Dimension(PANEL_WIDTH, 0));
        setBackground(new Color(240, 240, 240));

        // Rebuild swatches when document changes
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private final Timer debounce = new Timer(150, e -> rebuildSwatches());
            { debounce.setRepeats(false); }
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });

        // Handle clicks on swatches
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewportY = getViewportY();
                for (SwatchInfo swatch : swatches) {
                    int sx = 2 + swatch.xIndex * (SWATCH_SIZE + SWATCH_GAP);
                    int sy = swatch.textAreaY - viewportY;
                    if (e.getX() >= sx && e.getX() <= sx + SWATCH_SIZE &&
                        e.getY() >= sy && e.getY() <= sy + SWATCH_SIZE) {
                        openColorPicker(swatch);
                        break;
                    }
                }
            }
        });

        // Forward mouse wheel events to the editor scroll pane
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (editorScrollPane != null) {
                    JScrollBar vsb = editorScrollPane.getVerticalScrollBar();
                    int units = e.getUnitsToScroll() * vsb.getUnitIncrement();
                    vsb.setValue(vsb.getValue() + units);
                }
            }
        });
    }

    /**
     * Sets the editor scroll pane reference for forwarding wheel events and syncing.
     */
    public void setEditorScrollPane(RTextScrollPane scrollPane) {
        this.editorScrollPane = scrollPane;
        // Repaint this panel whenever the editor scrolls
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> repaint());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::rebuildSwatches);
    }

    private int getViewportY() {
        if (editorScrollPane != null) {
            return editorScrollPane.getViewport().getViewPosition().y;
        }
        return 0;
    }

    /**
     * Scans all lines for color values and builds the swatch list.
     * Swatch Y positions are stored in text-area coordinate space.
     */
    void rebuildSwatches() {
        swatches.clear();

        if (!textArea.isDisplayable()) {
            repaint();
            return;
        }

        try {
            Element root = textArea.getDocument().getDefaultRootElement();
            int totalLines = root.getElementCount();

            for (int line = 0; line < totalLines; line++) {
                Element lineElem = root.getElement(line);
                int lineStart = lineElem.getStartOffset();
                int lineEnd = lineElem.getEndOffset() - 1;
                String lineText = textArea.getDocument().getText(lineStart, lineEnd - lineStart);

                int colorIndex = 0;

                // Find all hex colors on this line
                Matcher hexMatcher = HEX_COLOR.matcher(lineText);
                while (hexMatcher.find() && colorIndex < MAX_SWATCHES_PER_LINE) {
                    Color color = parseHexColor(hexMatcher.group(1));
                    if (color != null) {
                        int y = getYForLine(line);
                        if (y >= 0) {
                            swatches.add(new SwatchInfo(line, y, colorIndex, color,
                                lineStart + hexMatcher.start(), lineStart + hexMatcher.end()));
                            colorIndex++;
                        }
                    }
                }

                // Find all rgb/rgba colors on this line
                Matcher rgbMatcher = RGB_COLOR.matcher(lineText);
                while (rgbMatcher.find() && colorIndex < MAX_SWATCHES_PER_LINE) {
                    Color color = parseRgbColor(rgbMatcher);
                    if (color != null) {
                        int y = getYForLine(line);
                        if (y >= 0) {
                            swatches.add(new SwatchInfo(line, y, colorIndex, color,
                                lineStart + rgbMatcher.start(), lineStart + rgbMatcher.end()));
                            colorIndex++;
                        }
                    }
                }
            }
        } catch (BadLocationException e) {
            // Best effort
        }

        repaint();
    }

    private int getYForLine(int line) {
        try {
            var rect2D = textArea.modelToView2D(
                textArea.getDocument().getDefaultRootElement().getElement(line).getStartOffset());
            if (rect2D == null) return -1;
            Rectangle rect = rect2D.getBounds();
            return rect.y + (rect.height - SWATCH_SIZE) / 2;
        } catch (BadLocationException e) {
            return -1;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int viewportY = getViewportY();
        int viewportHeight = getHeight();

        for (SwatchInfo swatch : swatches) {
            int x = 2 + swatch.xIndex * (SWATCH_SIZE + SWATCH_GAP);
            int y = swatch.textAreaY - viewportY;

            // Only paint if visible
            if (y + SWATCH_SIZE < 0 || y > viewportHeight) continue;

            // Fill with the color
            g2.setColor(swatch.color);
            g2.fillRect(x, y, SWATCH_SIZE, SWATCH_SIZE);

            // Border
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(x, y, SWATCH_SIZE, SWATCH_SIZE);
        }

        g2.dispose();
    }

    private void openColorPicker(SwatchInfo swatch) {
        Color chosen = JColorChooser.showDialog(
            SwingUtilities.getWindowAncestor(this), "Edit Color", swatch.color);
        if (chosen == null) return;

        try {
            String original = textArea.getDocument().getText(
                swatch.startOffset, swatch.endOffset - swatch.startOffset);

            String replacement;
            if (original.startsWith("rgba")) {
                Matcher m = RGB_COLOR.matcher(original);
                if (m.matches() && m.group(4) != null) {
                    replacement = String.format("rgba(%d, %d, %d, %s)",
                        chosen.getRed(), chosen.getGreen(), chosen.getBlue(), m.group(4));
                } else {
                    replacement = String.format("rgba(%d, %d, %d, 1)",
                        chosen.getRed(), chosen.getGreen(), chosen.getBlue());
                }
            } else if (original.startsWith("rgb")) {
                replacement = String.format("rgb(%d, %d, %d)",
                    chosen.getRed(), chosen.getGreen(), chosen.getBlue());
            } else {
                replacement = String.format("#%02x%02x%02x",
                    chosen.getRed(), chosen.getGreen(), chosen.getBlue());
            }

            textArea.getDocument().remove(swatch.startOffset, swatch.endOffset - swatch.startOffset);
            textArea.getDocument().insertString(swatch.startOffset, replacement, null);
        } catch (BadLocationException e) {
            // Best effort
        }
    }

    private static Color parseHexColor(String hex) {
        try {
            if (hex.length() == 3) {
                int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                return new Color(r, g, b);
            } else if (hex.length() == 4) {
                int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                int a = Integer.parseInt(hex.substring(3, 4), 16) * 17;
                return new Color(r, g, b, a);
            } else if (hex.length() == 6) {
                return Color.decode("#" + hex);
            } else if (hex.length() == 8) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                int a = Integer.parseInt(hex.substring(6, 8), 16);
                return new Color(r, g, b, a);
            }
        } catch (NumberFormatException e) {
            // invalid hex
        }
        return null;
    }

    private static Color parseRgbColor(Matcher m) {
        try {
            int r = Integer.parseInt(m.group(1));
            int g = Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3));
            if (r > 255 || g > 255 || b > 255) return null;
            if (m.group(4) != null) {
                String alphaStr = m.group(4);
                float a;
                if (alphaStr.endsWith("%")) {
                    a = Float.parseFloat(alphaStr.replace("%", "")) / 100f;
                } else {
                    a = Float.parseFloat(alphaStr);
                }
                return new Color(r, g, b, Math.round(a * 255));
            }
            return new Color(r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
