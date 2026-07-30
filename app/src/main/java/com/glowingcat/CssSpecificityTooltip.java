/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds a tooltip to CSS editors that shows the computed specificity of the selector
 * when hovering over a selector line. Specificity is displayed as (a, b, c) where:
 * <ul>
 *   <li>a = ID selectors (#id)</li>
 *   <li>b = class selectors (.class), attribute selectors ([attr]), pseudo-classes (:hover)</li>
 *   <li>c = type/element selectors (div) and pseudo-elements (::before)</li>
 * </ul>
 */
public class CssSpecificityTooltip {

    // Patterns for specificity calculation
    private static final Pattern ID_SELECTOR = Pattern.compile("#[a-zA-Z_][a-zA-Z0-9_-]*");
    private static final Pattern CLASS_SELECTOR = Pattern.compile("\\.[a-zA-Z_][a-zA-Z0-9_-]*");
    private static final Pattern ATTR_SELECTOR = Pattern.compile("\\[[^\\]]+\\]");
    private static final Pattern PSEUDO_ELEMENT = Pattern.compile("::?(?:before|after|first-line|first-letter|placeholder|selection|marker|backdrop)");
    private static final Pattern PSEUDO_CLASS = Pattern.compile(":(?!:)(?:hover|focus|active|visited|link|first-child|last-child|nth-child\\([^)]*\\)|nth-of-type\\([^)]*\\)|not\\([^)]*\\)|is\\([^)]*\\)|has\\([^)]*\\)|where\\([^)]*\\)|checked|disabled|enabled|empty|target|root|focus-within|focus-visible|first-of-type|last-of-type|only-child|only-of-type|placeholder-shown|required|optional|valid|invalid|in-range|out-of-range|read-only|read-write|lang\\([^)]*\\))");
    private static final Pattern TYPE_SELECTOR = Pattern.compile("(?:^|[\\s>+~,])\\s*([a-zA-Z][a-zA-Z0-9-]*)");
    private static final Pattern UNIVERSAL = Pattern.compile("\\*");

    /**
     * Installs the specificity tooltip on the given CSS text area.
     */
    public static void install(RSyntaxTextArea textArea) {
        textArea.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int offset = textArea.viewToModel2D(e.getPoint());
                if (offset < 0) {
                    textArea.setToolTipText(null);
                    return;
                }

                try {
                    // Get the line at the cursor position
                    Element root = textArea.getDocument().getDefaultRootElement();
                    int lineIndex = root.getElementIndex(offset);
                    Element lineElem = root.getElement(lineIndex);
                    int lineStart = lineElem.getStartOffset();
                    int lineEnd = lineElem.getEndOffset() - 1;
                    String lineText = textArea.getDocument().getText(lineStart, lineEnd - lineStart);

                    // Check if this line looks like a selector (contains { or is before a {)
                    String selector = extractSelector(textArea, lineIndex);
                    if (selector != null && !selector.isEmpty()) {
                        int[] specificity = computeSpecificity(selector);
                        textArea.setToolTipText(formatTooltip(selector.trim(), specificity));
                    } else {
                        textArea.setToolTipText(null);
                    }
                } catch (BadLocationException ex) {
                    textArea.setToolTipText(null);
                }
            }
        });

        // Enable tooltips
        ToolTipManager.sharedInstance().registerComponent(textArea);
    }

    /**
     * Extracts the selector for the rule containing the given line.
     * Returns null if the line is not part of a selector.
     */
    private static String extractSelector(RSyntaxTextArea textArea, int lineIndex) throws BadLocationException {
        Element root = textArea.getDocument().getDefaultRootElement();
        int totalLines = root.getElementCount();

        // Get line text
        Element lineElem = root.getElement(lineIndex);
        int lineStart = lineElem.getStartOffset();
        int lineEnd = lineElem.getEndOffset() - 1;
        String lineText = textArea.getDocument().getText(lineStart, lineEnd - lineStart).trim();

        // If the line itself contains '{', the selector is the part before '{'
        if (lineText.contains("{")) {
            int braceIdx = lineText.indexOf('{');
            String sel = lineText.substring(0, braceIdx).trim();
            if (!sel.isEmpty() && !sel.startsWith("@")) return sel;
            return null;
        }

        // Check if this is a selector line (no colon indicating a property, no closing brace)
        if (lineText.contains(":") && !lineText.startsWith(":") && !lineText.contains("{")) {
            // Likely a property declaration - look backwards for the selector
            return findSelectorBackward(textArea, lineIndex);
        }

        if (lineText.isEmpty() || lineText.equals("}") || lineText.startsWith("@") || lineText.startsWith("/*")) {
            return null;
        }

        // Could be a multi-line selector - look forward for the opening brace
        StringBuilder selector = new StringBuilder(lineText);
        for (int i = lineIndex + 1; i < totalLines && i < lineIndex + 5; i++) {
            Element elem = root.getElement(i);
            String text = textArea.getDocument().getText(elem.getStartOffset(),
                elem.getEndOffset() - elem.getStartOffset() - 1).trim();
            if (text.contains("{")) {
                int braceIdx = text.indexOf('{');
                if (braceIdx > 0) selector.append(" ").append(text, 0, braceIdx);
                String sel = selector.toString().trim();
                return sel.isEmpty() || sel.startsWith("@") ? null : sel;
            }
            selector.append(" ").append(text);
        }

        // Maybe just a bare selector on this line
        if (!lineText.contains(";") && !lineText.contains("}")) {
            return findSelectorBackward(textArea, lineIndex);
        }

        return null;
    }

    /**
     * Searches backward from the given line to find the selector of the enclosing rule block.
     */
    private static String findSelectorBackward(RSyntaxTextArea textArea, int lineIndex) throws BadLocationException {
        Element root = textArea.getDocument().getDefaultRootElement();
        for (int i = lineIndex - 1; i >= 0 && i > lineIndex - 20; i--) {
            Element elem = root.getElement(i);
            String text = textArea.getDocument().getText(elem.getStartOffset(),
                elem.getEndOffset() - elem.getStartOffset() - 1).trim();
            if (text.contains("{")) {
                int braceIdx = text.indexOf('{');
                String sel = text.substring(0, braceIdx).trim();
                if (!sel.isEmpty() && !sel.startsWith("@")) return sel;
                return null;
            }
            if (text.contains("}")) return null; // crossed into another rule
        }
        return null;
    }

    /**
     * Computes the specificity tuple (a, b, c) for a selector string.
     * Handles comma-separated selectors by returning the highest specificity.
     */
    static int[] computeSpecificity(String selector) {
        // Handle comma-separated selector lists - return highest
        String[] parts = selector.split(",");
        int[] highest = {0, 0, 0};
        for (String part : parts) {
            int[] spec = computeSingleSpecificity(part.trim());
            if (compareSpecificity(spec, highest) > 0) {
                highest = spec;
            }
        }
        return highest;
    }

    private static int[] computeSingleSpecificity(String selector) {
        int a = 0, b = 0, c = 0;

        // Remove :where(...) content (zero specificity)
        String cleaned = selector.replaceAll(":where\\([^)]*\\)", "");

        // Handle :is(), :not(), :has() — adopt highest specificity of their arguments
        Pattern functionalPseudo = Pattern.compile(":(is|not|has)\\(([^)]+)\\)");
        Matcher funcMatcher = functionalPseudo.matcher(cleaned);
        StringBuilder remaining = new StringBuilder();
        int lastEnd = 0;
        while (funcMatcher.find()) {
            remaining.append(cleaned, lastEnd, funcMatcher.start());
            // Compute specificity of arguments
            String args = funcMatcher.group(2);
            int[] argSpec = computeSpecificity(args);
            a += argSpec[0];
            b += argSpec[1];
            c += argSpec[2];
            lastEnd = funcMatcher.end();
        }
        remaining.append(cleaned.substring(lastEnd));
        cleaned = remaining.toString();

        // Count ID selectors
        Matcher idMatcher = ID_SELECTOR.matcher(cleaned);
        while (idMatcher.find()) a++;

        // Count class selectors
        Matcher classMatcher = CLASS_SELECTOR.matcher(cleaned);
        while (classMatcher.find()) b++;

        // Count attribute selectors
        Matcher attrMatcher = ATTR_SELECTOR.matcher(cleaned);
        while (attrMatcher.find()) b++;

        // Count pseudo-elements (before counting pseudo-classes to avoid overlap)
        Matcher peMatcher = PSEUDO_ELEMENT.matcher(cleaned);
        while (peMatcher.find()) c++;
        // Remove pseudo-elements so they don't get counted as pseudo-classes
        cleaned = PSEUDO_ELEMENT.matcher(cleaned).replaceAll("");

        // Count pseudo-classes
        Matcher pcMatcher = PSEUDO_CLASS.matcher(cleaned);
        while (pcMatcher.find()) b++;

        // Count type/element selectors
        // First strip IDs, classes, attrs, pseudo-classes to avoid false matches
        String forTypes = cleaned;
        forTypes = ID_SELECTOR.matcher(forTypes).replaceAll("");
        forTypes = CLASS_SELECTOR.matcher(forTypes).replaceAll("");
        forTypes = ATTR_SELECTOR.matcher(forTypes).replaceAll("");
        forTypes = Pattern.compile(":[a-zA-Z-]+(\\([^)]*\\))?").matcher(forTypes).replaceAll("");
        // Now find bare element names
        Pattern bareElement = Pattern.compile("(?:^|[\\s>+~])\\s*([a-zA-Z][a-zA-Z0-9-]*)");
        Matcher typeMatcher = bareElement.matcher(forTypes);
        while (typeMatcher.find()) {
            String name = typeMatcher.group(1);
            // Skip known non-element keywords
            if (!name.equals("and") && !name.equals("or") && !name.equals("not")) {
                c++;
            }
        }

        return new int[]{a, b, c};
    }

    private static int compareSpecificity(int[] a, int[] b) {
        if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
        if (a[1] != b[1]) return Integer.compare(a[1], b[1]);
        return Integer.compare(a[2], b[2]);
    }

    private static String formatTooltip(String selector, int[] specificity) {
        // Truncate long selectors for display
        String display = selector.length() > 60 ? selector.substring(0, 57) + "..." : selector;
        return "<html><b>Specificity:</b> (" + specificity[0] + ", " + specificity[1] + ", " + specificity[2] + ")"
            + "<br><code>" + escapeHtml(display) + "</code></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
