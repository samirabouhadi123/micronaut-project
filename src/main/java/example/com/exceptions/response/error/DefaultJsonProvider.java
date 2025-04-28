package example.com.exceptions.response.error;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.JsonErrorResponseBodyProvider;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
@Primary
public class DefaultJsonProvider implements JsonErrorResponseBodyProvider<JsonError> {

    @Value("${filter.prefix.micronaut}")
    protected String filterPrefixMicronaut;

    @Value("${filter.prefix.netty}")
    protected String filterPrefixNetty;

    @Value("${filter.unknown.source:Unknown Source}")
    protected String filterUnknownSource;

    @Value("${micronaut.environment:development}")
    protected String environment;

    @Override
    public JsonError body(ErrorContext errorContext, HttpResponse<?> response) {

        ExtendedJsonError jsonError;

        if (!errorContext.hasErrors()) {
            jsonError = new ExtendedJsonError(response.reason());
        } else if (errorContext.getErrors().size() == 1) {
            Error error = errorContext.getErrors().getFirst();
            jsonError = new ExtendedJsonError(error.getMessage());
            error.getPath().ifPresent(jsonError::path);
        } else {
            jsonError = new ExtendedJsonError(response.reason());

            List<Map<String, Object>> errors = new ArrayList<>(errorContext.getErrors().size());
            for (Error error : errorContext.getErrors()) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("message", error.getMessage());
                error.getPath().ifPresent(path -> errorMap.put("path", path));
                errors.add(errorMap);
            }
            jsonError.setErrors(errors);
        }

        HttpRequest<?> request = errorContext.getRequest();
        if (request != null) {
            if (jsonError.getPath().isEmpty()) {
                jsonError.path(request.getUri().getPath());
            }
            jsonError.link(Link.SELF, request.getUri().toString());
        }

        jsonError.setTimestamp(new Date().toString());
        jsonError.setStatus(response.status().getCode());
        jsonError.setError(response.getStatus().getReason());


        if ("development".equals(environment)) {
            Optional<Throwable> exception = errorContext.getRootCause();
            if (exception.isPresent()) {
                Map<String, Object> exceptionDetails = getStringObjectMap(exception);

                jsonError.setException(exceptionDetails);
            }
        }

        return jsonError;
    }

    private Map<String, Object> getStringObjectMap(Optional<Throwable> exception) {
        Map<String, Object> exceptionDetails = new HashMap<>();
        Throwable throwable = exception.get();

        exceptionDetails.put("type", throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            exceptionDetails.put("message", throwable.getMessage());
        }


        if (throwable.getStackTrace() != null && throwable.getStackTrace().length > 0) {
            List<String> filteredStackTrace = new ArrayList<>();

            for (StackTraceElement element : throwable.getStackTrace()) {
                String elementString = element.toString();
                if (!elementString.contains(filterPrefixMicronaut) &&
                        !elementString.contains(filterPrefixNetty) &&
                        !elementString.contains(filterUnknownSource)) {
                    filteredStackTrace.add(elementString);
                }
            }
            exceptionDetails.put("stackTrace", filteredStackTrace);
        }

        List<Map<String, String>> causes = new ArrayList<>();
        Throwable cause = throwable.getCause();
        while (cause != null && cause != throwable) {
            Map<String, String> causeInfo = new HashMap<>();
            causeInfo.put("type", cause.getClass().getName());
            if (cause.getMessage() != null) {
                causeInfo.put("message", cause.getMessage());
            }
            causes.add(causeInfo);
            cause = cause.getCause();
        }

        if (!causes.isEmpty()) {
            exceptionDetails.put("causes", causes);
        }
        return exceptionDetails;
    }

    @Serdeable
    public static class ExtendedJsonError extends JsonError {
        private String timestamp;
        private Integer status;
        private String error;
        private List<Map<String, Object>> errors;
        private Map<String, Object> exception;

        public ExtendedJsonError(String message) {
            super(message);
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public List<Map<String, Object>> getErrors() {
            return errors;
        }

        public void setErrors(List<Map<String, Object>> errors) {
            this.errors = errors;
        }

        public Map<String, Object> getException() {
            return exception;
        }

        public void setException(Map<String, Object> exception) {
            this.exception = exception;
        }
    }
}