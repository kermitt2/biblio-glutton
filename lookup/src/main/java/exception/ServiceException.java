package exception;

/**
 * Exception thrown when an external service is not responding correctly
 */
public class ServiceException extends RuntimeException {
    private int code;

    public ServiceException() {
        this(500);
    }

    public ServiceException(int code) {
        this(code, "Error while processing the request", null);
    }

    public ServiceException(int code, String message) {
        this(code, message, null);
    }

    public ServiceException(int code, String message, Throwable throwable) {
        super(message, throwable);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
