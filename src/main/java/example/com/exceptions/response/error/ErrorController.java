package example.com.exceptions.response.error;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;

import jakarta.inject.Inject;

@Controller
public class ErrorController {



    @Inject
    public ErrorController() {

    }


    @Get("/error")
    public HttpResponse<String> getError() {
        throw new RuntimeException("Something went wrong!");
    }




}
