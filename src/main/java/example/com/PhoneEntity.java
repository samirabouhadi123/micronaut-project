package example.com;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("phone")
public record PhoneEntity(
        @Id
        @GeneratedValue
        @Nullable
        Long id,

        @NonNull
        String phone,

        @Nullable
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        ContactEntity contact
) {
}
