package tw.cchi.medthimager.service.sync.task;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.widget.Toast;

import io.reactivex.disposables.Disposable;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.data.DataManager;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.util.NetworkUtils;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;

public abstract class SyncTask implements Disposable {
    MvpApplication application;
    SyncBroadcastSender broadcastSender;
    DataManager dataManager;
    ApiHelper apiHelper;
    volatile boolean disposed = false;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean finished = false;
    private Error error = null;

    SyncTask() {
    }

    public void run(SyncService syncService) {
        this.application = (MvpApplication) syncService.getApplication();
        this.broadcastSender = new SyncBroadcastSender(syncService);
        this.dataManager = application.dataManager;
        this.apiHelper = new ApiHelper(application);
    }

    boolean checkNetworkAndAuthed() {
        if (!NetworkUtils.isNetworkConnected(application)) {
            finish(new Errors.NetworkLostError());
            return false;
        } else if (!application.getSession().isActive()) {
            finish(new Errors.UnauthenticatedError());
            return false;
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

    void finish() {
        finish(null);
    }

    void finish(@Nullable Error error) {
        this.error = error;
        finished = true;
        dispose();
    }

    public Error getError() {
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
