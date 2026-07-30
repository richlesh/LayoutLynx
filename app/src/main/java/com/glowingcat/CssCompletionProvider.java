/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.autocomplete.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides CSS property name and value auto-completion for RSyntaxTextArea.
 * <p>
 * When the caret is inside a rule block (between { and }), typing triggers
 * property name suggestions. After a colon, value suggestions for the
 * current property are offered.
 */
class CssCompletionProvider extends DefaultCompletionProvider {

    /** Map of CSS property names to their common values. */
    private static final Map<String, String[]> CSS_PROPERTIES = new LinkedHashMap<>();

    static {
        // Layout
        CSS_PROPERTIES.put("display", new String[]{"none", "block", "inline", "inline-block", "flex", "inline-flex", "grid", "inline-grid", "table", "contents"});
        CSS_PROPERTIES.put("position", new String[]{"static", "relative", "absolute", "fixed", "sticky"});
        CSS_PROPERTIES.put("float", new String[]{"none", "left", "right", "inline-start", "inline-end"});
        CSS_PROPERTIES.put("clear", new String[]{"none", "left", "right", "both"});
        CSS_PROPERTIES.put("overflow", new String[]{"visible", "hidden", "scroll", "auto", "clip"});
        CSS_PROPERTIES.put("overflow-x", new String[]{"visible", "hidden", "scroll", "auto", "clip"});
        CSS_PROPERTIES.put("overflow-y", new String[]{"visible", "hidden", "scroll", "auto", "clip"});
        CSS_PROPERTIES.put("visibility", new String[]{"visible", "hidden", "collapse"});
        CSS_PROPERTIES.put("z-index", new String[]{"auto", "0", "1", "10", "100", "999"});
        CSS_PROPERTIES.put("box-sizing", new String[]{"content-box", "border-box"});

        // Flexbox
        CSS_PROPERTIES.put("flex-direction", new String[]{"row", "row-reverse", "column", "column-reverse"});
        CSS_PROPERTIES.put("flex-wrap", new String[]{"nowrap", "wrap", "wrap-reverse"});
        CSS_PROPERTIES.put("flex-flow", new String[]{"row nowrap", "row wrap", "column nowrap", "column wrap"});
        CSS_PROPERTIES.put("justify-content", new String[]{"flex-start", "flex-end", "center", "space-between", "space-around", "space-evenly", "start", "end"});
        CSS_PROPERTIES.put("align-items", new String[]{"stretch", "flex-start", "flex-end", "center", "baseline", "start", "end"});
        CSS_PROPERTIES.put("align-content", new String[]{"stretch", "flex-start", "flex-end", "center", "space-between", "space-around", "space-evenly"});
        CSS_PROPERTIES.put("align-self", new String[]{"auto", "stretch", "flex-start", "flex-end", "center", "baseline"});
        CSS_PROPERTIES.put("flex", new String[]{"none", "auto", "1", "0 1 auto", "1 1 auto", "1 0 auto"});
        CSS_PROPERTIES.put("flex-grow", new String[]{"0", "1"});
        CSS_PROPERTIES.put("flex-shrink", new String[]{"0", "1"});
        CSS_PROPERTIES.put("flex-basis", new String[]{"auto", "0", "100%", "fit-content", "max-content", "min-content"});
        CSS_PROPERTIES.put("order", new String[]{"0", "1", "-1"});
        CSS_PROPERTIES.put("gap", new String[]{"0", "4px", "8px", "16px", "1rem", "2rem"});
        CSS_PROPERTIES.put("row-gap", new String[]{"0", "4px", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("column-gap", new String[]{"0", "4px", "8px", "16px", "1rem"});

        // Grid
        CSS_PROPERTIES.put("grid-template-columns", new String[]{"none", "1fr", "repeat(2, 1fr)", "repeat(3, 1fr)", "repeat(auto-fill, minmax(200px, 1fr))", "repeat(auto-fit, minmax(200px, 1fr))"});
        CSS_PROPERTIES.put("grid-template-rows", new String[]{"none", "auto", "1fr", "repeat(3, 1fr)", "minmax(100px, auto)"});
        CSS_PROPERTIES.put("grid-column", new String[]{"auto", "1", "1 / 2", "1 / -1", "span 2"});
        CSS_PROPERTIES.put("grid-row", new String[]{"auto", "1", "1 / 2", "1 / -1", "span 2"});
        CSS_PROPERTIES.put("grid-area", new String[]{"auto"});
        CSS_PROPERTIES.put("place-items", new String[]{"center", "start", "end", "stretch"});
        CSS_PROPERTIES.put("place-content", new String[]{"center", "start", "end", "stretch", "space-between", "space-around"});

        // Box Model
        CSS_PROPERTIES.put("width", new String[]{"auto", "100%", "fit-content", "max-content", "min-content"});
        CSS_PROPERTIES.put("height", new String[]{"auto", "100%", "100vh", "fit-content"});
        CSS_PROPERTIES.put("min-width", new String[]{"0", "auto", "100%", "fit-content", "max-content", "min-content"});
        CSS_PROPERTIES.put("max-width", new String[]{"none", "100%", "fit-content", "max-content", "min-content"});
        CSS_PROPERTIES.put("min-height", new String[]{"0", "auto", "100%", "100vh"});
        CSS_PROPERTIES.put("max-height", new String[]{"none", "100%", "100vh"});
        CSS_PROPERTIES.put("margin", new String[]{"0", "auto", "0 auto", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("margin-top", new String[]{"0", "auto", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("margin-right", new String[]{"0", "auto", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("margin-bottom", new String[]{"0", "auto", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("margin-left", new String[]{"0", "auto", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("padding", new String[]{"0", "4px", "8px", "16px", "1rem", "2rem"});
        CSS_PROPERTIES.put("padding-top", new String[]{"0", "4px", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("padding-right", new String[]{"0", "4px", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("padding-bottom", new String[]{"0", "4px", "8px", "16px", "1rem"});
        CSS_PROPERTIES.put("padding-left", new String[]{"0", "4px", "8px", "16px", "1rem"});

        // Border
        CSS_PROPERTIES.put("border", new String[]{"none", "1px solid black", "1px solid #ccc", "2px solid", "1px dashed", "1px dotted"});
        CSS_PROPERTIES.put("border-top", new String[]{"none", "1px solid", "1px solid #ccc"});
        CSS_PROPERTIES.put("border-right", new String[]{"none", "1px solid", "1px solid #ccc"});
        CSS_PROPERTIES.put("border-bottom", new String[]{"none", "1px solid", "1px solid #ccc"});
        CSS_PROPERTIES.put("border-left", new String[]{"none", "1px solid", "1px solid #ccc"});
        CSS_PROPERTIES.put("border-width", new String[]{"0", "1px", "2px", "thin", "medium", "thick"});
        CSS_PROPERTIES.put("border-style", new String[]{"none", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset"});
        CSS_PROPERTIES.put("border-color", new String[]{"currentColor", "transparent", "inherit", "black", "#ccc"});
        CSS_PROPERTIES.put("border-radius", new String[]{"0", "2px", "4px", "8px", "50%", "9999px"});
        CSS_PROPERTIES.put("border-collapse", new String[]{"separate", "collapse"});
        CSS_PROPERTIES.put("outline", new String[]{"none", "1px solid", "2px solid blue"});
        CSS_PROPERTIES.put("outline-offset", new String[]{"0", "2px", "4px"});

        // Colors & Backgrounds
        CSS_PROPERTIES.put("color", new String[]{"inherit", "currentColor", "black", "white", "#333", "#666", "transparent"});
        CSS_PROPERTIES.put("background", new String[]{"none", "transparent", "white", "#f5f5f5", "linear-gradient(to bottom, #fff, #eee)"});
        CSS_PROPERTIES.put("background-color", new String[]{"transparent", "inherit", "white", "black", "#f5f5f5"});
        CSS_PROPERTIES.put("background-image", new String[]{"none", "url()", "linear-gradient()", "radial-gradient()"});
        CSS_PROPERTIES.put("background-repeat", new String[]{"repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round"});
        CSS_PROPERTIES.put("background-position", new String[]{"center", "top", "bottom", "left", "right", "center center", "top left"});
        CSS_PROPERTIES.put("background-size", new String[]{"auto", "cover", "contain", "100% 100%"});
        CSS_PROPERTIES.put("background-attachment", new String[]{"scroll", "fixed", "local"});
        CSS_PROPERTIES.put("opacity", new String[]{"1", "0.9", "0.8", "0.5", "0.3", "0"});

        // Typography
        CSS_PROPERTIES.put("font-family", new String[]{"inherit", "sans-serif", "serif", "monospace", "system-ui", "'Segoe UI', sans-serif", "'Helvetica Neue', Arial, sans-serif"});
        CSS_PROPERTIES.put("font-size", new String[]{"inherit", "12px", "14px", "16px", "18px", "24px", "1rem", "1.25rem", "1.5rem", "2rem", "small", "medium", "large"});
        CSS_PROPERTIES.put("font-weight", new String[]{"normal", "bold", "lighter", "bolder", "100", "200", "300", "400", "500", "600", "700", "800", "900"});
        CSS_PROPERTIES.put("font-style", new String[]{"normal", "italic", "oblique"});
        CSS_PROPERTIES.put("font-variant", new String[]{"normal", "small-caps"});
        CSS_PROPERTIES.put("line-height", new String[]{"normal", "1", "1.2", "1.4", "1.5", "1.6", "2"});
        CSS_PROPERTIES.put("letter-spacing", new String[]{"normal", "0.5px", "1px", "0.05em", "0.1em"});
        CSS_PROPERTIES.put("word-spacing", new String[]{"normal", "2px", "4px"});
        CSS_PROPERTIES.put("text-align", new String[]{"left", "right", "center", "justify", "start", "end"});
        CSS_PROPERTIES.put("text-decoration", new String[]{"none", "underline", "line-through", "overline"});
        CSS_PROPERTIES.put("text-transform", new String[]{"none", "capitalize", "uppercase", "lowercase"});
        CSS_PROPERTIES.put("text-indent", new String[]{"0", "1em", "2em"});
        CSS_PROPERTIES.put("text-overflow", new String[]{"clip", "ellipsis"});
        CSS_PROPERTIES.put("white-space", new String[]{"normal", "nowrap", "pre", "pre-wrap", "pre-line", "break-spaces"});
        CSS_PROPERTIES.put("word-break", new String[]{"normal", "break-all", "keep-all", "break-word"});
        CSS_PROPERTIES.put("overflow-wrap", new String[]{"normal", "break-word", "anywhere"});
        CSS_PROPERTIES.put("vertical-align", new String[]{"baseline", "top", "middle", "bottom", "text-top", "text-bottom", "sub", "super"});

        // Positioning
        CSS_PROPERTIES.put("top", new String[]{"auto", "0", "50%", "100%"});
        CSS_PROPERTIES.put("right", new String[]{"auto", "0", "50%", "100%"});
        CSS_PROPERTIES.put("bottom", new String[]{"auto", "0", "50%", "100%"});
        CSS_PROPERTIES.put("left", new String[]{"auto", "0", "50%", "100%"});
        CSS_PROPERTIES.put("inset", new String[]{"0", "auto"});

        // Transforms & Transitions
        CSS_PROPERTIES.put("transform", new String[]{"none", "translateX(0)", "translateY(0)", "translate(0, 0)", "scale(1)", "rotate(0deg)", "skew(0deg)"});
        CSS_PROPERTIES.put("transform-origin", new String[]{"center", "top left", "top right", "bottom left", "bottom right", "50% 50%"});
        CSS_PROPERTIES.put("transition", new String[]{"none", "all 0.3s ease", "all 0.2s", "opacity 0.3s", "transform 0.3s ease"});
        CSS_PROPERTIES.put("transition-property", new String[]{"none", "all", "opacity", "transform", "background-color", "color"});
        CSS_PROPERTIES.put("transition-duration", new String[]{"0s", "0.2s", "0.3s", "0.5s", "1s"});
        CSS_PROPERTIES.put("transition-timing-function", new String[]{"ease", "linear", "ease-in", "ease-out", "ease-in-out", "cubic-bezier(0.4, 0, 0.2, 1)"});
        CSS_PROPERTIES.put("transition-delay", new String[]{"0s", "0.1s", "0.2s", "0.5s"});
        CSS_PROPERTIES.put("animation", new String[]{"none"});
        CSS_PROPERTIES.put("animation-name", new String[]{"none"});
        CSS_PROPERTIES.put("animation-duration", new String[]{"0s", "0.3s", "0.5s", "1s", "2s"});
        CSS_PROPERTIES.put("animation-timing-function", new String[]{"ease", "linear", "ease-in", "ease-out", "ease-in-out"});
        CSS_PROPERTIES.put("animation-iteration-count", new String[]{"1", "2", "infinite"});
        CSS_PROPERTIES.put("animation-direction", new String[]{"normal", "reverse", "alternate", "alternate-reverse"});
        CSS_PROPERTIES.put("animation-fill-mode", new String[]{"none", "forwards", "backwards", "both"});

        // Effects
        CSS_PROPERTIES.put("box-shadow", new String[]{"none", "0 1px 3px rgba(0,0,0,0.12)", "0 2px 4px rgba(0,0,0,0.1)", "0 4px 6px rgba(0,0,0,0.1)", "0 10px 15px rgba(0,0,0,0.1)", "inset 0 2px 4px rgba(0,0,0,0.06)"});
        CSS_PROPERTIES.put("text-shadow", new String[]{"none", "1px 1px 2px rgba(0,0,0,0.3)", "0 0 4px rgba(0,0,0,0.5)"});
        CSS_PROPERTIES.put("filter", new String[]{"none", "blur(4px)", "brightness(0.8)", "contrast(1.2)", "grayscale(1)", "opacity(0.5)", "saturate(1.5)", "drop-shadow(2px 2px 4px rgba(0,0,0,0.3))"});
        CSS_PROPERTIES.put("backdrop-filter", new String[]{"none", "blur(10px)", "blur(20px)", "saturate(180%) blur(20px)"});
        CSS_PROPERTIES.put("mix-blend-mode", new String[]{"normal", "multiply", "screen", "overlay", "darken", "lighten"});

        // Lists
        CSS_PROPERTIES.put("list-style", new String[]{"none", "disc", "circle", "square", "decimal"});
        CSS_PROPERTIES.put("list-style-type", new String[]{"none", "disc", "circle", "square", "decimal", "lower-alpha", "upper-alpha", "lower-roman", "upper-roman"});
        CSS_PROPERTIES.put("list-style-position", new String[]{"outside", "inside"});

        // Table
        CSS_PROPERTIES.put("table-layout", new String[]{"auto", "fixed"});
        CSS_PROPERTIES.put("caption-side", new String[]{"top", "bottom"});
        CSS_PROPERTIES.put("empty-cells", new String[]{"show", "hide"});

        // Cursor & Interaction
        CSS_PROPERTIES.put("cursor", new String[]{"auto", "default", "pointer", "move", "text", "wait", "help", "not-allowed", "crosshair", "grab", "grabbing"});
        CSS_PROPERTIES.put("pointer-events", new String[]{"auto", "none"});
        CSS_PROPERTIES.put("user-select", new String[]{"auto", "none", "text", "all"});
        CSS_PROPERTIES.put("resize", new String[]{"none", "both", "horizontal", "vertical"});
        CSS_PROPERTIES.put("touch-action", new String[]{"auto", "none", "pan-x", "pan-y", "manipulation"});
        CSS_PROPERTIES.put("scroll-behavior", new String[]{"auto", "smooth"});

        // Content & Counters
        CSS_PROPERTIES.put("content", new String[]{"none", "\"\"", "attr()", "open-quote", "close-quote", "counter()"});
        CSS_PROPERTIES.put("quotes", new String[]{"none", "auto"});

        // Misc
        CSS_PROPERTIES.put("aspect-ratio", new String[]{"auto", "1", "16 / 9", "4 / 3", "1 / 1"});
        CSS_PROPERTIES.put("object-fit", new String[]{"fill", "contain", "cover", "none", "scale-down"});
        CSS_PROPERTIES.put("object-position", new String[]{"center", "top", "bottom", "left", "right"});
        CSS_PROPERTIES.put("clip-path", new String[]{"none", "circle()", "ellipse()", "polygon()", "inset()"});
        CSS_PROPERTIES.put("will-change", new String[]{"auto", "transform", "opacity", "scroll-position"});
        CSS_PROPERTIES.put("contain", new String[]{"none", "layout", "paint", "size", "content", "strict"});
        CSS_PROPERTIES.put("isolation", new String[]{"auto", "isolate"});
        CSS_PROPERTIES.put("appearance", new String[]{"none", "auto"});
        CSS_PROPERTIES.put("accent-color", new String[]{"auto"});
        CSS_PROPERTIES.put("caret-color", new String[]{"auto", "currentColor", "transparent"});
        CSS_PROPERTIES.put("direction", new String[]{"ltr", "rtl"});
        CSS_PROPERTIES.put("writing-mode", new String[]{"horizontal-tb", "vertical-rl", "vertical-lr"});
        CSS_PROPERTIES.put("unicode-bidi", new String[]{"normal", "embed", "isolate", "bidi-override", "isolate-override"});
        CSS_PROPERTIES.put("columns", new String[]{"auto", "2", "3"});
        CSS_PROPERTIES.put("column-count", new String[]{"auto", "2", "3", "4"});
        CSS_PROPERTIES.put("column-width", new String[]{"auto", "200px", "300px"});
        CSS_PROPERTIES.put("column-rule", new String[]{"none", "1px solid #ccc"});
    }

    /** Whether we are currently in "value" context (after a colon). */
    private boolean inValueContext;

    /** The property name when completing values. */
    private String currentProperty;

    CssCompletionProvider() {
        // Don't set auto-activation rules here — we control activation via isAutoActivateOkay
        setAutoActivationRules(false, null);
    }

    @Override
    public String getAlreadyEnteredText(JTextComponent comp) {
        String text = comp.getText();
        int caret = comp.getCaretPosition();
        if (caret <= 0 || caret > text.length()) return "";

        // Determine context: are we inside a rule block?
        String before = text.substring(0, caret);
        int lastOpen = before.lastIndexOf('{');
        int lastClose = before.lastIndexOf('}');
        if (lastOpen < 0 || lastClose > lastOpen) {
            // Not inside a rule block — no completions
            inValueContext = false;
            currentProperty = null;
            return "";
        }

        // Check if we're after a colon (value context) or before one (property context)
        String insideBlock = before.substring(lastOpen + 1);
        int lastSemicolon = insideBlock.lastIndexOf(';');
        String currentDecl = lastSemicolon >= 0 ? insideBlock.substring(lastSemicolon + 1) : insideBlock;

        int colonIdx = currentDecl.indexOf(':');
        if (colonIdx >= 0) {
            // Value context — extract the property name and the partial value
            inValueContext = true;
            currentProperty = currentDecl.substring(0, colonIdx).trim();
            String afterColon = currentDecl.substring(colonIdx + 1);
            // Return the trimmed partial value as the already-entered text
            return afterColon.stripLeading();
        } else {
            // Property context
            inValueContext = false;
            currentProperty = null;
            return currentDecl.stripLeading();
        }
    }

    @Override
    public List<Completion> getCompletionByInputText(String inputText) {
        return getCompletionsImpl(inputText);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Completion> getCompletions(JTextComponent comp) {
        String entered = getAlreadyEnteredText(comp);
        return getCompletionsImpl(entered);
    }

    private List<Completion> getCompletionsImpl(String entered) {
        List<Completion> completions = new ArrayList<>();

        // Require at least one alpha character to have been typed
        if (entered.isEmpty() || !containsAlpha(entered)) {
            return completions;
        }

        String lower = entered.toLowerCase();

        if (inValueContext && currentProperty != null) {
            // Offer value completions for the current property
            String[] values = CSS_PROPERTIES.get(currentProperty.toLowerCase());
            if (values != null) {
                for (String value : values) {
                    if (value.toLowerCase().startsWith(lower)) {
                        completions.add(new BasicCompletion(this, value));
                    }
                }
            }
            // Also offer "inherit", "initial", "unset", "revert" as universal values
            for (String universal : new String[]{"inherit", "initial", "unset", "revert"}) {
                if (universal.startsWith(lower)) {
                    completions.add(new BasicCompletion(this, universal));
                }
            }
        } else {
            // Offer property name completions (with ": " appended)
            for (String prop : CSS_PROPERTIES.keySet()) {
                if (prop.startsWith(lower)) {
                    BasicCompletion c = new BasicCompletion(this, prop + ": ");
                    c.setShortDescription("CSS property");
                    completions.add(c);
                }
            }
        }

        return completions;
    }

    /** Returns true if the string contains at least one alphabetic character. */
    private static boolean containsAlpha(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    @Override
    public boolean isAutoActivateOkay(JTextComponent tc) {
        // Only auto-activate if we're inside a rule block and have typed alpha chars
        String text = tc.getText();
        int caret = tc.getCaretPosition();
        if (caret <= 0 || caret > text.length()) return false;

        // Must be inside a rule block
        String before = text.substring(0, caret);
        int lastOpen = before.lastIndexOf('{');
        int lastClose = before.lastIndexOf('}');
        if (lastOpen < 0 || lastClose > lastOpen) return false;

        // Get the current token being typed
        String insideBlock = before.substring(lastOpen + 1);
        int lastSemicolon = insideBlock.lastIndexOf(';');
        String currentDecl = lastSemicolon >= 0 ? insideBlock.substring(lastSemicolon + 1) : insideBlock;

        int colonIdx = currentDecl.indexOf(':');
        String typed = colonIdx >= 0 ? currentDecl.substring(colonIdx + 1).stripLeading() : currentDecl.stripLeading();

        // Require at least 2 alpha characters before showing popup
        int alphaCount = 0;
        for (int i = 0; i < typed.length(); i++) {
            if (Character.isLetter(typed.charAt(i))) alphaCount++;
            if (alphaCount >= 2) return true;
        }
        return false;
    }

    /**
     * Installs CSS auto-completion on the given text area.
     */
    static void install(RSyntaxTextArea textArea) {
        CssCompletionProvider provider = new CssCompletionProvider();
        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(300);
        ac.setShowDescWindow(false);
        ac.setAutoCompleteSingleChoices(false);
        ac.install(textArea);
    }
}
