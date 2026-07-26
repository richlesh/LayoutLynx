/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * CustomTokenPainterFactory.java
 *
 * A token painter factory that uses {@link CustomWhitespaceTokenPainter}
 * when whitespace is visible, providing improved space and tab rendering.
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.DefaultTokenPainterFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenPainter;
import org.fife.ui.rsyntaxtextarea.TokenPainterFactory;


/**
 * A token painter factory that returns a {@link CustomWhitespaceTokenPainter}
 * when whitespace rendering is enabled, and delegates to the default factory
 * otherwise.
 */
public class CustomTokenPainterFactory implements TokenPainterFactory {

    private final DefaultTokenPainterFactory defaultFactory = new DefaultTokenPainterFactory();

    @Override
    public TokenPainter getTokenPainter(RSyntaxTextArea textArea) {
        if (textArea.isWhitespaceVisible()) {
            return new CustomWhitespaceTokenPainter();
        }
        return defaultFactory.getTokenPainter(textArea);
    }
}
