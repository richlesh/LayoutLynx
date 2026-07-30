/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.aichat;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * AI Chat panel that provides LLM-powered markdown writing assistance.
 * Renders conversation as HTML in a JavaFX WebView with full markdown support
 * (tables, math, code highlighting). Falls back to a basic Swing renderer
 * when WebView is unavailable.
 *
 * Use {@link #builder()} to construct instances.
 */
public class AIChatPanel extends JPanel {

    private final JTextArea inputArea;
    private final JButton sendBtn;
    private final DocumentEditor editor;
    private final AIChatPreferences aiPreferences;
    private ChatColors chatColors;
    private final List<Map<String, String>> messages = new ArrayList<>();
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final String systemPrompt;
    private final Parser mdParser;
    private final HtmlRenderer mdRenderer;
    private final String humanIconDataUri;
    private final String aiIconDataUri;

    // WebView rendering
    private javafx.embed.swing.JFXPanel jfxPanel;
    private javafx.scene.web.WebEngine webEngine;
    private boolean useWebView = false;
    private boolean webViewReady = false;
    // Strong reference to prevent GC of the JavaScript bridge object
    private ChatBridge chatBridge;

    // Fallback rendering
    private JEditorPane fallbackPane;
    private JScrollPane fallbackScroll;

    private volatile Thread currentThread;
    private boolean pulsing = false;
    private int promptCount = 0;
    private LLMClient llmClient;
    private Runnable promptNagCallback;

    /** Internal chat message record. */
    private static class ChatMessage {
        final String role; // "user" or "assistant"
        final String markdown;
        boolean accepted; // for code approval messages
        boolean rejected;
        boolean isApproval; // whether this contains a document replacement

        ChatMessage(String role, String markdown) {
            this.role = role;
            this.markdown = markdown;
        }
    }

    /**
     * Create a builder for constructing an AIChatPanel.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AIChatPanel.
     */
    public static class Builder {
        private DocumentEditor editor;
        private AIChatPreferences preferences;
        private ChatColors chatColors;
        private LLMClient llmClient;
        private Runnable onPromptNag;

        private Builder() {}

        /** Set the document editor (required). */
        public Builder editor(DocumentEditor editor) {
            this.editor = editor;
            return this;
        }

        /** Set the AI chat preferences (required). */
        public Builder preferences(AIChatPreferences preferences) {
            this.preferences = preferences;
            return this;
        }

        /** Set the chat bubble colors (optional; falls back to AIChatPreferences defaults). */
        public Builder chatColors(ChatColors chatColors) {
            this.chatColors = chatColors;
            return this;
        }

        /** Set the LLM client (optional; will use LLMClientFactory if not set). */
        public Builder llmClient(LLMClient llmClient) {
            this.llmClient = llmClient;
            return this;
        }

        /** Set a callback invoked every N prompts for nag/licensing (optional). */
        public Builder onPromptNag(Runnable onPromptNag) {
            this.onPromptNag = onPromptNag;
            return this;
        }

        /** Build the AIChatPanel. */
        public AIChatPanel build() {
            if (editor == null) throw new IllegalStateException("editor is required");
            if (preferences == null) throw new IllegalStateException("preferences is required");
            return new AIChatPanel(this);
        }
    }

    private AIChatPanel(Builder builder) {
        super(new BorderLayout());
        this.editor = builder.editor;
        this.aiPreferences = builder.preferences;
        this.chatColors = builder.chatColors;
        this.llmClient = builder.llmClient;
        this.promptNagCallback = builder.onPromptNag;
        this.systemPrompt = buildSystemPrompt();
        this.humanIconDataUri = loadIconAsDataUri("/human.png");
        this.aiIconDataUri = loadIconAsDataUri("/AI.png");

        // Set up commonmark parser for rendering AI responses
        List<Extension> extensions = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create(),
            AutolinkExtension.create()
        );
        mdParser = Parser.builder().extensions(extensions).build();
        mdRenderer = HtmlRenderer.builder().extensions(extensions).build();

        setPreferredSize(new Dimension(380, 0));
        setBorder(BorderFactory.createTitledBorder("AI Assistant"));

        // Initialize chat display (WebView or fallback)
        initChatDisplay();

        // Input area
        inputArea = new JTextArea(3, 20);
        inputArea.setFont(new Font(aiPreferences.getAiFontFamily(), Font.PLAIN, aiPreferences.getAiFontSize()));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isShiftDown()) {
                    e.consume();
                    inputArea.insert("\n", inputArea.getCaretPosition());
                }
            }
        });
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        sendBtn = new JButton("Send");
        JButton clearBtn = new JButton("Clear");
        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        btnPanel.add(sendBtn);
        btnPanel.add(clearBtn);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(btnPanel, BorderLayout.EAST);

        JLabel statusBar = new JLabel(" ");
        statusBar.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        Runnable statusUpdater = () -> {
            int sp = systemPrompt.length();
            int doc = editor.getText().length();
            statusBar.setText(String.format("System: %,d chars    Document: %,d chars", sp, doc));
        };
        statusUpdater.run();

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(inputPanel, BorderLayout.CENTER);
        southPanel.add(statusBar, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);

        sendBtn.addActionListener(e -> sendMessage());
        clearBtn.addActionListener(e -> {
            messages.clear();
            chatMessages.clear();
            pulsing = false;
            if (llmClient instanceof GenericClient gc) {
                GenericVendorConfig cfg = gc.getConfig();
                if (cfg != null) cfg.resetGuid();
            }
            renderChat();
        });
    }

    private void initChatDisplay() {
        try {
            jfxPanel = new javafx.embed.swing.JFXPanel();
            add(jfxPanel, BorderLayout.CENTER);
            javafx.application.Platform.runLater(() -> {
                try {
                    javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
                    webEngine = webView.getEngine();

                    // JavaScript-to-Java bridge for button clicks and copy
                    chatBridge = new ChatBridge();
                    webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                            netscape.javascript.JSObject win = (netscape.javascript.JSObject) webEngine.executeScript("window");
                            win.setMember("chatBridge", chatBridge);
                            webViewReady = true;
                        }
                    });

                    javafx.scene.Scene scene = new javafx.scene.Scene(webView);
                    jfxPanel.setScene(scene);
                    useWebView = true;
                    renderChat();
                } catch (Throwable t) {
                    SwingUtilities.invokeLater(() -> {
                        remove(jfxPanel);
                        jfxPanel = null;
                        useWebView = false;
                        initFallback();
                        revalidate();
                        repaint();
                    });
                }
            });
        } catch (Throwable t) {
            if (jfxPanel != null) {
                remove(jfxPanel);
                jfxPanel = null;
            }
            initFallback();
        }
    }

    private void initFallback() {
        fallbackPane = new JEditorPane();
        fallbackPane.setContentType("text/html");
        fallbackPane.setEditable(false);
        fallbackPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        fallbackScroll = new JScrollPane(fallbackPane);
        add(fallbackScroll, BorderLayout.CENTER);
    }

    /** JavaScript bridge for WebView callbacks. */
    public class ChatBridge {
        /** Called when user clicks Accept on a code approval bubble. */
        public void acceptChanges(int index) {
            SwingUtilities.invokeLater(() -> {
                if (index >= 0 && index < chatMessages.size()) {
                    ChatMessage msg = chatMessages.get(index);
                    if (msg instanceof ApprovalMessage am) {
                        msg.accepted = true;
                        editor.setText(am.replacementMarkdown);
                        renderChat();
                    }
                }
            });
        }

        /** Called when user clicks Reject on a code approval bubble. */
        public void rejectChanges(int index) {
            SwingUtilities.invokeLater(() -> {
                if (index >= 0 && index < chatMessages.size()) {
                    ChatMessage msg = chatMessages.get(index);
                    msg.rejected = true;
                    renderChat();
                }
            });
        }

        /** Called when user clicks Copy on an AI response bubble. */
        public void copyMarkdown(int index) {
            SwingUtilities.invokeLater(() -> {
                if (index >= 0 && index < chatMessages.size()) {
                    ChatMessage msg = chatMessages.get(index);
                    String md = (msg instanceof ApprovalMessage am) ? am.replacementMarkdown : msg.markdown;
                    StringSelection sel = new StringSelection(md);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                }
            });
        }

        /** Called when a link is clicked. */
        public void openLink(String url) {
            SwingUtilities.invokeLater(() -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ex) {
                    // Silently fail
                }
            });
        }
    }

    /** Set or replace the LLM client at runtime (e.g., after preferences change). */
    public void setLlmClient(LLMClient client) {
        this.llmClient = client;
    }

    /** Set or replace the prompt nag callback at runtime. */
    public void setPromptNagCallback(Runnable callback) {
        this.promptNagCallback = callback;
    }

    /** Set or replace the chat colors at runtime and re-render. */
    public void setChatColors(ChatColors colors) {
        this.chatColors = colors;
        renderChat();
    }

    /** Update fonts after preferences change. */
    public void updateFont() {
        inputArea.setFont(new Font(aiPreferences.getAiFontFamily(), Font.PLAIN, aiPreferences.getAiFontSize()));
        renderChat();
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        inputArea.setText("");

        // Add user bubble
        ChatMessage userMsg = new ChatMessage("user", text);
        chatMessages.add(userMsg);

        // Invoke nag callback every 10 prompts
        promptCount++;
        if (promptCount % 10 == 0 && promptNagCallback != null) {
            promptNagCallback.run();
        }

        String context = "Current markdown document:\n```markdown\n" + editor.getText() + "\n```";
        if (messages.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", context + "\n\nUser request: " + text));

        // Ensure we have an LLM client
        if (llmClient == null) {
            llmClient = LLMClientFactory.create(aiPreferences);
        }

        sendBtn.setEnabled(false);
        pulsing = true;
        renderChat();

        final LLMClient client = llmClient;
        currentThread = new Thread(() -> {
            try {
                String response = client.chat(messages, systemPrompt);
                messages.add(Map.of("role", "assistant", "content", response));
                SwingUtilities.invokeLater(() -> {
                    pulsing = false;
                    processResponse(response);
                    sendBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    pulsing = false;
                    if (!Thread.currentThread().isInterrupted()) {
                        ChatMessage errMsg = new ChatMessage("assistant",
                            "Error (" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
                        chatMessages.add(errMsg);
                    }
                    renderChat();
                    sendBtn.setEnabled(true);
                });
            }
        });
        currentThread.start();
    }

    private void processResponse(String response) {
        // Normalize line endings
        String normalized = response.replace("\r\n", "\n").replace("\r", "\n");

        int codeStart = normalized.indexOf("```markdown\n");
        if (codeStart < 0) codeStart = normalized.indexOf("```md\n");
        if (codeStart < 0) codeStart = normalized.indexOf("```markdown ");
        if (codeStart < 0) codeStart = normalized.indexOf("```md ");

        if (codeStart >= 0) {
            int blockStart = normalized.indexOf("\n", codeStart) + 1;
            int blockEnd = -1;
            int searchFrom = blockStart;
            while (searchFrom < normalized.length()) {
                int candidate = normalized.indexOf("\n```", searchFrom);
                if (candidate < 0) break;
                int afterFence = candidate + 4;
                if (afterFence >= normalized.length()) {
                    blockEnd = candidate;
                } else {
                    char nextChar = normalized.charAt(afterFence);
                    if (nextChar == '\n' || normalized.substring(afterFence).trim().isEmpty()) {
                        blockEnd = candidate;
                    }
                }
                searchFrom = candidate + 4;
            }
            if (blockEnd > blockStart) {
                String newMarkdown = normalized.substring(blockStart, blockEnd);
                String explanation = normalized.substring(0, codeStart).trim();
                int fenceEndPos = normalized.indexOf("\n", blockEnd + 1);
                if (fenceEndPos < 0) fenceEndPos = blockEnd + 4;
                if (fenceEndPos < normalized.length()) {
                    String after = normalized.substring(fenceEndPos).trim();
                    if (!after.isEmpty()) explanation += (explanation.isEmpty() ? "" : "\n") + after;
                }
                // Create approval message
                ChatMessage approvalMsg = new ChatMessage("assistant",
                    explanation.isEmpty() ? newMarkdown : explanation);
                approvalMsg.isApproval = true;
                // Store the full replacement markdown in the message
                // We use a special field - store it as the markdown content
                chatMessages.add(new ApprovalMessage(explanation, newMarkdown));
                renderChat();
                return;
            }
        }
        ChatMessage aiMsg = new ChatMessage("assistant", response);
        chatMessages.add(aiMsg);
        renderChat();
    }

    /** Special message type for document replacement approvals. */
    private static class ApprovalMessage extends ChatMessage {
        final String explanation;
        final String replacementMarkdown;

        ApprovalMessage(String explanation, String replacementMarkdown) {
            super("assistant", explanation.isEmpty()
                ? "Here's the updated document. Review and accept or reject the changes."
                : explanation);
            this.explanation = explanation;
            this.replacementMarkdown = replacementMarkdown;
            this.isApproval = true;
        }
    }

    /** Render the full chat as HTML and load into WebView or fallback. */
    private void renderChat() {
        String html = buildChatHtml();
        if (useWebView && webEngine != null) {
            javafx.application.Platform.runLater(() -> {
                webEngine.loadContent(html);
            });
        } else if (fallbackPane != null) {
            fallbackPane.setText(html);
            SwingUtilities.invokeLater(() -> {
                JScrollBar v = fallbackScroll.getVerticalScrollBar();
                v.setValue(v.getMaximum());
            });
        }
    }

    private String buildChatHtml() {
        String fontFamily = aiPreferences.getAiFontFamily();
        int fontSize = aiPreferences.getAiFontSize();
        String codeFontFamily = aiPreferences.getAiCodeFontFamily();
        int codeFontSize = aiPreferences.getAiCodeFontSize();
        String userBg = chatColors != null ? chatColors.getUserPromptColor() : aiPreferences.getUserPromptColor();
        String userText = chatColors != null ? chatColors.getUserTextColor() : aiPreferences.getUserTextColor();
        String aiBg = chatColors != null ? chatColors.getAiResponseColor() : aiPreferences.getAiResponseColor();
        String aiText = chatColors != null ? chatColors.getAiTextColor() : aiPreferences.getAiTextColor();

        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"utf-8\"><style>");
        // Dynamic styles that depend on preferences
        html.append("body { font-family: '").append(fontFamily).append("', 'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji', sans-serif; ");
        html.append("font-size: ").append(fontSize).append("pt; }");
        html.append(".user-bubble { background: ").append(userBg).append("; color: ").append(userText).append("; }");
        html.append(".ai-bubble { background: ").append(aiBg).append("; color: ").append(aiText).append("; }");
        html.append(".approval-btns button { font-size: ").append(fontSize).append("pt; }");
        html.append("code, pre { font-family: '").append(codeFontFamily).append("', monospace; ");
        html.append("font-size: ").append(codeFontSize).append("pt; }");
        html.append("</style>");
        // Static styles from resource file
        html.append("<style>").append(loadCssResource()).append("</style>");
        html.append("<script>");
        html.append("MathJax = {");
        html.append("  tex: { inlineMath: [['$','$'], ['\\\\(','\\\\)']], displayMath: [['$$','$$'], ['\\\\[','\\\\]']] },");
        html.append("  options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'] },");
        html.append("  svg: { fontCache: 'global' }");
        html.append("};");
        html.append("</script>");
        html.append("<script src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js\" async></script>");
        html.append("<script>");
        html.append("document.addEventListener('click', function(e) {");
        html.append("  var a = e.target.closest('a');");
        html.append("  if(a && a.href && (a.href.startsWith('http://') || a.href.startsWith('https://'))) {");
        html.append("    e.preventDefault();");
        html.append("    if(window.chatBridge) window.chatBridge.openLink(a.href);");
        html.append("  }");
        html.append("});");
        html.append("function acceptChanges(idx) { if(window.chatBridge) window.chatBridge.acceptChanges(idx); }");
        html.append("function rejectChanges(idx) { if(window.chatBridge) window.chatBridge.rejectChanges(idx); }");
        html.append("function copyMarkdown(idx) { if(window.chatBridge) window.chatBridge.copyMarkdown(idx); }");
        html.append("</script>");
        html.append("</head><body>");

        // Render all chat messages
        for (int i = 0; i < chatMessages.size(); i++) {
            ChatMessage msg = chatMessages.get(i);
            if ("user".equals(msg.role)) {
                html.append("<div class=\"bubble-row\">");
                html.append("<img class=\"bubble-icon\" src=\"").append(humanIconDataUri).append("\">");
                html.append("<div class=\"bubble user-bubble bubble-content\">");
                html.append(escapeHtml(msg.markdown));
                html.append("</div></div>");
            } else {
                // AI message — render markdown as HTML
                String renderedContent;
                if (msg instanceof ApprovalMessage am) {
                    renderedContent = renderMarkdownToHtml(am.explanation.isEmpty()
                        ? "Here's the updated document. Review and accept or reject the changes."
                        : am.explanation);
                } else {
                    renderedContent = renderMarkdownToHtml(msg.markdown);
                }

                html.append("<div class=\"bubble-row\">");
                html.append("<img class=\"bubble-icon\" src=\"").append(aiIconDataUri).append("\">");
                html.append("<div class=\"bubble ai-bubble bubble-content\">");
                html.append("<button class=\"copy-btn\" onclick=\"copyMarkdown(").append(i).append(")\" title=\"Copy markdown\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 16 16\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\"><rect x=\"5\" y=\"5\" width=\"9\" height=\"9\" rx=\"1.5\"/><path d=\"M3 11V2.5A1.5 1.5 0 0 1 4.5 1H11\"/></svg></button>");
                html.append(renderedContent);

                // Approval buttons
                if (msg.isApproval) {
                    if (msg.accepted) {
                        html.append("<div class=\"status-label\">✓ Changes accepted.</div>");
                    } else if (msg.rejected) {
                        html.append("<div class=\"status-label\">✗ Changes rejected.</div>");
                    } else {
                        html.append("<div class=\"approval-btns\">");
                        html.append("<button class=\"accept-btn\" onclick=\"acceptChanges(").append(i).append(")\">Accept</button>");
                        html.append("<button class=\"reject-btn\" onclick=\"rejectChanges(").append(i).append(")\">Reject</button>");
                        html.append("</div>");
                    }
                }

                html.append("</div></div>");
            }
        }

        // Pulsing "thinking" indicator
        if (pulsing) {
            html.append("<div class=\"thinking\"><img class=\"bubble-icon\" src=\"").append(aiIconDataUri).append("\">Thinking...</div>");
        }

        // Auto-scroll to bottom
        html.append("<script>window.onload = function() { window.scrollTo(0, document.body.scrollHeight); ");
        html.append("if(window.MathJax && MathJax.typesetPromise) MathJax.typesetPromise(); };</script>");

        html.append("</body></html>");
        return html.toString();
    }

    private String renderMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        // Convert LaTeX-style math delimiters to dollar-sign delimiters before parsing
        String converted = markdown.replaceAll("\\\\\\((.+?)\\\\\\)", "\\$$1\\$");
        converted = converted.replaceAll("(?s)\\\\\\[(.+?)\\\\\\]", "\\$\\$$1\\$\\$");
        Node document = mdParser.parse(converted);
        return mdRenderer.render(document);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("\n", "<br>");
    }

    private String buildSystemPrompt() {
        try (var is = AIChatPanel.class.getResourceAsStream("/system_prompt.md")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            // Fall through to hardcoded fallback
        }
        return "You are an AI writing assistant. Help users write and improve markdown documents.";
    }

    private static String loadIconAsDataUri(String resourcePath) {
        try (var is = AIChatPanel.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                return "data:image/png;base64," + base64;
            }
        } catch (Exception e) {
            // Fall through
        }
        return "";
    }

    private static String loadCssResource() {
        try (var is = AIChatPanel.class.getResourceAsStream("/ai_chat.css")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Fall through
        }
        return "";
    }
}
