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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.di.component.DaggerServiceComponent;
import tw.cchi.medthimager.di.component.ServiceComponent;
import tw.cchi.medthimager.model.RecurrentRunningStatus;
import tw.cchi.medthimager.service.sync.task.SyncPatientsTask;
import tw.cchi.medthimager.service.sync.task.SyncSinglePatientTask;
import tw.cchi.medthimager.service.sync.task.SyncSingleThImageTask;
import tw.cchi.medthimager.service.sync.task.SyncTask;
import tw.cchi.medthimager.service.sync.task.UpSyncThImagesTask;
import tw.cchi.medthimager.util.CommonUtils;
import tw.cchi.medthimager.util.NetworkUtils;

public class SyncService extends Service {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private IBinder mBinder;
    private NetworkStateBroadcastReceiver networkStateReceiver;
    private final CompositeDisposable taskWorkerSubs = new CompositeDisposable();

    private static final Set<Class<? extends SyncTask>> syncTaskClasses = new HashSet<>();
    private final ConcurrentHashMap<Class<? extends SyncTask>, PublishSubject<SyncTask>> taskPublishSubjects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends SyncTask>, ExecutorService> taskExecutors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends SyncTask>, RecurrentRunningStatus> taskRunningStatus = new ConcurrentHashMap<>();

    static {
        // All sync tasks should be added here
        syncTaskClasses.add(SyncSinglePatientTask.class);
        syncTaskClasses.add(SyncPatientsTask.class);
        syncTaskClasses.add(SyncSingleThImageTask.class);
        syncTaskClasses.add(UpSyncThImagesTask.class);
    }

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
        for (Class<? extends SyncTask> syncTaskClass : syncTaskClasses) {
            taskRunningStatus.put(syncTaskClass, new RecurrentRunningStatus());
            taskPublishSubjects.put(syncTaskClass, PublishSubject.create());
            taskExecutors.put(syncTaskClass, Executors.newSingleThreadExecutor());

            // Only one task with same class is allowed to run at the same time
            Observable<SyncTask> taskWorkerSub = taskPublishSubjects.get(syncTaskClass);
            taskWorkerSubs.add(taskWorkerSub
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                    syncTask -> {
                        taskRunningStatus.get(syncTask.getClass()).setRunning(true);

                        syncTask.setSyncService(this);
                        Future<Void> future = taskExecutors.get(syncTask.getClass()).submit(syncTask);
                        try {
                            Log.i(TAG, "timeout=" + syncTask.getTimeout());
                            if (syncTask.getTimeout() != 0)
                                future.get(syncTask.getTimeout(), TimeUnit.MILLISECONDS);
                            else
                                future.get();
                        } catch (Exception e) {
                            if (e instanceof TimeoutException) {
                                Log.w(TAG, "Timeout, canceling task: " + syncTask.getClass().getSimpleName());
                                syncTask.dispose();

                                // Interrupt and stop the underlying task after 100ms
                                CommonUtils.sleep(500);
                                future.cancel(true);
                            } else {
                                // The may be the exception thrown by the underlying task
                                e.printStackTrace();
                            }
                        }

                        taskRunningStatus.get(syncTask.getClass()).setRunning(false);
                    },
                    Throwable::printStackTrace,
                    () -> {}
                ));
        }
    }

    /**
     * Errors such like network or authentication error will be caught and ignored.
     */
    public void scheduleNewTask(SyncTask syncTask) {
        PublishSubject<SyncTask> publishSubject = taskPublishSubjects.get(syncTask.getClass());
        if (publishSubject == null)
            throw new RuntimeException("Target sync task not found / not added to this.syncTaskClasses");
        else
            publishSubject.onNext(syncTask);
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
                //
            }
        }
    }

    public class SyncServiceBinder extends Binder {
        public SyncService getService() {
            return SyncService.this;
        }
    }
}
