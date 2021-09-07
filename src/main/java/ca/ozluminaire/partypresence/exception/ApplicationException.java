package ca.ozluminaire.partypresence.exception;

import ca.ozluminaire.partypresence.StatusCode;
import lombok.Getter;

public class ApplicationException extends RuntimeException {

    @Getter
    private final StatusCode statusCode;

    public ApplicationException(StatusCode statusCode) {
        super();
        this.statusCode = statusCode;
    }
}
