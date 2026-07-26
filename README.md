![app_icon_256](src/main/resources/app_icon_256.png)

# LayoutLynx

A lightweight HTML/CSS editor with a live preview pane, built for rapid front-end prototyping and learning.

## Features

- **Tabbed Editor** — Edit HTML and CSS files side-by-side with syntax highlighting powered by RSyntaxTextArea
- **Live Preview** — Real-time HTML/CSS rendering via JavaFX WebView, updating as you type
- **Computed Styles Inspector** — Click any element in the preview to see its non-default CSS properties; Alt/Option-click for the full computed style list. Each property shows the source file and selector that set it.
- **Color Swatch Gutter** — Visual color swatches appear next to CSS color values; click to open a color picker and edit inline
- **CSS Specificity Tooltips** — Hover over selectors in CSS files to see their computed specificity
- **Code Formatting** — Tidy HTML, CSS, and JavaScript using js-beautify (via GraalVM Polyglot)
- **Find & Replace** — Full-featured find/replace with regex support and find-all results
- **AI Chat Panel** — Integrated LLM assistant for code help (configurable vendor/model)
- **macOS Integration** — Native menu bar, file associations, About/Preferences handlers
- **Cross-Platform** — Runs on macOS, Windows, and Linux

## AI Assistant

The built-in AI chat panel provides context-aware coding assistance directly alongside your editor:

- **Draft HTML** — Generate sections, components, layouts, lists, tables, and semantic structures
- **Write & improve CSS** — Create selectors, properties, responsive designs, animations, flexbox/grid layouts
- **Fix & explain** — Troubleshoot styling issues, explain CSS behavior, and suggest best practices
- **Accessibility** — Get guidance on HTML and CSS construction

The AI always sees your current document content, so its suggestions are contextual. When it proposes a full document update, the response includes **Allow/Reject** buttons — click Allow to replace your document, or Reject to discard.

### Supported Providers

Configure your LLM provider in **Preferences → AI**:

Alibaba, Anthropic, Cerebras, DeepSeek, Google, Groq, Meta, Mistral, Moonshot AI, Ollama (local), OpenAI, Perplexity, xAI

Set the vendor, model name, and API key. For Ollama, no API key is needed (runs locally on port 11434).

## Requirements

- Java 21+
- Maven 3.8+

## Building

```bash
mvn clean package
```

## Running

```bash
mvn exec:java
```

Or run the shaded JAR directly:

```bash
java --enable-native-access=ALL-UNNAMED \
     --add-modules=javafx.controls,javafx.web,javafx.swing \
     -jar target/LayoutLynx-1.0.0.jar
```

## Demo

A sample project is included in the `demo/` directory with an `index.html` and multiple CSS files demonstrating `@import` and multi-file styling.

## Tech Stack

- **Java 21** — Application language
- **JavaFX 24** — WebView for live preview, embedded browser engine
- **Swing** — Primary UI framework (editor, dialogs, panels)
- **RSyntaxTextArea 3.5** — Syntax highlighting, code folding, editor component
- **GraalVM Polyglot 24.2** — JavaScript runtime for js-beautify code formatting
- **Gson 2.10** — JSON parsing for preferences and AI API communication
- **Maven** — Build tool with shade plugin for fat JAR packaging

## License

© 2026 Glowing Cat Software. All rights reserved.
