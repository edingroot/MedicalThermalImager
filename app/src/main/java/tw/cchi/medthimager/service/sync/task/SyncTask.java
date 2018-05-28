package tw.cchi.medthimager.service.sync.task;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.widget.Toast;

import io.reactivex.disposables.Disposable;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;

abstract class SyncTask implements Runnable, Disposable {
    protected MvpApplication application;
    protected volatile boolean disposed = false;

    private Handler mainHandler;
    private volatile boolean finished = false;
    private Error error = null;

    SyncTask(MvpApplication application) {
        this.application = application;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    protected void finish() {
        finish(null);
    }

    protected void finish(@Nullable Error error) {
        this.error = error;
        finished = true;
        dispose();
    }

    @BgThreadCapable
    protected void showToast(String message) {
        mainHandler.post(() ->
                Toast.makeText(application, message, Toast.LENGTH_SHORT).show());
    }


    Error getError() {
        return error;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    static class NetworkLostError extends Error {
        NetworkLostError() {
            super("Network not connected");
        }
    }

    static class UnauthenticatedError extends Error {
        UnauthenticatedError() {
            super("Not authenticated");
        }
    }
}
