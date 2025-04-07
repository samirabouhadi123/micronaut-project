package example.com.exceptions.response.error;


import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Internal
final class DefaultErrorContext implements ErrorContext {

    private final HttpRequest<?> request;
    private final Throwable cause;
    private final List<Error> jsonErrors;

    private DefaultErrorContext(@NonNull HttpRequest<?> request,
                                @Nullable Throwable cause,
                                @NonNull List<Error> jsonErrors) {
        this.request = request;
        this.cause = cause;
        this.jsonErrors = jsonErrors;
    }

    @Override
    @NonNull
    public HttpRequest<?> getRequest() {
        return request;
    }

    @Override
    public Throwable getRootCause() {
        return cause;
    }

    @Override
    @NonNull
    public List<Error> getErrors() {
        return jsonErrors;
    }

    /**
     * Creates a context builder for this implementation.
     *
     * @param request The request
     * @return A new builder
     */
    public static Builder builder(@NonNull HttpRequest<?> request) {
        return new Builder(request);
    }

    private static final class Builder implements ErrorContext.Builder {

        private final HttpRequest<?> request;
        private Throwable cause;
        private final List<Error> jsonErrors = new ArrayList<>();

        private Builder(@NonNull HttpRequest<?> request) {
            this.request = request;
        }

        @Override
        @NonNull
        public Builder cause(@Nullable Throwable cause) {
            this.cause = cause;
            return this;
        }

        @Override
        @NonNull
        public Builder errorMessage(@NonNull String message) {
            jsonErrors.add(() -> message);
            return this;
        }

        @Override
        @NonNull
        public Builder error(@NonNull Error error) {
            jsonErrors.add(error);
            return this;
        }

        @Override
        @NonNull
        public Builder errorMessages(@NonNull List<String> errors) {
            for (String error: errors) {
                errorMessage(error);
            }
            return this;
        }

        @Override
        @NonNull
        public Builder errors(@NonNull List<Error> errors) {
            jsonErrors.addAll(errors);
            return this;
        }

        @Override
        @NonNull
        public ErrorContext build() {
            return new DefaultErrorContext(request, cause, jsonErrors);
        }
    }
}
