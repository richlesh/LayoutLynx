/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel that renders an HTML/CSS preview using JavaFX WebView.
 * Text selection is disabled and clicking an element shows its computed styles.
 * <p>
 * Normal click: shows only non-default properties (diffed against an isolated iframe baseline).
 * Alt/Option click: shows the full computed property list.
 * <p>
 * Hovering over a property in the dialog shows a tooltip with the stylesheet filename
 * and selector that set that property.
 */
public class PreviewPanel extends JPanel {

    private final JFXPanel fxPanel;
    private WebView webView;
    private WebEngine webEngine;

    /** The currently active breakpoint width, or -1 for "auto" (fill available space). */
    private int activeBreakpoint = -1;

    /** Buttons for the breakpoint toolbar so we can update their selection state. */
    private final List<JToggleButton> breakpointButtons = new ArrayList<>();

    /** Remembered column widths for the computed styles table. */
    private int[] savedColumnWidths = {180, 200, 220};

    /** Remembered dialog size (width, height). */
    private Dimension savedDialogSize = null;

    /** CSS injected into every page to disable text selection and style links as inert. */
    private static final String NO_SELECT_CSS =
        "* { -webkit-user-select: none !important; user-select: none !important; cursor: crosshair !important; }" +
        " a { pointer-events: auto !important; cursor: crosshair !important; }";

    /**
     * JavaScript helper function (as a string) that builds a sourceMap for an element
     * by walking all stylesheets and also checking ancestor elements for inherited properties.
     * Iterates rule.style directly (WebKit expands shorthands to longhands).
     * For the target element, later rules overwrite earlier ones (cascade order),
     * and inline styles have the highest priority.
     * For ancestors, the closest ancestor's rule takes priority.
     */
    private static final String BUILD_SOURCE_MAP_JS =
        // Polyfill for Element.matches in older WebKit
        "  var matchesFn = Element.prototype.matches || Element.prototype.webkitMatchesSelector || Element.prototype.msMatchesSelector;" +
        // buildSourceMap(el) returns an object mapping property -> "file : selector"
        "  function buildSourceMap(el) {" +
        "    var map = {};" +
        "    var current = el;" +
        "    while (current && current.nodeType === 1) {" +
        // Walk stylesheets
        "      for (var si = 0; si < document.styleSheets.length; si++) {" +
        "        var sheet = document.styleSheets[si];" +
        "        var href = (sheet.ownerNode && sheet.ownerNode.getAttribute('data-href')) || (sheet.href ? sheet.href.replace(/[?#].*$/, '').replace(/.*\\//, '') : 'inline');" +
        "        var rules;" +
        "        try { rules = sheet.cssRules || sheet.rules; } catch(e) { continue; }" +
        "        if (!rules) continue;" +
        "        for (var ri = 0; ri < rules.length; ri++) {" +
        "          var rule = rules[ri];" +
        "          if (!rule.selectorText) continue;" +
        "          var doesMatch = false;" +
        "          try { doesMatch = matchesFn.call(current, rule.selectorText); } catch(e) {}" +
        "          if (!doesMatch) continue;" +
        "          var style = rule.style;" +
        "          var src = href + ' : ' + rule.selectorText;" +
        "          for (var pi = 0; pi < style.length; pi++) {" +
        "            var prop = style[pi];" +
        "            if (current === el) {" +
        // For target element: later rules overwrite (cascade)
        "              map[prop] = src;" +
        "            } else if (!map[prop]) {" +
        // For ancestors: first (closest) ancestor wins
        "              map[prop] = src + ' (inherited)';" +
        "            }" +
        "          }" +
        "        }" +
        "      }" +
        // Check inline styles (highest priority for target, first-come for ancestors)
        "      if (current.style && current.style.length > 0) {" +
        "        for (var ii = 0; ii < current.style.length; ii++) {" +
        "          var ip = current.style[ii];" +
        "          if (current === el) {" +
        "            map[ip] = 'inline style';" +
        "          } else if (!map[ip]) {" +
        "            map[ip] = 'inline style (inherited)';" +
        "          }" +
        "        }" +
        "      }" +
        "      current = current.parentElement;" +
        "    }" +
        "    return map;" +
        "  }";

    /**
     * JavaScript that retrieves ALL computed styles for the element at a given point,
     * along with the source (stylesheet href : selector) for each property.
     * Returns: selector\nname\tvalue\tsource\nname\tvalue\tsource\n...
     */
    private static final String FULL_COMPUTED_STYLES_JS =
        "(function(x, y) {" +
        "  var el = document.elementFromPoint(x, y);" +
        "  if (!el) return '';" +
        "  var tag = el.tagName.toLowerCase();" +
        "  var id = el.id ? '#' + el.id : '';" +
        "  var cls = el.className && typeof el.className === 'string' ? '.' + el.className.trim().replace(/\\s+/g, '.') : '';" +
        "  var selector = tag + id + cls;" +
        BUILD_SOURCE_MAP_JS +
        "  var sourceMap = buildSourceMap(el);" +
        "  var cs = window.getComputedStyle(el);" +
        "  var props = [];" +
        "  for (var i = 0; i < cs.length; i++) {" +
        "    var name = cs[i];" +
        "    var val = cs.getPropertyValue(name);" +
        "    var src = sourceMap[name] || 'browser default';" +
        "    props.push(name + '\\t' + val + '\\t' + src);" +
        "  }" +
        "  return selector + '\\n' + props.join('\\n');" +
        "})(%d, %d)";

    /**
     * JavaScript that computes non-default styles by comparing the clicked element
     * against a baseline reference of the same tag inside a style-free iframe,
     * along with source information for each property.
     */
    private static final String DIFF_COMPUTED_STYLES_JS =
        "(function(x, y) {" +
        "  var el = document.elementFromPoint(x, y);" +
        "  if (!el) return '';" +
        "  var tag = el.tagName.toLowerCase();" +
        "  var id = el.id ? '#' + el.id : '';" +
        "  var cls = el.className && typeof el.className === 'string' ? '.' + el.className.trim().replace(/\\s+/g, '.') : '';" +
        "  var selector = tag + id + cls;" +
        BUILD_SOURCE_MAP_JS +
        "  var sourceMap = buildSourceMap(el);" +
        // Create iframe baseline for diff comparison
        "  var iframe = document.createElement('iframe');" +
        "  iframe.style.cssText = 'position:absolute;left:-9999px;top:-9999px;width:0;height:0;border:none;visibility:hidden;';" +
        "  document.body.appendChild(iframe);" +
        "  var iDoc = iframe.contentDocument || iframe.contentWindow.document;" +
        "  iDoc.open();" +
        "  iDoc.write('<html><body><' + tag + '></' + tag + '></body></html>');" +
        "  iDoc.close();" +
        "  var refEl = iDoc.body.firstChild;" +
        "  var refCs = iframe.contentWindow.getComputedStyle(refEl);" +
        // Compare computed styles
        "  var cs = window.getComputedStyle(el);" +
        "  var props = [];" +
        "  for (var i = 0; i < cs.length; i++) {" +
        "    var name = cs[i];" +
        "    var val = cs.getPropertyValue(name);" +
        "    var refVal = refCs.getPropertyValue(name);" +
        "    if (val !== refVal) {" +
        "      var src = sourceMap[name] || 'inherited';" +
        "      props.push(name + '\\t' + val + '\\t' + src);" +
        "    }" +
        "  }" +
        // Clean up
        "  document.body.removeChild(iframe);" +
        "  return selector + '\\n' + props.join('\\n');" +
        "})(%d, %d)";

    public PreviewPanel() {
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(200, 100));

        // --- Responsive breakpoint toolbar ---
        JPanel breakpointBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
        breakpointBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        JLabel label = new JLabel("Responsive:");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        breakpointBar.add(label);

        int[] widths = {320, 768, 1024, 1440};
        String[] labels = {"320", "768", "1024", "1440"};
        String[] tooltips = {"Mobile (320px)", "Tablet (768px)", "Desktop (1024px)", "Wide (1440px)"};

        ButtonGroup breakpointGroup = new ButtonGroup();

        // "Auto" button — fill available space
        JToggleButton autoBtn = new JToggleButton("Auto", true);
        autoBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        autoBtn.setMargin(new Insets(2, 6, 2, 6));
        autoBtn.setToolTipText("Fill available width");
        autoBtn.setFocusPainted(false);
        autoBtn.addActionListener(e -> setBreakpoint(-1));
        breakpointGroup.add(autoBtn);
        breakpointBar.add(autoBtn);
        breakpointButtons.add(autoBtn);

        for (int i = 0; i < widths.length; i++) {
            int width = widths[i];
            JToggleButton btn = new JToggleButton(labels[i]);
            btn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            btn.setMargin(new Insets(2, 6, 2, 6));
            btn.setToolTipText(tooltips[i]);
            btn.setFocusPainted(false);
            btn.addActionListener(e -> setBreakpoint(width));
            breakpointGroup.add(btn);
            breakpointBar.add(btn);
            breakpointButtons.add(btn);
        }

        add(breakpointBar, BorderLayout.NORTH);

        // --- WebView panel ---
        fxPanel = new JFXPanel();
        add(fxPanel, BorderLayout.CENTER);

        Platform.runLater(() -> {
            webView = new WebView();
            webEngine = webView.getEngine();

            // Set crosshair cursor on the WebView
            webView.setCursor(Cursor.CROSSHAIR);

            // Click handler to show computed styles
            webView.addEventHandler(MouseEvent.MOUSE_CLICKED, this::onPreviewClicked);

            // Inject no-select CSS and disable links after every page load
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    injectNoSelectStyle();
                }
            });

            Scene scene = new Scene(new StackPane(webView));
            fxPanel.setScene(scene);
        });
    }

    /**
     * Sets the preview viewport to a fixed width (simulating a responsive breakpoint),
     * or -1 to fill all available space.
     */
    private void setBreakpoint(int width) {
        activeBreakpoint = width;
        Platform.runLater(() -> {
            if (webView == null) return;
            if (width <= 0) {
                // Auto: let WebView fill its parent
                webView.setMaxWidth(Double.MAX_VALUE);
                webView.setMinWidth(0);
                webView.setPrefWidth(-1);
            } else {
                webView.setMinWidth(width);
                webView.setPrefWidth(width);
                webView.setMaxWidth(width);
            }
        });
    }

    /**
     * Injects a style element that disables text selection and sets crosshair cursor,
     * and disables all hyperlink navigation so clicks trigger the computed styles dialog.
     */
    private void injectNoSelectStyle() {
        String script =
            "var s = document.createElement('style');" +
            "s.textContent = '" + NO_SELECT_CSS.replace("'", "\\'") + "';" +
            "document.head.appendChild(s);" +
            // Prevent all link clicks from navigating
            "document.addEventListener('click', function(e) {" +
            "  var target = e.target.closest('a');" +
            "  if (target) { e.preventDefault(); e.stopPropagation(); }" +
            "}, true);";
        webEngine.executeScript(script);
    }

    /**
     * Handles a click on the preview. Alt/Option-click shows full computed styles;
     * normal click shows only non-default properties (diffed against iframe baseline).
     */
    private void onPreviewClicked(MouseEvent event) {
        double x = event.getX();
        double y = event.getY();
        boolean showAll = event.isAltDown();

        String jsTemplate = showAll ? FULL_COMPUTED_STYLES_JS : DIFF_COMPUTED_STYLES_JS;
        String js = String.format(jsTemplate, (int) x, (int) y);
        Object result = webEngine.executeScript(js);
        if (result == null || result.toString().isEmpty()) return;

        String text = result.toString();
        int newline = text.indexOf('\n');
        String selector = newline > 0 ? text.substring(0, newline) : text;
        String propertiesBlock = newline > 0 ? text.substring(newline + 1) : "";

        String title = showAll
            ? "All Computed Styles \u2014 " + selector
            : "Non-Default Styles \u2014 " + selector;

        // Parse into structured entries
        List<StyleEntry> entries = parseStyleEntries(propertiesBlock);

        // Show in a dialog on the Swing EDT
        SwingUtilities.invokeLater(() -> showComputedStylesDialog(title, entries));
    }

    /**
     * Parses tab-separated style entries (name\tvalue\tsource) into structured objects.
     */
    private List<StyleEntry> parseStyleEntries(String block) {
        List<StyleEntry> entries = new ArrayList<>();
        if (block == null || block.isEmpty()) return entries;
        String[] lines = block.split("\n");
        for (String line : lines) {
            String[] parts = line.split("\t", 3);
            if (parts.length >= 2) {
                String name = parts[0];
                String value = parts[1];
                String source = parts.length >= 3 ? parts[2] : "";
                entries.add(new StyleEntry(name, value, source));
            }
        }
        return entries;
    }

    /**
     * Displays a dialog with a table showing computed style properties.
     * Columns: Property | Value | Source
     * Column widths are remembered between invocations.
     */
    private void showComputedStylesDialog(String title, List<StyleEntry> entries) {
        String[] columns = {"Property", "Value", "Source"};
        String[][] data = new String[entries.size()][3];
        for (int i = 0; i < entries.size(); i++) {
            StyleEntry entry = entries.get(i);
            data[i][0] = entry.name();
            data[i][1] = entry.value();
            data[i][2] = entry.source();
        }

        JTable table = new JTable(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowSelectionAllowed(true);
        table.setCellSelectionEnabled(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Restore saved column widths explicitly
        for (int i = 0; i < 3; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(savedColumnWidths[i]);
            table.getColumnModel().getColumn(i).setWidth(savedColumnWidths[i]);
        }

        // When a row is clicked, navigate to the source file and highlight the selector
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0 || row >= entries.size()) return;
            StyleEntry entry = entries.get(row);
            String source = entry.source();
            if (source == null || source.isEmpty()
                    || "browser default".equals(source)
                    || "inherited".equals(source)
                    || "inline style".equals(source)
                    || "inline style (inherited)".equals(source)) return;
            // Parse "filename : selector" or "filename : selector (inherited)"
            String cleanSource = source.replace(" (inherited)", "");
            int colonIdx = cleanSource.indexOf(" : ");
            if (colonIdx < 0) return;
            String fileName = cleanSource.substring(0, colonIdx).trim();
            String selector = cleanSource.substring(colonIdx + 3).trim();
            EditorWindow editor = EditorWindow.getActiveInstance();
            if (editor != null) {
                if ("inline".equals(fileName)) {
                    // "inline" means a <style> block in the HTML file
                    editor.navigateToSelector(null, selector);
                } else {
                    editor.navigateToSelector(fileName, selector);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);

        JDialog dialog = new JDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), title, false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(scrollPane, BorderLayout.CENTER);

        // Restore saved dialog size or use default
        if (savedDialogSize != null) {
            dialog.setSize(savedDialogSize);
        } else {
            int totalWidth = savedColumnWidths[0] + savedColumnWidths[1] + savedColumnWidths[2] + 50;
            dialog.setSize(new Dimension(totalWidth, 400));
        }
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));

        // Save column widths and dialog size when dialog is closed
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                savedColumnWidths[0] = table.getColumnModel().getColumn(0).getWidth();
                savedColumnWidths[1] = table.getColumnModel().getColumn(1).getWidth();
                savedColumnWidths[2] = table.getColumnModel().getColumn(2).getWidth();
                savedDialogSize = dialog.getSize();
            }
        });

        dialog.setVisible(true);
    }

    /**
     * Updates the preview with HTML content. Inlines external CSS files as
     * &lt;style&gt; elements with a data-href attribute so that stylesheet rules
     * are accessible via document.styleSheets.cssRules without cross-origin issues.
     */
    public void updateContent(File htmlFile, String htmlContent) {
        // Perform all file I/O (CSS inlining, image embedding) on the calling thread
        // BEFORE dispatching to the JavaFX thread.
        String processed;
        if (htmlFile != null) {
            processed = inlineExternalStyles(htmlFile.getParentFile(), htmlContent);
            try {
                processed = embedImages(htmlFile.getParentFile(), processed);
            } catch (Exception e) {
                System.err.println("LayoutLynx: embedImages failed: " + e.getMessage());
            }
        } else {
            processed = htmlContent;
        }
        final String finalHtml = processed;
        final File finalHtmlFile = htmlFile;

        Platform.runLater(() -> {
            if (webEngine == null) return;
            if (finalHtmlFile != null) {
                try {
                    // Write to temp file — loadContent has size limits that can
                    // truncate large HTML with many base64-embedded images
                    File tempFile = new File(finalHtmlFile.getParentFile(), ".layoutlynx-preview.html");
                    java.nio.file.Files.writeString(tempFile.toPath(), finalHtml);
                    tempFile.deleteOnExit();
                    webEngine.load(tempFile.toURI().toString());
                } catch (java.io.IOException e) {
                    webEngine.loadContent(finalHtml, "text/html");
                }
            } else {
                webEngine.loadContent(finalHtml, "text/html");
            }
        });
    }

    /**
     * Finds img/source tags with relative src/srcset paths and embeds the image
     * data as base64 data URIs. Handles:
     * - &lt;img src="..."&gt;
     * - &lt;img srcset="..."&gt;
     * - &lt;source srcset="..."&gt;
     */
    private String embedImages(File baseDir, String html) {
        // Process src="..." attributes on img tags
        html = embedAttribute(baseDir, html, "src");
        // Process srcset="..." attributes on img and source tags
        html = embedSrcset(baseDir, html);
        return html;
    }

    /**
     * Embeds images referenced by a given attribute (src) as base64 data URIs.
     */
    private String embedAttribute(File baseDir, String html, String attr) {
        // Match attr="value" (double quotes)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(\\b" + attr + "\\s*=\\s*\")([^\"]+)(\")",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(2);
            String resolved = embedSinglePath(baseDir, path);
            matcher.appendReplacement(sb,
                java.util.regex.Matcher.quoteReplacement(matcher.group(1) + resolved + matcher.group(3)));
        }
        matcher.appendTail(sb);

        // Match attr='value' (single quotes)
        pattern = java.util.regex.Pattern.compile(
            "(\\b" + attr + "\\s*=\\s*')([^']+)(')",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(sb.toString());
        StringBuilder sb2 = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(2);
            String resolved = embedSinglePath(baseDir, path);
            matcher.appendReplacement(sb2,
                java.util.regex.Matcher.quoteReplacement(matcher.group(1) + resolved + matcher.group(3)));
        }
        matcher.appendTail(sb2);

        return sb2.toString();
    }

    /**
     * Embeds images referenced by srcset attributes. srcset values are comma-separated
     * entries like "path/img.jpg 2x" or "path/img.jpg 300w".
     */
    private String embedSrcset(File baseDir, String html) {
        // Match srcset="value" (double quotes)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(\\bsrcset\\s*=\\s*\")([^\"]+)(\")",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String srcsetValue = matcher.group(2);
            String resolved = embedSrcsetValue(baseDir, srcsetValue);
            matcher.appendReplacement(sb,
                java.util.regex.Matcher.quoteReplacement(matcher.group(1) + resolved + matcher.group(3)));
        }
        matcher.appendTail(sb);

        // Match srcset='value' (single quotes)
        pattern = java.util.regex.Pattern.compile(
            "(\\bsrcset\\s*=\\s*')([^']+)(')",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(sb.toString());
        StringBuilder sb2 = new StringBuilder();
        while (matcher.find()) {
            String srcsetValue = matcher.group(2);
            String resolved = embedSrcsetValue(baseDir, srcsetValue);
            matcher.appendReplacement(sb2,
                java.util.regex.Matcher.quoteReplacement(matcher.group(1) + resolved + matcher.group(3)));
        }
        matcher.appendTail(sb2);

        return sb2.toString();
    }

    /**
     * Processes a srcset attribute value (comma-separated entries).
     * Each entry is "path descriptor" e.g. "images/img.webp 2x".
     */
    private String embedSrcsetValue(File baseDir, String srcsetValue) {
        String[] entries = srcsetValue.split(",");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i].trim();
            if (entry.isEmpty()) continue;
            // Split into path and descriptor (e.g. "2x" or "300w")
            int lastSpace = entry.lastIndexOf(' ');
            String path;
            String descriptor;
            if (lastSpace > 0) {
                path = entry.substring(0, lastSpace).trim();
                descriptor = entry.substring(lastSpace).trim();
            } else {
                path = entry;
                descriptor = "";
            }
            String resolvedPath = embedSinglePath(baseDir, path);
            if (result.length() > 0) result.append(", ");
            result.append(resolvedPath);
            if (!descriptor.isEmpty()) {
                result.append(' ').append(descriptor);
            }
        }
        return result.toString();
    }

    /**
     * Resolves a single image path: if it's a relative path pointing to an existing
     * file, returns a base64 data URI. Otherwise returns the path unchanged.
     */
    private String embedSinglePath(File baseDir, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")
                || path.startsWith("data:") || path.startsWith("file://")
                || path.startsWith("#") || path.startsWith("//") || path.isEmpty()) {
            return path;
        }
        String decoded = path.replace("%20", " ");
        File imgFile = new File(baseDir, decoded);
        if (imgFile.isFile()) {
            String dataUri = toDataUri(imgFile);
            if (dataUri != null) {
                return dataUri;
            }
        }
        return path;
    }

    /**
     * Reads a file and returns a base64 data URI, or null if unsupported type.
     */
    private String toDataUri(File file) {
        String mime = mimeForFile(file.getName());
        if (mime == null) return null;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            return "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * Returns MIME type for common image extensions, or null.
     */
    private String mimeForFile(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".avif")) return "image/avif";
        return null;
    }
    /**
     * Replaces external &lt;link rel="stylesheet"&gt; references with inline
     * &lt;style data-href="filename"&gt; blocks so cssRules remains accessible.
     * Also recursively resolves @import directives within CSS files, giving each
     * imported file its own &lt;style&gt; block for accurate source attribution.
     * Falls back to keeping the link tag if the file can't be read.
     */
    private String inlineExternalStyles(File baseDir, String html) {
        // Match <link rel="stylesheet" href="..."> in various attribute orders
        java.util.regex.Pattern linkPattern = java.util.regex.Pattern.compile(
            "<link\\b[^>]*\\brel=[\"']stylesheet[\"'][^>]*>",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern hrefPattern = java.util.regex.Pattern.compile(
            "href=[\"']([^\"']+)[\"']",
            java.util.regex.Pattern.CASE_INSENSITIVE);

        java.util.regex.Matcher linkMatcher = linkPattern.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (linkMatcher.find()) {
            String linkTag = linkMatcher.group();
            java.util.regex.Matcher hrefMatcher = hrefPattern.matcher(linkTag);
            if (hrefMatcher.find()) {
                String href = hrefMatcher.group(1);
                // Strip query strings (e.g. cache busters)
                String cleanHref = href.replaceAll("\\?.*$", "");
                File cssFile = new File(baseDir, cleanHref);
                if (cssFile.isFile()) {
                    try {
                        // Flatten into multiple <style> blocks, one per file
                        List<CssFileContent> flattened = new ArrayList<>();
                        flattenImports(cssFile, flattened, new java.util.HashSet<>());
                        StringBuilder replacement = new StringBuilder();
                        for (CssFileContent entry : flattened) {
                            replacement.append("<style data-href=\"")
                                .append(entry.fileName()).append("\">\n")
                                .append(entry.content()).append("\n</style>\n");
                        }
                        linkMatcher.appendReplacement(sb,
                            java.util.regex.Matcher.quoteReplacement(replacement.toString()));
                        continue;
                    } catch (java.io.IOException e) {
                        // Fall through to keep original link
                    }
                }
            }
            linkMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(linkTag));
        }
        linkMatcher.appendTail(sb);
        return sb.toString();
    }

    /** Pattern matching @import url('...') or @import "..." */
    private static final java.util.regex.Pattern CSS_IMPORT_PATTERN = java.util.regex.Pattern.compile(
        "@import\\s+(?:url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)|[\"']([^\"']+)[\"'])\\s*;?",
        java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Holds a CSS file's content paired with its filename for inlining. */
    private record CssFileContent(String fileName, String content) {}

    /**
     * Recursively reads a CSS file and flattens @import directives. Each imported file
     * becomes a separate entry in the list (imports first, then the file's own rules).
     * This preserves correct source attribution per file.
     */
    private void flattenImports(File cssFile, List<CssFileContent> result, java.util.Set<String> visited)
            throws java.io.IOException {
        String canonicalPath = cssFile.getCanonicalPath();
        if (!visited.add(canonicalPath)) {
            return; // circular import
        }

        String content = java.nio.file.Files.readString(cssFile.toPath());
        File cssDir = cssFile.getParentFile();

        // Find and process @import statements (they must appear before other rules)
        java.util.regex.Matcher importMatcher = CSS_IMPORT_PATTERN.matcher(content);
        StringBuilder ownContent = new StringBuilder();
        int lastEnd = 0;

        while (importMatcher.find()) {
            // Only process @imports that appear before non-whitespace/comment content
            String between = content.substring(lastEnd, importMatcher.start()).trim();
            if (!between.isEmpty() && !between.startsWith("/*")) {
                // Past the @import section — stop processing imports
                break;
            }

            String importPath = importMatcher.group(1) != null ? importMatcher.group(1) : importMatcher.group(2);
            importPath = importPath.replaceAll("\\?.*$", "");
            File importedFile = new File(cssDir, importPath);

            if (importedFile.isFile()) {
                // Recursively flatten the imported file (added before this file's rules)
                flattenImports(importedFile, result, visited);
            }
            lastEnd = importMatcher.end();
        }

        // The remaining content (after @imports are stripped) is this file's own rules
        String ownRules = content.substring(lastEnd);
        // Also strip any remaining @import lines that we processed
        ownRules = CSS_IMPORT_PATTERN.matcher(ownRules).replaceAll("");
        if (!ownRules.trim().isEmpty()) {
            result.add(new CssFileContent(cssFile.getName(), ownRules));
        }
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

    // ---- Inner types ----

    /** Holds a single style property with its value and source information. */
    private record StyleEntry(String name, String value, String source) {}
}
