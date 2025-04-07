package example.com;


import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable
public record ContactPreview(
        @NonNull Long id,
        @Nullable String firstName,
        @Nullable String lastName
) {
}
