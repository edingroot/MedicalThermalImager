package tw.cchi.medthimager.service.sync.task;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.widget.Toast;

import java.util.concurrent.Callable;

import io.reactivex.disposables.Disposable;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.data.DataManager;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.util.NetworkUtils;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;

public abstract class SyncTask implements Callable<Void>, Disposable {
    MvpApplication application;
    SyncBroadcastSender broadcastSender;
    DataManager dataManager;
    ApiHelper apiHelper;
    long timeout = 0;
    volatile boolean disposed = false;

    private SyncService syncService;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean finished = false;

    SyncTask() {
    }

    /**
     * @param syncService SyncService instance that launches this task
     */
    public void setSyncService(@NonNull SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * {@link #setSyncService(SyncService)} should have been called.
     */
    @Override
    public Void call() throws Exception {
        if (syncService == null) {
            throw new RuntimeException("SyncService instance not set");
        }

        this.application = (MvpApplication) syncService.getApplication();
        this.broadcastSender = new SyncBroadcastSender(syncService);
        this.dataManager = application.dataManager;
        this.apiHelper = new ApiHelper(application);

        inject();
        doWork();
        finish();
        return null;
    }

    /**
     * Can be override for Dagger injection for child class.
     */
    void inject() {}

    /**
     * This method should be designed as a thread-blocking worker.
     */
    abstract void doWork();

    boolean checkNetworkAndAuthed() {
        if (!NetworkUtils.isNetworkConnected(application)) {
            throw new Errors.NetworkLostError();
        } else if (!application.getSession().isActive()) {
            throw new Errors.UnauthenticatedError();
        }
        return true;
    }

    String getString(int stringRes) {
        return application.getString(stringRes);
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

    private void finish() {
        finished = true;
        dispose();
    }

    /**
     * @return 0 for unlimited
     */
    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
