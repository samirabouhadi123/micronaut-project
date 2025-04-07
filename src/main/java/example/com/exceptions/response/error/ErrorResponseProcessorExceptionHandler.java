package example.com.exceptions.response.error;


import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;

/**
 * An abstract class to handle exceptions via an HTTP Response and the {@link ErrorResponseProcessor} API.
 *
 * @param <T> The throwable
 * @author Sergio del Amo
 * @since 4.6.0
 */
public abstract class ErrorResponseProcessorExceptionHandler<T extends Throwable> implements ExceptionHandler<T, HttpResponse<?>> {

    protected final ErrorResponseProcessor<?> responseProcessor;

    /**
     * Constructor.
     *
     * @param responseProcessor Error Response Processor
     */
    protected ErrorResponseProcessorExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, T exception) {
        return responseProcessor.processResponse(ErrorContext.builder(request)
                .cause(exception)
                .errorMessage(exception.getMessage())
                .build(), createResponse(exception));
    }

    @NonNull
    protected abstract MutableHttpResponse<?> createResponse(T exception);
}
