package tw.cchi.medthimager.service.sync;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.di.component.DaggerServiceComponent;
import tw.cchi.medthimager.di.component.ServiceComponent;
import tw.cchi.medthimager.model.RecurrentRunningStatus;
import tw.cchi.medthimager.service.sync.task.SyncPatientsTask;
import tw.cchi.medthimager.service.sync.task.SyncTask;
import tw.cchi.medthimager.service.sync.task.UpSyncPatientTask;
import tw.cchi.medthimager.util.NetworkUtils;

public class SyncService extends Service {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private IBinder mBinder;
    private NetworkStateBroadcastReceiver networkStateReceiver;
    private CompositeDisposable taskWorkerSubs = new CompositeDisposable();
    private ConcurrentHashMap<Class<? extends SyncTask>, RecurrentRunningStatus> taskRunningStatus = new ConcurrentHashMap<>();

    // PublishSubject for worker of each sync task
    private PublishSubject<UpSyncPatientTask> upSyncPatientsTaskPub = PublishSubject.create();
    private PublishSubject<SyncPatientsTask> syncPatientsTaskPub = PublishSubject.create();

    public static void start(Context context) {
        context.startService(new Intent(context, SyncService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, SyncService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ServiceComponent component = DaggerServiceComponent.builder()
                .applicationComponent(((MvpApplication) getApplication()).getComponent())
                .build();
        component.inject(this);

        mBinder = new SyncServiceBinder();
        networkStateReceiver = new NetworkStateBroadcastReceiver();
        registerReceiver(networkStateReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "SyncService started");

        startTaskWorkers();
        return START_STICKY;
    }

    private void startTaskWorkers() {
        taskRunningStatus.put(UpSyncPatientTask.class, new RecurrentRunningStatus(false));
        taskRunningStatus.put(SyncPatientsTask.class, new RecurrentRunningStatus(false));

        taskWorkerSubs.add(upSyncPatientsTaskPub
                .subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
                .subscribe(task -> {
                    taskRunningStatus.get(task.getClass()).setRunning(true);
                    task.run(this);
                    // if (task.getError() != null) {}
                    taskRunningStatus.get(task.getClass()).setRunning(false);
                }));

        taskWorkerSubs.add(syncPatientsTaskPub
                .subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
                .subscribe(task -> {
                    taskRunningStatus.get(task.getClass()).setRunning(true);
                    task.run(this);
                    // if (task.getError() != null) {}
                    taskRunningStatus.get(task.getClass()).setRunning(false);
                }));
    }

    /**
     * Errors such like network or authentication error will be caught and ignored.
     */
    public void scheduleNewTask(SyncTask syncTask) {
        if (syncTask instanceof UpSyncPatientTask)
            upSyncPatientsTaskPub.onNext((UpSyncPatientTask) syncTask);
        else if (syncTask instanceof SyncPatientsTask)
            syncPatientsTaskPub.onNext((SyncPatientsTask) syncTask);
    }

    public boolean isTaskRunning(Class<? extends SyncTask> syncTaskClass) {
        RecurrentRunningStatus runningStatus = taskRunningStatus.get(syncTaskClass);
        return runningStatus != null && runningStatus.isRunning();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "SyncService stopped");

        unregisterReceiver(networkStateReceiver);
        taskWorkerSubs.dispose();
        super.onDestroy();
    }

    private class NetworkStateBroadcastReceiver extends BroadcastReceiver {
        public NetworkStateBroadcastReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (NetworkUtils.isNetworkConnected(context)) {
                // TODO
            }
        }
    }

    public class SyncServiceBinder extends Binder {
        public SyncService getService() {
            return SyncService.this;
        }
    }
}
