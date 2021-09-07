package ca.ozluminaire.partypresence.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Client {

    @EqualsAndHashCode.Include
    @ToString.Include
    private final Long id;

    // name, profile picture URL, possibly other info...
}
