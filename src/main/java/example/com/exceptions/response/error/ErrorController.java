package example.com.exceptions.response.error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;


@Controller
public class ErrorController {
    @Get("/error")
    public HttpResponse<String> getError() {
        throw new RuntimeException("Something went wrong!");
    }

    //Add Post method  to test not allowed
    @Post("/create")
    public  void create(HttpRequest request, HttpResponse<?> response) {

    }
    //Bad request 400


}
