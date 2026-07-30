/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.aichat;

/**
 * Factory that creates the appropriate LLMClient based on AIChatPreferences.
 */
public class LLMClientFactory {

    private LLMClientFactory() {}

    /**
     * Create an LLMClient for the currently configured vendor.
     *
     * @param prefs the AI chat preferences
     * @return an appropriate LLMClient implementation
     */
    public static LLMClient create(AIChatPreferences prefs) {
        String vendor = prefs.getLlmVendor();
        String apiKey = prefs.getLlmApiKey();
        String model = prefs.getLlmModel();

        if ("Generic".equals(vendor)) {
            return new GenericClient(apiKey, model);
        }

        if ("Anthropic".equals(vendor)) {
            return new AnthropicClient(apiKey, model);
        }

        String baseUrl = switch (vendor) {
            case "Alibaba" -> "https://dashscope-us.aliyuncs.com/compatible-mode/v1";
            case "Cerebras" -> "https://api.cerebras.ai/v1";
            case "DeepSeek" -> "https://api.deepseek.com/v1";
            case "Generic OpenAI API" -> {
                String ep = prefs.getLlmEndpoint();
                if (ep == null || ep.isBlank()) throw new RuntimeException("No endpoint configured for Generic OpenAI API");
                if (ep.endsWith("/")) ep = ep.substring(0, ep.length() - 1);
                yield ep;
            }
            case "Google" -> "https://generativelanguage.googleapis.com/v1beta/openai";
            case "Groq" -> "https://api.groq.com/openai/v1";
            case "Meta" -> "https://api.meta.ai/v1";
            case "Mistral" -> "https://api.mistral.ai/v1";
            case "Moonshot AI" -> "https://api.moonshot.ai/v1";
            case "Ollama" -> "http://localhost:11434/v1";
            case "OpenAI" -> "https://api.openai.com/v1";
            case "Perplexity" -> "https://api.perplexity.ai";
            case "xAI" -> "https://api.x.ai/v1";
            default -> "https://api.openai.com/v1";
        };

        return new OpenAIClient(baseUrl, apiKey, model);
    }
}
