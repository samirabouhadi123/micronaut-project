package example.com.exceptions.response.error;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MutableHttpResponse;

/**
 * Creates Http responses that represent errors.
 *
 * @param <T> The response body type
 * @author James Kleeh
 * @since 2.4.0
 */
@DefaultImplementation(DefaultErrorResponseProcessor.class)
public interface ErrorResponseProcessor<T> {

    /**
     * Modifies the http response representing the error. Callers of this
     * method should return the response that was passed in baseResponse parameter,
     * however that isn't required.
     *
     * Error response processors should not set the body or content type if
     * the request method is HEAD.
     *
     * @param errorContext The error context
     * @param baseResponse The base response to retrieve information or
     *                     mutate
     * @return An error response
     */
    @NonNull
    MutableHttpResponse<T> processResponse(@NonNull ErrorContext errorContext, @NonNull MutableHttpResponse<?> baseResponse);

}