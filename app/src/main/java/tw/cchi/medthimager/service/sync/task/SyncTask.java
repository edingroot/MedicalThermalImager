package tw.cchi.medthimager.service.sync.task;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.widget.Toast;

import io.reactivex.disposables.Disposable;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.data.DataManager;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.util.NetworkUtils;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;

abstract class SyncTask implements Runnable, Disposable {
    MvpApplication application;
    DataManager dataManager;
    SyncBroadcastSender broadcastSender;
    volatile boolean disposed = false;

    private Handler mainHandler;
    private volatile boolean finished = false;
    private Error error = null;

    SyncTask(SyncService syncService) {
        this.application = (MvpApplication) syncService.getApplication();
        this.dataManager = application.dataManager;
        this.broadcastSender = new SyncBroadcastSender(syncService);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    void finish() {
        finish(null);
    }

    void finish(@Nullable Error error) {
        this.error = error;
        finished = true;
        dispose();
    }


    boolean checkNetworkAndAuthed() {
        if (!NetworkUtils.isNetworkConnected(application)) {
            finish(new Errors.NetworkLostError());
        } else if (!application.getSession().isActive()) {
            finish(new Errors.UnauthenticatedError());
        }
        return true;
    }

    @BgThreadCapable
    void showToast(int stringRes) {
        showToast(application.getString(stringRes));
    }

    @BgThreadCapable
    void showToast(String message) {
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
}
