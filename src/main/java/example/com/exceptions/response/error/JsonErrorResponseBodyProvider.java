package example.com.exceptions.response.error;


import io.micronaut.http.MediaType;

/**
 * A {@link ErrorResponseBodyProvider} for JSON responses.
 * Responses with content type {@link io.micronaut.http.MediaType#APPLICATION_JSON}.
 * @author Sergio del Amo
 * @since 4.7.0
 * @param <T> The error type
 */
@FunctionalInterface
public interface JsonErrorResponseBodyProvider<T> extends ErrorResponseBodyProvider<T> {
    @Override
    default String contentType() {
        return MediaType.APPLICATION_JSON;
    }
}
