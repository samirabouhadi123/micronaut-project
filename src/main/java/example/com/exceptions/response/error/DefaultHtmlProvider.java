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

    private static final Map<Integer, String> DEFAULT_ERROR_BOLD = Map.of(
            NOT_FOUND.getCode(), "the page is not available",
            REQUEST_ENTITY_TOO_LARGE.getCode(), "The file or data you are trying to upload exceeds the size"
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
            }
            h2 {
                margin-top: -0.95em;
                font-size: 6em;
                opacity: .2;
            }
            body {
              background: #2559a7;
              color: #FFF;
              display: grid;
              font-family: -apple-system, "Helvetica Neue", Helvetica, sans-serif;
              font-size: clamp(1rem, 2.5vw, 2rem);
              -webkit-font-smoothing: antialiased;
              font-style: normal;
              font-weight: 400;
              letter-spacing: -0.0025em;
              line-height: 1.4;
              min-height: 100vh;
              place-items: center;
              text-rendering: optimizeLegibility;
              -webkit-text-size-adjust: 100%;
            }
            a {
              color: inherit;
              font-weight: 700;
              text-decoration: underline;
              text-underline-offset: 0.0925em;
            }
            b, strong {
              font-weight: 700;
            }
            i, em {
              font-style: italic;
            }
            main {
              display: grid;
              gap: 1em;
              padding: 2em;
              place-items: center;
              text-align: center;
            }
            main header {
              width: min(100%, 18em);
            }
            main header svg {
              height: auto;
              max-width: 100%;
              width: 100%;
            }
            main article {
              margin-top: -0.95em;
              width: min(100%, 30em);
            }
            main article p {
              font-size: 75%;
            }
            main article br {
              display: none;
              @media(min-width: 48em) {
                display: inline;
              }
            }
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
        header += "<h2>" + htmlErrorPage.httpStatusCode() + "</h1>";

        String stackTraceHtml = getStackTrace(errorContext);

        return MessageFormat.format("<!doctype html><html lang=\"en\"><head><title>{0} — {1}</title><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"initial-scale=1, width=device-width\"><meta name=\"robots\" content=\"noindex, nofollow\"><style>{2}</style></head><body><main><header>{3}</header><article>{4}{5}</article></main></body></html>",
                htmlErrorPage.httpStatusCode(),
                errorTitle,
                CSS,
                header,
                article(htmlErrorPage),
                stackTraceHtml);
    }

    public String getStackTrace(ErrorContext errorContext) throws IOException {

        if (errorContext == null) {
            return null;
        }
        @NonNull Optional<Throwable> exception = errorContext.getRootCause();
        if (exception.isEmpty()) {
            return null;
        }

        // Get the code snippet
        String codeSnippet = getCodeSnippet(errorContext);
        final StringWriter stringWriter = new StringWriter();
        exception.get().printStackTrace(new PrintWriter(stringWriter));

        // Filter out framework internals
        String[] lines = stringWriter.toString().split("\n");
        StringBuilder filteredStackTrace = new StringBuilder();

        for (String line : lines) {
            if (!(line.contains(filterPrefixMicronaut) || line.contains(filterPrefixNetty))) {
                filteredStackTrace.append(line).append("\n");
            }
        }

        // Combine the stack trace and code snippet
        String combinedOutput = filteredStackTrace + "\n\nCode Snippet:\n" + codeSnippet;

        return "<div style='outline: 50px solid transparent; padding: 10px; overflow-x: auto; max-width: 80%;'><pre style='font-size: 15px;'>" +
                combinedOutput +
                "</pre></div>";
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

    private record HtmlErrorPage(Locale locale,
                                 int httpStatusCode,
                                 String httpStatusReason,
                                 String error,
                                 String errorBold
    ) {
    }
    //
    private String getFullPath(String className) {
        int lastDotIndex = className.lastIndexOf(".");
        if (lastDotIndex > 0) {
            String packageName = className.substring(0, lastDotIndex).replace('.', '/');
            return packageName;
        } else {
            return "";
        }
    }

    private String getCodeSnippet(ErrorContext errorContext) throws IOException {

        Throwable rootCause = errorContext.getRootCause().get();


        StackTraceElement[] stackTraceElements = rootCause.getStackTrace();

        if (stackTraceElements == null || stackTraceElements.length == 0) {
            throw new IllegalStateException("No stack trace elements found.");
        }

        StringBuilder codeSnippets = new StringBuilder();

        for (StackTraceElement stackTraceElement : stackTraceElements) {
            if (stackTraceElement.getFileName() == null) {
                continue;
            }
            if (stackTraceElement.getClassName().contains(filterPrefixMicronaut) || stackTraceElement.getClassName().contains(filterPrefixNetty)) {
                continue;
            }


            int lineNumber = stackTraceElement.getLineNumber();

            String packageName = getFullPath(stackTraceElement.getClassName());

            String path = "src/main/java/" + packageName + "/" + stackTraceElement.getFileName();
            Path filePath = Paths.get(System.getProperty("user.dir"), path);

            try {

                List<String> lines = Files.readAllLines(filePath);


                StringBuilder codeSnippet = new StringBuilder();

                int startIndex = Math.max(0, lineNumber - 5);
                int endIndex = Math.min(lines.size(), lineNumber + 5);

                for (int i = startIndex; i < endIndex; i++) {
                    String line = lines.get(i);
                    codeSnippet.append(line).append("\n");
                }

                codeSnippets.append("Exception at ").append(stackTraceElement.getClassName()).append(":").append(lineNumber).append("\n")
                        .append(codeSnippet.toString()).append("\n\n");

            } catch (IOException e) {
                codeSnippets.append("Failed to read file: ").append(filePath).append("\n\n");
            }
        }

        return codeSnippets.toString();
    }

}