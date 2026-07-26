/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * AI Chat panel for CSS assistance. Connects to LLM APIs configured in preferences.
 */
public class AIChatPanel extends JPanel {

    private final JTextPane chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final Preferences preferences;

    public AIChatPanel(Preferences preferences) {
        this.preferences = preferences;
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(250, 100));
        setPreferredSize(new Dimension(350, 400));

        // Header
        JLabel header = new JLabel("  AI CSS Assistant");
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(header, BorderLayout.NORTH);

        // Chat area
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setContentType("text/html");
        chatArea.setText("<html><body style='font-family: " + preferences.getAiFontFamily()
            + "; font-size: " + preferences.getAiFontSize() + "px; padding: 8px;'>"
            + "<p style='color: #888;'>Ask me about CSS properties, selectors, layouts, "
            + "or paste your CSS for suggestions.</p></body></html>");
        JScrollPane chatScroll = new JScrollPane(chatArea);
        add(chatScroll, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        inputField = new JTextField();
        inputField.setFont(new Font(preferences.getAiFontFamily(), Font.PLAIN, preferences.getAiFontSize()));
        inputField.addActionListener(e -> sendMessage());
        inputPanel.add(inputField, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) return;
        inputField.setText("");

        // Append user message to chat
        appendMessage("You", message, preferences.getUserPromptColor());

        // Send to LLM in background
        sendButton.setEnabled(false);
        new Thread(() -> {
            try {
                String response = callLLM(message);
                SwingUtilities.invokeLater(() -> {
                    appendMessage("AI", response, preferences.getAiResponseColor());
                    sendButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("Error", ex.getMessage(), "#cc0000");
                    sendButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void appendMessage(String sender, String message, String color) {
        // Simple append - in production this would use styled documents
        String current = chatArea.getText();
        String bubble = "<div style='background-color: " + color + "; color: white; "
            + "border-radius: 8px; padding: 8px; margin: 4px 0;'>"
            + "<b>" + sender + ":</b><br>" + escapeHtml(message) + "</div>";

        // Insert before </body>
        String updated = current.replace("</body>", bubble + "</body>");
        chatArea.setText(updated);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                   .replace(">", "&gt;").replace("\n", "<br>");
    }

    private String callLLM(String userMessage) throws Exception {
        String apiKey = preferences.getLlmApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "Please configure your AI API key in Settings to use the assistant.";
        }

        String vendor = preferences.getLlmVendor();
        String model = preferences.getLlmModel();
        String baseUrl = getBaseUrl(vendor);

        String systemPrompt = "You are a CSS expert assistant. Help the user with CSS styling, "
            + "layout techniques, selectors, properties, and best practices. "
            + "Provide concise, practical answers with code examples when appropriate.";

        String jsonBody = """
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                ],
                "max_tokens": 1024
            }
            """.formatted(model, systemPrompt, userMessage.replace("\"", "\\\"").replace("\n", "\\n"));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if ("Anthropic".equals(vendor)) {
            reqBuilder.header("x-api-key", apiKey);
            reqBuilder.header("anthropic-version", "2023-06-01");
        } else {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = client.send(reqBuilder.build(),
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "API Error (" + response.statusCode() + "): " + response.body();
        }

        // Simple JSON parsing for the response content
        String body = response.body();
        int contentIdx = body.indexOf("\"content\":");
        if (contentIdx >= 0) {
            int start = body.indexOf("\"", contentIdx + 10) + 1;
            int end = body.indexOf("\"", start);
            // Handle escaped quotes
            while (end > 0 && body.charAt(end - 1) == '\\') {
                end = body.indexOf("\"", end + 1);
            }
            if (start > 0 && end > start) {
                return body.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            }
        }
        return "Unable to parse response.";
    }

    private String getBaseUrl(String vendor) {
        return switch (vendor) {
            case "Anthropic" -> "https://api.anthropic.com/v1";
            case "OpenAI" -> "https://api.openai.com/v1";
            case "Google" -> "https://generativelanguage.googleapis.com/v1beta/openai";
            case "DeepSeek" -> "https://api.deepseek.com/v1";
            case "Alibaba" -> "https://dashscope-us.aliyuncs.com/compatible-mode/v1";
            case "Cerebras" -> "https://api.cerebras.ai/v1";
            case "Groq" -> "https://api.groq.com/openai/v1";
            case "Mistral" -> "https://api.mistral.ai/v1";
            case "Ollama" -> "http://localhost:11434/v1";
            case "xAI" -> "https://api.x.ai/v1";
            case "Perplexity" -> "https://api.perplexity.ai";
            default -> "https://api.openai.com/v1";
        };
    }
}
