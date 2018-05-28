package tw.cchi.medthimager;

public final class Errors {
    private Errors() {}

    public static class NetworkLostError extends Error {
        public NetworkLostError() {
            super("Network not connected");
        }
    }

    public static class UnauthenticatedError extends Error {
        public UnauthenticatedError() {
            super("Not authenticated");
        }
    }

    public static class UnhandledStateError extends Error {
        public UnhandledStateError() {
            super("Unhandled state error");
        }
    }

}
