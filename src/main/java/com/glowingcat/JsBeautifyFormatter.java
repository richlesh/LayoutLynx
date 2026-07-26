/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wraps the js-beautify library via GraalVM Polyglot to format HTML, CSS, and JavaScript.
 * The GraalJS context is created lazily and reused for performance.
 */
public class JsBeautifyFormatter {

    private static Context context;
    private static boolean initialized = false;
    private static String initError = null;

    /**
     * Initializes the GraalJS context and loads js-beautify libraries.
     * Called lazily on first format request.
     */
    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        try {
            context = Context.newBuilder("js")
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build();

            // Provide global/window shims so js-beautify can export its functions
            context.eval("js", "var global = globalThis; var window = globalThis;");

            // Load js-beautify libraries
            String beautifyJs = loadResource("/beautify.min.js");
            String beautifyCss = loadResource("/beautify-css.min.js");
            String beautifyHtml = loadResource("/beautify-html.min.js");

            context.eval("js", beautifyJs);
            context.eval("js", beautifyCss);
            context.eval("js", beautifyHtml);
        } catch (Exception e) {
            initError = e.getMessage();
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
                context = null;
            }
        }
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream is = JsBeautifyFormatter.class.getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Formats HTML content (including embedded CSS and JavaScript).
     *
     * @param html    the HTML source to format
     * @param options formatting options as a JSON string
     * @return the formatted HTML, or the original on failure
     */
    public static String formatHtml(String html, String options) {
        ensureInitialized();
        if (context == null) return html;
        try {
            context.getBindings("js").putMember("__input", html);
            context.getBindings("js").putMember("__options", options);
            Value result = context.eval("js",
                "html_beautify(__input, JSON.parse(__options))");
            return result.asString();
        } catch (Exception e) {
            return html;
        }
    }

    /**
     * Formats CSS content.
     *
     * @param css     the CSS source to format
     * @param options formatting options as a JSON string
     * @return the formatted CSS, or the original on failure
     */
    public static String formatCss(String css, String options) {
        ensureInitialized();
        if (context == null) return css;
        try {
            context.getBindings("js").putMember("__input", css);
            context.getBindings("js").putMember("__options", options);
            Value result = context.eval("js",
                "css_beautify(__input, JSON.parse(__options))");
            return result.asString();
        } catch (Exception e) {
            return css;
        }
    }

    /**
     * Formats JavaScript content.
     *
     * @param js      the JavaScript source to format
     * @param options formatting options as a JSON string
     * @return the formatted JavaScript, or the original on failure
     */
    public static String formatJs(String js, String options) {
        ensureInitialized();
        if (context == null) return js;
        try {
            context.getBindings("js").putMember("__input", js);
            context.getBindings("js").putMember("__options", options);
            Value result = context.eval("js",
                "js_beautify(__input, JSON.parse(__options))");
            return result.asString();
        } catch (Exception e) {
            return js;
        }
    }

    /**
     * Returns any initialization error message, or null if successful.
     */
    public static String getInitError() {
        ensureInitialized();
        return initError;
    }

    /**
     * Closes the GraalJS context. Call on application exit.
     */
    public static void shutdown() {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
            context = null;
        }
    }
}
