package example.com.exceptions.response.error;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.*;
import io.micronaut.http.hateoas.JsonError;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link ErrorResponseProcessor}.
 * It delegates to {@link JsonErrorResponseBodyProvider} for JSON responses and to {@link HtmlProvider} for HTML responses.
 *
 * @author Sergio del Amo
 * @since 4.7.0
 */
@Internal
@Singleton
@Requires(missingBeans = ErrorResponseProcessor.class)
final class DefaultErrorResponseProcessor implements ErrorResponseProcessor {
    private final JsonErrorResponseBodyProvider<?> jsonBodyErrorResponseProvider;
    private final HtmlProvider htmlBodyErrorResponseProvider;

    DefaultErrorResponseProcessor(JsonErrorResponseBodyProvider<?> jsonBodyErrorResponseProvider,
                                  HtmlProvider htmlBodyErrorResponseProvider) {
        this.jsonBodyErrorResponseProvider = jsonBodyErrorResponseProvider;
        this.htmlBodyErrorResponseProvider = htmlBodyErrorResponseProvider;
    }

    @Override
    public MutableHttpResponse processResponse(ErrorContext errorContext, MutableHttpResponse response) {
        HttpRequest<?> request = errorContext.getRequest();
        if (request.getMethod() == HttpMethod.HEAD) {
            return (MutableHttpResponse<JsonError>) response;
        }
        final boolean isError = response.status().getCode() >= 400;
        if (isError
                && request.accept().stream().anyMatch(mediaType -> mediaType.equals(MediaType.TEXT_HTML_TYPE))
                && request.accept().stream().noneMatch(m -> m.matchesExtension(MediaType.EXTENSION_JSON))
        ) {
            return response.body(htmlBodyErrorResponseProvider.body(errorContext, response))
                    .contentType(htmlBodyErrorResponseProvider.contentType());
        }
        return response.body(jsonBodyErrorResponseProvider.body(errorContext, response))
                .contentType(jsonBodyErrorResponseProvider.contentType());
    }
}
