package example.com.exceptions.response.error;


import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.http.MediaType;


@DefaultImplementation(DefaultHtmlProvider.class)
public interface HtmlProvider extends ErrorResponseBodyProvider<String> {

    @Override
    default String contentType() {
        return MediaType.TEXT_HTML;
    }
}
