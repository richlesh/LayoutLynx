/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * CustomWhitespaceTokenPainter.java
 *
 * Renders visible whitespace characters with improved visual indicators:
 * spaces as centered dots and tabs as lines with arrowheads.
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.VisibleWhitespaceTokenPainter;

import java.awt.*;


/**
 * A custom token painter that renders spaces as small centered dots and tabs
 * as horizontal lines with arrowheads pointing right. Extends
 * {@link VisibleWhitespaceTokenPainter} to override only the whitespace
 * rendering methods.
 */
public class CustomWhitespaceTokenPainter extends VisibleWhitespaceTokenPainter {

    /**
     * Draws a small filled circle (dot) centered in the space character's area.
     *
     * @param g      the graphics context
     * @param x      the x-value at which to paint
     * @param y      the y-value of the current line
     * @param ascent the ascent of the current font
     * @param width  the width of the space character
     * @param height the height of the current line of text being painted
     */
    @Override
    protected void paintSpaceText(Graphics2D g, float x, float y, int ascent,
                                  int width, int height) {
        int dotSize = Math.max(3, height / 10 + 1);
        int dotX = (int)(x - width / 2f) - dotSize / 2;
        int dotY = (int)(y - ascent + height / 2f) - dotSize / 2;
        g.fillOval(dotX, dotY, dotSize, dotSize);
    }

    /**
     * Draws a horizontal line with an arrowhead on the right end, representing
     * a tab character.
     *
     * @param g           the graphics context
     * @param x           the x-value at which to paint
     * @param y           the y-value of the current line
     * @param nextTabStop where the next tab stop would start
     * @param ascent      the ascent of the current font
     * @param height      the height of the line of text being painted
     */
    @Override
    protected void paintTabText(Graphics2D g, float x, float y,
                                float nextTabStop, int ascent, int height) {
        int x1 = (int) x;
        int x2 = (int) nextTabStop - 2;
        if (x2 <= x1) return;

        int halfHeight = height / 2;
        int ymid = (int) y - ascent + halfHeight;

        // Draw the horizontal line
        g.drawLine(x1, ymid, x2, ymid);

        // Draw the arrowhead on the right end
        int arrowSize = Math.max(3, height / 5);
        g.drawLine(x2, ymid, x2 - arrowSize, ymid - arrowSize);
        g.drawLine(x2, ymid, x2 - arrowSize, ymid + arrowSize);
    }
}
