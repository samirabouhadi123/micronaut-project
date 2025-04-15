package example.com.exceptions.response.error;

import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.LocaleResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.HtmlErrorResponseBodyProvider;
import io.micronaut.http.util.HtmlSanitizer;
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
class DefaultHtmlProvider implements HtmlErrorResponseBodyProvider {


    @Value("${filter.prefix.micronaut}")
    protected String filterPrefixMicronaut;

    @Value("${filter.prefix.netty}")
    protected String filterPrefixNetty;


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
              width: 90%;
              max-width: 50em;
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
            .stacktrace-container {
              margin-top: 1em;
              border: 1px solid #ddd;
              border-radius: 4px;
              overflow: hidden;
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
            .stacktrace-content {
              padding: 15px;
              background: #fff;
              overflow-x: auto;
              max-height: 500px;
              overflow-y: auto;
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
              background: #f5f5f5;
              padding: 15px;
              border-radius: 5px;
              margin-top: 10px;
              margin-bottom: 10px;
              border: 1px solid #ddd;
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
            .language-java .keyword { color: #0033b3; }
            .language-java .string { color: #067d17; }
            .language-java .comment { color: #8c8c8c; font-style: italic; }
            .language-java .number { color: #1750eb; }
            .language-java .method { color: #7a3e9d; }
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
    """;

    private static final String JAVASCRIPT = """
            document.addEventListener('DOMContentLoaded', function() {
                const collapsibles = document.querySelectorAll('.collapsible');
                collapsibles.forEach(function(collapsible) {
                    collapsible.addEventListener('click', function() {
                        this.classList.toggle('active');
                        const content = this.nextElementSibling;
                        if (content.style.maxHeight) {
                            content.style.maxHeight = null;
                            this.querySelector('.toggle-icon').textContent = '▼';
                        } else {
                            content.style.maxHeight = content.scrollHeight + 'px';
                            this.querySelector('.toggle-icon').textContent = '▲';
                        }
                    });
                });
                // Add click event for copy button
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
            });
    """;

    private final HtmlSanitizer htmlSanitizer;
    private final MessageSource messageSource;
    private final LocaleResolver<HttpRequest<?>> localeResolver;

    DefaultHtmlProvider(HtmlSanitizer htmlSanitizer,
                        MessageSource messageSource,
                        LocaleResolver<HttpRequest<?>> localeResolver) {
        this.htmlSanitizer = htmlSanitizer;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    private String html(@NonNull HtmlErrorPage htmlErrorPage, ErrorContext errorContext) throws IOException {
        final String errorTitleCode = htmlErrorPage.httpStatusCode() + ".error.title";
        final String errorTitle = messageSource.getMessage(errorTitleCode, htmlErrorPage.httpStatusReason(), htmlErrorPage.locale());

        String header = "<h1>" + errorTitle + "</h1>";
        header += "<h2>" + htmlErrorPage.httpStatusCode() + "</h2>";

        String stackTraceHtml = getStackTrace(errorContext);
        String requestInfoHtml = getRequestInfo(errorContext);

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
                requestInfoHtml,
                "production".equalsIgnoreCase(environment) ? "" : stackTraceHtml
        );
    }

    private String getRequestInfo(ErrorContext errorContext) {
        if (errorContext == null || errorContext.getRequest() == null) {
            return "";
        }

        HttpRequest<?> request = errorContext.getRequest();
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"request-info\">");
        sb.append("<h3>Request Information</h3>");

        sb.append("<div class=\"request-info-item\"><strong>Method:</strong> ")
                .append(request.getMethod())
                .append("</div>");

        sb.append("<div class=\"request-info-item\"><strong>URL:</strong> ")
                .append(request.getUri().toString())
                .append("</div>");

        sb.append("<div class=\"stacktrace-header collapsible\">")
                .append("Headers <span class=\"toggle-icon\">▼</span>")
                .append("</div>");

        sb.append("<div class=\"stacktrace-content\" style=\"max-height: 0; overflow: hidden;\">");
        request.getHeaders().forEach((name, values) -> {
            sb.append("<div class=\"request-info-item\">")
                    .append(name)
                    .append(": ")
                    .append(String.join(", ", values))
                    .append("</div>");
        });
        sb.append("</div>");

        sb.append("</div>");

        return sb.toString();
    }

    public String getStackTrace(ErrorContext errorContext) throws IOException {
        if (errorContext == null) return "";
        Optional<Throwable> exception = errorContext.getRootCause();
        if (exception.isEmpty()) return "";

        StringWriter stringWriter = new StringWriter();
        exception.get().printStackTrace(new PrintWriter(stringWriter));
        String stackTraceText = stringWriter.toString();
        String[] lines = stackTraceText.split("\n");

        StringBuilder stackTraceHtml = new StringBuilder();
        stackTraceHtml.append("<div class=\"stacktrace-container\">");

        stackTraceHtml.append("<div class=\"stacktrace-header collapsible\">")
                .append("Stack Trace: ")
                .append(htmlSanitizer.sanitize(exception.get().toString()))
                .append(" <span class=\"toggle-icon\">▼</span>")
                .append("<button class=\"button copy-button\">Copy</button>")
                .append("</div>");

        stackTraceHtml.append("<div class=\"stacktrace-content\" style=\"max-height: 0; overflow: hidden;\">");

        List<String> processedLines = new ArrayList<>();
        for (String line : lines) {
            // Skip filtered packages
            if (line.contains(filterPrefixMicronaut) || line.contains(filterPrefixNetty)) {
                continue;
            }

            processedLines.add(line);

            // Check if we can add a code snippet
            Optional<StackTraceElement> maybeElement = parseStackTraceLine(line.trim());
            if (maybeElement.isPresent()) {
                StackTraceElement element = maybeElement.get();
                String snippet = getCodeSnippetFromElement(element);
                if (snippet != null && !snippet.isBlank()) {
                    processedLines.add(snippet);
                }
            }
        }

        for (String line : processedLines) {
            if (line.startsWith("<div class=\"code-snippet\"") || line.endsWith("</div>\n\n")) {
                stackTraceHtml.append(line);
            } else {
                stackTraceHtml.append("<div class=\"stack-line\">")
                        .append(htmlSanitizer.sanitize(line))
                        .append("</div>");
            }
        }

        stackTraceHtml.append("</div></div>");

        return stackTraceHtml.toString();
    }

    private String article(@NonNull HtmlErrorPage htmlErrorPage) {
        StringBuilder sb = new StringBuilder();

        String error = htmlErrorPage.error();
        String errorBold = htmlErrorPage.errorBold();
        if (error != null || errorBold != null) {
            sb.append("<p>");
            if (errorBold != null) {
                sb.append("<strong>");
                sb.append(errorBold);
                sb.append("</strong>. ");
            }
            if (error != null) {
                sb.append(error);
                sb.append(".");
            }
            sb.append("</p>");
        }
        return sb.toString();
    }

    @Override
    public String body(ErrorContext errorContext, HttpResponse<?> response) {
        HtmlErrorPage key = error(errorContext, response);
        try {
            return html(key, errorContext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HtmlErrorPage error(ErrorContext errorContext, HttpResponse<?> response) {
        int httpStatusCode = response.code();
        Locale locale = localeResolver.resolveOrDefault(errorContext.getRequest());
        final String errorBoldCode = httpStatusCode + ".error.bold";
        final String errorCode = httpStatusCode + ".error";
        String defaultErrorBold = DEFAULT_ERROR_BOLD.get(httpStatusCode);
        String defaultError = DEFAULT_ERROR.get(httpStatusCode);
        String errorBold = defaultErrorBold != null
                ? messageSource.getMessage(errorBoldCode, defaultErrorBold, locale)
                : messageSource.getMessage(errorBoldCode, locale).orElse(null);
        String error = defaultError != null
                ? messageSource.getMessage(errorCode, defaultError, locale)
                : messageSource.getMessage(errorCode, locale).orElse(null);
        String httpStatusReason = htmlSanitizer.sanitize(response.reason());

        return new HtmlErrorPage(locale, httpStatusCode, httpStatusReason, error, errorBold);
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

    private String getCodeSnippetFromElement(StackTraceElement element) {
        try {
            String className = element.getClassName();
            int lineNumber = element.getLineNumber();
            if (lineNumber < 0) return null;

            Path path = getPathFromClass(className);

            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                int startIndex = Math.max(0, lineNumber - 3);
                int endIndex = Math.min(lines.size(), lineNumber + 3);

                StringBuilder codeSnippet = new StringBuilder();
                codeSnippet.append("<div class=\"code-snippet\">");
                codeSnippet.append("<div style=\"margin-bottom:5px;\"><strong>")
                        .append(path.getFileName())
                        .append("</strong></div>");

                for (int i = startIndex; i < endIndex; i++) {
                    String line = lines.get(i);
                    if (i + 1 == lineNumber) {
                        codeSnippet.append("<div class=\"highlighted-line\">");
                    } else {
                        codeSnippet.append("<div class=\"code-line\">");
                    }
                    codeSnippet.append("<span class=\"line-number\">")
                            .append(String.format("%d", i + 1))
                            .append("</span> ")
                            .append(line)
                            .append("</div>");
                }
                codeSnippet.append("</div>\n\n");
                return codeSnippet.toString();
            }
        } catch (Exception ignored) {

        }
        return null;
    }


    private Path getPathFromClass(String className) {
        String relativePath = className.replace('.', '/') + ".java";
        return Paths.get("src/main/java", relativePath);
    }

    private record HtmlErrorPage(Locale locale,
                                 int httpStatusCode,
                                 String httpStatusReason,
                                 String error,
                                 String errorBold
    ) {
    }
}