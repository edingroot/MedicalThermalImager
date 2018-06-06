package tw.cchi.medthimager;

public final class Errors {
    private Errors() {}

    public static class AppError extends Error {
        public AppError(String message) {
            super(message);
        }
    }

    public static class NetworkLostError extends AppError {
        public NetworkLostError() {
            super("Network not connected");
        }
    }

    public static class UnauthenticatedError extends AppError {
        public UnauthenticatedError() {
            super("Not authenticated");
        }
    }

    public static class UnhandledStateError extends AppError {
        public UnhandledStateError() {
            super("Unhandled state error");
        }

        public UnhandledStateError(String message) {
            super(message);
        }
    }

    public static class TimeoutError extends AppError {
        public TimeoutError() {
            super("Timeout");
        }

        public TimeoutError(String message) {
            super(message);
        }
    }
}
