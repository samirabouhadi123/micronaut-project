package example.com.exceptions.response.error;

import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.LocaleResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.HtmlErrorResponseBodyProvider;
import io.micronaut.http.server.exceptions.response.JsonErrorResponseBodyProvider;
import io.micronaut.http.util.HtmlSanitizer;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;

import static io.micronaut.http.HttpStatus.*;

@Singleton
@Primary
public class DefaultHtmlProvider implements HtmlErrorResponseBodyProvider {

    @Value("${filter.prefix.micronaut}")
    protected String filterPrefixMicronaut;

    @Value("${filter.prefix.netty}")
    protected String filterPrefixNetty;

    @Value("${filter.unknown.source:Unknown Source}")
    protected String filterUnknownSource;

    @Value("${micronaut.environment:development}")
    protected String environment;

    private static final Map<Integer, String> DEFAULT_ERROR_BOLD = Map.of(
            NOT_FOUND.getCode(), "The page is not available",
            REQUEST_ENTITY_TOO_LARGE.getCode(), "The file or data you are trying to upload exceeds the size",
            INTERNAL_SERVER_ERROR.getCode(), "An internal server error occurred"
    );

    private static final Map<Integer, String> DEFAULT_ERROR = Map.of(
            NOT_FOUND.getCode(), "You may have mistyped the address or the page may have moved",
            REQUEST_ENTITY_TOO_LARGE.getCode(), "Please try again with a smaller file"
    );

    private static final String CSS = """
            *, *::before, *::after {
              box-sizing: border-box;
            }
            * {
              margin: 0;
            }
            html {
              font-size: 16px;
              height: 100%;
            }
            body {
              color: #0d0c0c;
              font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
              font-size: clamp(1rem, 2.5vw, 1.2rem);
              -webkit-font-smoothing: antialiased;
              font-style: normal;
              font-weight: 400;
              letter-spacing: -0.0025em;
              line-height: 1.6;
              min-height: 100vh;
              margin: 0;
              padding: 0;
              display: flex;
              justify-content: center;
              align-items: center;
              background-color: #c3cfe2;
              text-rendering: optimizeLegibility;
              -webkit-text-size-adjust: 100%;
            }
            h1 {
                font-size: 2em;
                color: #333;
                margin-bottom: 0.5em;
            }
            h2 {
                font-size: 5.5em;
                opacity: 0.3;
                color: #4A90E2;
                text-shadow: 2px 2px 5px rgba(0, 0, 0, 0.3);
                transition: opacity 0.3s ease-in-out;
                margin-top: -0.2em;
                margin-bottom: 0.2em;
            }
            h2:hover {
                opacity: 1;
            }
            a {
              color: inherit;
              font-weight: 700;
              text-decoration: underline;
              text-underline-offset: 0.1em;
              transition: color 0.3s ease, transform 0.2s ease;
            }
            a:hover {
              color: #4A90E2;
              transform: scale(1.05);
            }
            b, strong {
              font-weight: 700;
              color: #333;
            }
            i, em {
              font-style: italic;
              color: #333;
            }
            main {
              display: flex;
              flex-direction: column;
              gap: 1.5em;
              padding: 2em;
              background-color: #f5f7fa;
              border-radius: 8px;
              box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
              width: 75%;
              margin: 2em;
            }
            main header {
              width: 100%;
              border-bottom: 2px solid #ddd;
              padding-bottom: 1.5em;
              margin-bottom: 1em;
            }
            main article {
              width: 100%;
              color: #d9534f;
              font-size: 1.2rem;
              line-height: 1.5;
              margin-bottom: 1em;
            }
            .error-message {
              background-color: transparent;
              color: #333;
              border: none;
              padding: 0;
              margin-top: 1.5em;
              font-size: 1rem;
              text-align: left;
              font-family: 'Courier New', Courier, monospace;
            }
            .stacktrace-container, .code-container, .request-container {
              margin-top: 1em;
              border: 2px solid #ddd;
              border-radius: 4px;
              overflow: hidden;
              margin-bottom: 1em;
            }
            .section-header {
              background: #f5f5f5;
              padding: 10px 15px;
              border-bottom: 1px solid #ddd;
              display: flex;
              justify-content: space-between;
              align-items: center;
              font-size: 1.1em;
              font-weight: 500;
            }
            .stacktrace-header {
              background: #f5f5f5;
              padding: 10px 15px;
              cursor: pointer;
              border-bottom: 1px solid #ddd;
              display: flex;
              justify-content: space-between;
              align-items: center;
            }
            .stacktrace-content, .code-content {
              padding: 15px;
              background: #fff;
              overflow-x: auto;
              display: none;
            }
            .code-box {
              background-color: #f8f8f8;
              border-radius: 5px;
              margin-bottom: 20px;
              font-family: monospace;
              white-space: pre-wrap;
              font-size: 0.9em;
              line-height: 1.4;
            }
            .stack-line {
              padding: 2px 6px;
              color: #333;
            }
            .code-snippet {
              background-color: #f8f8f8;
              padding: 10px;
              border-radius: 5px;
              margin: 10px 0;
              border-left: 4px solid #4A90E2;
            }
            .code-line {
              padding: 2px 6px;
              white-space: pre;
            }
            .highlighted-line {
              background-color: #ffecec;
              color: #d8000c;
              padding: 2px 6px;
              border-left: 4px solid #d8000c;
            }
            .line-number {
              color: #999;
              margin-right: 10px;
              display: inline-block;
              min-width: 40px;
              text-align: right;
            }
            .collapsible {
              cursor: pointer;
              user-select: none;
            }
            .request-info {
              padding: 15px;
              border-radius: 5px;
              margin-top: 10px;
              margin-bottom: 10px;
            }
            .request-info h3 {
              margin-top: 0;
              margin-bottom: 10px;
              color: #333;
            }
            .request-info-item {
              margin: 5px 0;
              font-family: monospace;
            }
            .button {
              padding: 5px 10px;
              background: #4A90E2;
              color: white;
              border: none;
              border-radius: 3px;
              cursor: pointer;
              font-size: 14px;
              transition: background 0.3s ease;
              margin-left: 10px;
            }
            .button:hover {
              background: #3a7bc8;
            }
            .copy-button {
              float: right;
            }
            .switch {
              position: relative;
              display: inline-block;
              width: 50px;
              height: 24px;
              margin-left: 10px;
            }
            .switch input {
              opacity: 0;
              width: 0;
              height: 0;
            }
            .slider {
              position: absolute;
              cursor: pointer;
              top: 0;
              left: 0;
              right: 0;
              bottom: 0;
              background-color: #ccc;
              transition: .4s;
              border-radius: 24px;
            }
            .slider:before {
              position: absolute;
              content: "";
              height: 16px;
              width: 16px;
              left: 4px;
              bottom: 4px;
              background-color: white;
              transition: .4s;
              border-radius: 50%;
            }
            input:checked + .slider {
              background-color: #4A90E2;
            }
            input:checked + .slider:before {
              transform: translateX(26px);
            }
            .filter-label {
              font-size: 14px;
              margin-right: 10px;
            }
            .filter-toggle {
              display: flex;
              align-items: center;
              margin-left: auto;
            }
            .error-section, .source-code-section, .exception-section {
              margin-bottom: 20px;
            }
            .file-name {
              font-weight: bold;
              margin-bottom: 5px;
              color: #333;
            }
            .json-response-section {
              margin-top: 1em;
              margin-bottom: 1em;
            }
            .json-response-section pre {
              white-space: pre-wrap;
              word-wrap: break-word;
              overflow-x: auto;
              max-width: 100%;
              background-color: #f8f8f8;
              padding: 10px;
              border-radius: 4px;
              border: 1px solid #ddd;
            }
    """;

    private static final String JAVASCRIPT = """
        document.addEventListener('DOMContentLoaded', function() {
            const filterToggle = document.getElementById('filter-toggle');
            if (filterToggle) {
              filterToggle.addEventListener('change', function () {
                const filteredStackTrace = document.getElementById('filtered-stack-trace');
                const fullStackTrace = document.getElementById('full-stack-trace');
                if (this.checked) {
                  fullStackTrace.style.display = 'block';
                  filteredStackTrace.style.display = 'none';
                  expandStackTrace(fullStackTrace);
                } else {
                  filteredStackTrace.style.display = 'block';
                  fullStackTrace.style.display = 'none';
                  expandStackTrace(filteredStackTrace);
                }
              });
            }
            function expandStackTrace(stackTraceElement) {
              const headerElement = stackTraceElement.querySelector('.stacktrace-header.collapsible');
              const contentElement = stackTraceElement.querySelector('.stacktrace-content');
              if (headerElement && contentElement) {
                headerElement.classList.add('active');
                contentElement.style.display = 'block';
                const toggleIcon = headerElement.querySelector('.toggle-icon');
                if (toggleIcon) toggleIcon.textContent = '▲';
              }
            }
            document.querySelectorAll('.copy-button').forEach(function(button) {
                button.addEventListener('click', function(e) {
                    e.stopPropagation();
                    const textToCopy = this.closest('.stacktrace-container').querySelector('.stacktrace-content').innerText;
                    navigator.clipboard.writeText(textToCopy).then(function() {
                        const originalText = button.innerText;
                        button.innerText = 'Copied!';
                        setTimeout(function() {
                            button.innerText = originalText;
                        }, 2000);
                    });
                });
            });
            document.querySelectorAll('.collapsible').forEach(function(header) {
                header.addEventListener('click', function() {
                    const content = this.nextElementSibling;
                    const toggleIcon = this.querySelector('.toggle-icon');
                    if (content.style.display === 'none' || content.style.display === '') {
                        content.style.display = 'block';
                        if (toggleIcon) toggleIcon.textContent = '▲';
                    } else {
                        content.style.display = 'none';
                        if (toggleIcon) toggleIcon.textContent = '▼';
                    }
                });
            });
        });
    """;

    private final HtmlSanitizer htmlSanitizer;
    private final MessageSource messageSource;
    private final LocaleResolver<HttpRequest<?>> localeResolver;
    private final ObjectMapper objectMapper;
    private final JsonErrorResponseBodyProvider<JsonError> jsonErrorResponseBodyProvider;

    DefaultHtmlProvider(HtmlSanitizer htmlSanitizer,
                        MessageSource messageSource,
                        LocaleResolver<HttpRequest<?>> localeResolver,
                        ObjectMapper objectMapper,
                        JsonErrorResponseBodyProvider<JsonError> jsonErrorResponseBodyProvider) {
        this.htmlSanitizer = htmlSanitizer;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
        this.objectMapper = objectMapper;
        this.jsonErrorResponseBodyProvider = jsonErrorResponseBodyProvider;
    }

    @Override
    public String body(ErrorContext errorContext, HttpResponse<?> response) {
        HtmlErrorPage key = error(errorContext, response);
        try {
            return html(key, errorContext, response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String html(@NonNull HtmlErrorPage htmlErrorPage, ErrorContext errorContext, HttpResponse<?> response) throws IOException {
        final String errorTitleCode = htmlErrorPage.httpStatusCode() + ".error.title";
        final String errorTitle = messageSource.getMessage(errorTitleCode, htmlErrorPage.httpStatusReason(), htmlErrorPage.locale());

        String header = "<h1>" + errorTitle + "</h1><h2>" + htmlErrorPage.httpStatusCode() + "</h2>";

        boolean isProduction = "production".equalsIgnoreCase(environment);

        String sourceCodeHtml = isProduction ? "" : buildSourceCodeSection(extractCodeSnippets(errorContext));
        String stackTraceHtml = isProduction ? "" : buildStackTraceSection(errorContext);
        String requestInfoHtml = buildRequestInfoSection(errorContext);
        String jsonResponseHtml = isProduction ? "" : buildJsonResponseSection(errorContext, response);

        return MessageFormat.format("""
                <!doctype html>
                <html lang="en">
                <head>
                    <title>{0} — {1}</title>
                    <meta charset="utf-8">
                    <meta name="viewport" content="initial-scale=1, width=device-width">
                    <meta name="robots" content="noindex, nofollow">
                    <style>{2}</style>
                    <script>{3}</script>
                </head>
                <body>
                    <main>
                        <header>{4}</header>
                        <article>{5}</article>
                        {6}
                        {7}
                        {8}
                        {9}
                    </main>
                </body>
                </html>
                """,
                htmlErrorPage.httpStatusCode(),
                errorTitle,
                CSS,
                JAVASCRIPT,
                header,
                article(htmlErrorPage),
                sourceCodeHtml,
                stackTraceHtml,
                requestInfoHtml,
                jsonResponseHtml
        );
    }

    private HtmlErrorPage error(ErrorContext errorContext, HttpResponse<?> response) {
        int httpStatusCode = response.code();
        Locale locale = localeResolver.resolveOrDefault(errorContext.getRequest());

        String errorBold = getMessage(httpStatusCode + ".error.bold", DEFAULT_ERROR_BOLD.get(httpStatusCode), locale);
        String error = getMessage(httpStatusCode + ".error", DEFAULT_ERROR.get(httpStatusCode), locale);
        String httpStatusReason = htmlSanitizer.sanitize(response.reason());

        return new HtmlErrorPage(locale, httpStatusCode, httpStatusReason, error, errorBold);
    }

    private String getMessage(String code, String defaultMessage, Locale locale) {
        return defaultMessage != null
                ? messageSource.getMessage(code, defaultMessage, locale)
                : messageSource.getMessage(code, locale).orElse(null);
    }

    private List<CodeSnippet> extractCodeSnippets(ErrorContext errorContext) {
        List<CodeSnippet> snippets = new ArrayList<>();
        if (errorContext == null) return snippets;

        Optional<Throwable> exception = errorContext.getRootCause();
        if (exception.isEmpty()) return snippets;

        String stackTraceText = getStackTraceAsString(exception.get());
        Set<String> processedFiles = new HashSet<>();

        for (String line : stackTraceText.split("\n")) {
            if (shouldFilterLine(line)) continue;

            Optional<StackTraceElement> maybeElement = parseStackTraceLine(line.trim());
            if (maybeElement.isPresent()) {
                StackTraceElement element = maybeElement.get();
                String fileLineKey = element.getClassName() + ":" + element.getLineNumber();

                if (!processedFiles.contains(fileLineKey)) {
                    String codeSnippet = getCodeFromElement(element);
                    if (codeSnippet != null && !codeSnippet.isEmpty()) {
                        snippets.add(new CodeSnippet(
                                element.getClassName(),
                                getFileNameFromClass(element.getClassName()),
                                element.getLineNumber(),
                                codeSnippet
                        ));
                        processedFiles.add(fileLineKey);
                    }
                }
            }
        }

        return snippets;
    }

    private String getStackTraceAsString(Throwable exception) {
        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private boolean shouldFilterLine(String line) {
        return line.contains(filterPrefixMicronaut) ||
                line.contains(filterPrefixNetty) ||
                line.contains(filterUnknownSource);
    }

    private String getFileNameFromClass(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0 && lastDot < className.length() - 1) {
            return className.substring(lastDot + 1) + ".java";
        }
        return className + ".java";
    }

    private String buildSourceCodeSection(List<CodeSnippet> codeSnippets) {
        if (codeSnippets.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"source-code-section\">")
                .append("<div class=\"code-container\">")
                .append("<div class=\"stacktrace-header collapsible\">Source Code <span class=\"toggle-icon\">▼</span></div>")
                .append("<div class=\"code-content\">");

        for (CodeSnippet snippet : codeSnippets) {
            sb.append("<div class=\"code-snippet\">")
                    .append("<div class=\"file-name\">").append(snippet.fileName()).append("</div>")
                    .append(snippet.codeHtml())
                    .append("</div>");
        }

        sb.append("</div></div></div>");
        return sb.toString();
    }

    private String buildRequestInfoSection(ErrorContext errorContext) {
        if (errorContext == null || errorContext.getRequest() == null) return "";

        HttpRequest<?> request = errorContext.getRequest();
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"error-section\">")
                .append("<div class=\"request-container\">")
                .append("<div class=\"section-header\">Request Information</div>")
                .append("<div class=\"request-info\">")
                .append("<div class=\"request-info-item\"><strong>Method:</strong> ").append(request.getMethod()).append("</div>")
                .append("<div class=\"request-info-item\"><strong>URL:</strong> ").append(request.getUri().toString()).append("</div>")
                .append("<div class=\"stacktrace-header collapsible\">Headers <span class=\"toggle-icon\">▼</span></div>")
                .append("<div class=\"stacktrace-content\">");

        request.getHeaders().forEach((name, values) -> {
            sb.append("<div class=\"request-info-item\">")
                    .append(name).append(": ").append(String.join(", ", values))
                    .append("</div>");
        });

        sb.append("</div></div></div></div>");
        return sb.toString();
    }

    private String buildStackTraceSection(ErrorContext errorContext) {
        if (errorContext == null) return "";
        Optional<Throwable> exception = errorContext.getRootCause();
        if (exception.isEmpty()) return "";

        return "<div class=\"exception-section\">" +
                createStackTraceContainer(exception.get(), false) +
                createStackTraceContainer(exception.get(), true) +
                "</div>";
    }

    private String createStackTraceContainer(Throwable exception, boolean showFullStackTrace) {
        String exceptionInfo = exception.getClass().getName() +
                (exception.getMessage() != null ? ": " + exception.getMessage() : "");
        String containerId = showFullStackTrace ? "full-stack-trace" : "filtered-stack-trace";
        String initialStyle = showFullStackTrace ? "display: none;" : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"").append(containerId).append("\" class=\"stacktrace-container\" style=\"")
                .append(initialStyle).append("\">")
                .append("<div class=\"stacktrace-header collapsible\">")
                .append("Stack Trace: ").append(exceptionInfo);

        if (!showFullStackTrace) {
            sb.append("<div>")
                    .append("<span class=\"filter-label\">Show Full Stack:</span>")
                    .append("<label class=\"switch\">")
                    .append("<input type=\"checkbox\" id=\"filter-toggle\">")
                    .append("<span class=\"slider\"></span>")
                    .append("</label>")
                    .append("</div>");
        }

        sb.append("<span class=\"toggle-icon\">▼</span>")
                .append("<button class=\"button copy-button\">Copy</button>")
                .append("</div>")
                .append("<div class=\"stacktrace-content\">");

        String stackTraceText = getStackTraceAsString(exception);
        for (String line : stackTraceText.split("\n")) {
            if (!showFullStackTrace && shouldFilterLine(line)) continue;

            sb.append("<div class=\"stack-line\">")
                    .append(htmlSanitizer.sanitize(line))
                    .append("</div>");
        }

        sb.append("</div></div>");
        return sb.toString();
    }

    private String article(@NonNull HtmlErrorPage htmlErrorPage) {
        StringBuilder sb = new StringBuilder();
        if (htmlErrorPage.error() != null || htmlErrorPage.errorBold() != null) {
            sb.append("<p>");
            if (htmlErrorPage.errorBold() != null) {
                sb.append("<strong>").append(htmlErrorPage.errorBold()).append("</strong>. ");
            }
            if (htmlErrorPage.error() != null) {
                sb.append(htmlErrorPage.error()).append(".");
            }
            sb.append("</p>");
        }
        return sb.toString();
    }

    private Optional<StackTraceElement> parseStackTraceLine(String line) {
        try {
            if (!line.startsWith("at ")) return Optional.empty();
            line = line.substring(3);

            int openParen = line.indexOf('(');
            int closeParen = line.indexOf(')');
            if (openParen == -1 || closeParen == -1) return Optional.empty();

            String methodInfo = line.substring(0, openParen);
            String fileInfo = line.substring(openParen + 1, closeParen);

            if (!fileInfo.contains(":")) return Optional.empty();

            String[] parts = fileInfo.split(":");
            String fileName = parts[0];
            int lineNumber = Integer.parseInt(parts[1]);

            int lastDot = methodInfo.lastIndexOf('.');
            String className = methodInfo.substring(0, lastDot);

            return Optional.of(new StackTraceElement(className, "", fileName, lineNumber));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String getCodeFromElement(StackTraceElement element) {
        try {
            int lineNumber = element.getLineNumber();
            if (lineNumber < 0) return null;

            Path path = getPathFromClass(element.getClassName());
            if (!Files.exists(path)) return null;

            return readCodeSnippet(path, lineNumber);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readCodeSnippet(Path path, int lineNumber) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            StringBuilder codeHtml = new StringBuilder();
            String line;
            int currentLine = 1;
            int startLine = Math.max(1, lineNumber - 3);
            int endLine = lineNumber + 2;

            while ((line = reader.readLine()) != null) {
                if (currentLine >= startLine && currentLine <= endLine) {
                    String cssClass = currentLine == lineNumber ? "highlighted-line" : "code-line";
                    codeHtml.append("<div class=\"").append(cssClass).append("\">")
                            .append("<span class=\"line-number\">").append(currentLine).append("</span> ")
                            .append(line)
                            .append("</div>");
                }
                if (currentLine > endLine) break;
                currentLine++;
            }
            return codeHtml.toString();
        }
    }

    private Path getPathFromClass(String className) {
        String relativePath = className.replace('.', '/') + ".java";
        return Paths.get("src/main/java", relativePath);
    }

    private String buildJsonResponseSection(ErrorContext errorContext, HttpResponse<?> response) {
        if (errorContext == null) return "";

        try {
            JsonError jsonBody = jsonErrorResponseBodyProvider.body(errorContext, response);
            String jsonString = objectMapper.writeValueAsString(jsonBody);

            return "<div class=\"json-response-section\">" +
                    "<div class=\"stacktrace-header collapsible\">JSON Response <span class=\"toggle-icon\">▼</span></div>" +
                    "<div class=\"stacktrace-content\">" +
                    "<pre style=\"white-space: pre-wrap; overflow-x: auto;\">" +
                    htmlSanitizer.sanitize(jsonString) +
                    "</pre>" +
                    "</div>" +
                    "</div>";
        } catch (Exception ignored) {
            return "";
        }
    }

    private record HtmlErrorPage(
            Locale locale,
            int httpStatusCode,
            String httpStatusReason,
            String error,
            String errorBold
    ) {}

    private record CodeSnippet(
            String className,
            String fileName,
            int lineNumber,
            String codeHtml
    ) {}
}