package ca.ozluminaire.partypresence.messaging;

import ca.ozluminaire.partypresence.model.Session;
import lombok.Data;

import java.util.Objects;

@Data
public class SessionReference {

    private Session session;

    @Override
    public String toString() {
        return Objects.toString(session);
    }

}
