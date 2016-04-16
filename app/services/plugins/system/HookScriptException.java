package services.plugins.system;

/**
 * The default exception for a script error
 * @author Pierre-Yves Cloux
 */
public class HookScriptException extends Exception {
    private static final long serialVersionUID = 1L;

    public HookScriptException() {
    }

    public HookScriptException(String message) {
        super(message);
    }

    public HookScriptException(Throwable cause) {
        super(cause);
    }

    public HookScriptException(String message, Throwable cause) {
        super(message, cause);
    }

    public HookScriptException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
