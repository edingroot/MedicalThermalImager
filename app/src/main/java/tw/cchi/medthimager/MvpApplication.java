package tw.cchi.medthimager;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import com.squareup.leakcanary.LeakCanary;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.data.DataManager;
import tw.cchi.medthimager.di.component.ApplicationComponent;
import tw.cchi.medthimager.di.component.DaggerApplicationComponent;
import tw.cchi.medthimager.di.module.ApplicationModule;
import tw.cchi.medthimager.helper.FlirDeviceDelegate;
import tw.cchi.medthimager.helper.FlirFrameProcessorDelegate;
import tw.cchi.medthimager.helper.session.Session;
import tw.cchi.medthimager.helper.session.SessionManager;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.util.NetworkUtils;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;

/**
 * This is able to be access globally in whole app
 */
public class MvpApplication extends Application {
    private ApplicationComponent mApplicationComponent;

    private SyncService syncService;
    private ServiceConnection syncServiceConnection;
    private volatile boolean syncServiceBounded = false;
    private Handler mainLooperHandler;

    @Inject public DataManager dataManager;
    @Inject public SessionManager sessionManager;

    // This is used to avoid memory leak due to (flir) Device.cachedDelegate is a static field
    @Inject public FlirDeviceDelegate flirDeviceDelegate;
    // This is used to avoid memory leak due to (flir) FrameProcessor.processors is a static field
    @Inject public FlirFrameProcessorDelegate flirFrameProcessorDelegate;

    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);

        this.mApplicationComponent = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this)).build();
        this.mApplicationComponent.inject(this);

        mainLooperHandler = new Handler(getMainLooper());

        SyncService.start(this);
    }

    public ApplicationComponent getComponent() {
        return mApplicationComponent;
    }

    // Needed to replace the component with a test specific one
    public void setComponent(ApplicationComponent applicationComponent) {
        mApplicationComponent = applicationComponent;
    }


    public Session getSession() {
        return sessionManager.getSession();
    }

    @BgThreadCapable
    public boolean checkNetworkAuthedAndAct() {
        if (!NetworkUtils.isNetworkConnected(this)) {
            mainLooperHandler.post(() ->
                    Toast.makeText(this, getString(R.string.no_network_access), Toast.LENGTH_SHORT).show());
            return false;
        } else if (!getSession().isActive()) {
            mainLooperHandler.post(() ->
                    Toast.makeText(this, getString(R.string.unauthenticated), Toast.LENGTH_SHORT).show());
            getSession().invalidate();
            return false;
        } else {
            return true;
        }
    }

    public Observable<SyncService> getSyncService() {
        return Observable.<SyncService>create(emitter -> {
            synchronized (MvpApplication.class) {
                if (syncServiceBounded) {
                    emitter.onNext(syncService);
                    emitter.onComplete();
                } else {
                    syncServiceConnection = new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName componentName, IBinder service) {
                            syncService = ((SyncService.SyncServiceBinder) service).getService();
                            emitter.onNext(syncService);
                            emitter.onComplete();
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName componentName) {
                            syncService = null;
                            syncServiceBounded = false;
                        }
                    };
                    syncServiceBounded = bindService(new Intent(this, SyncService.class),
                            syncServiceConnection, BIND_AUTO_CREATE);
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public void onTerminate() {
        if (syncServiceBounded)
            unbindService(syncServiceConnection);

        super.onTerminate();
    }
}


//    /**
//     * copyDatabaseToSDCard: Copy db file to sdcard for development purpose.
//     */
//    private void copyDatabaseToSDCard() {
//        try {
//            File sd = Environment.getExternalStorageDirectory();
//            File data = Environment.getDataDirectory();
//
//            if (sd.canWrite()) {
//                String currentDBPath = getDatabasePath(AppDatabase.DATABASE_NAME).getAbsolutePath();
//                String backupDBPath = AppUtils.getExportsDir() + "/" + AppDatabase.DATABASE_NAME;
//                File currentDB = new File(data, currentDBPath);
//                File backupDB = new File(sd, backupDBPath);
//
//                if (currentDB.exists()) {
//                    System.out.printf("Copying database from %s to %s\n", currentDBPath, backupDBPath);
//                    FileChannel src = new FileInputStream(currentDB).getChannel();
//                    FileChannel dst = new FileOutputStream(backupDB).getChannel();
//                    dst.transferFrom(src, 0, src.size());
//                    src.close();
//                    dst.close();
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
