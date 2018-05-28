package tw.cchi.medthimager;

import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;

import com.squareup.leakcanary.LeakCanary;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.di.component.ApplicationComponent;
import tw.cchi.medthimager.di.component.DaggerApplicationComponent;
import tw.cchi.medthimager.di.module.ApplicationModule;
import tw.cchi.medthimager.helper.FlirDeviceDelegate;
import tw.cchi.medthimager.helper.FlirFrameProcessorDelegate;
import tw.cchi.medthimager.helper.api.ApiClient;
import tw.cchi.medthimager.helper.api.ApiServiceGenerator;
import tw.cchi.medthimager.helper.session.Session;
import tw.cchi.medthimager.helper.session.SessionManager;
import tw.cchi.medthimager.helper.pref.PreferencesHelper;
import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.service.sync.SyncService;

/**
 * This is able to be access globally in whole app
 */
public class MvpApplication extends Application {
    private ApplicationComponent mApplicationComponent;

    protected SyncService syncService;
    protected ServiceConnection syncServiceConnection;
    protected volatile boolean syncServiceBounded = false;

    @Inject public SessionManager sessionManager;
    @Inject public PreferencesHelper preferencesHelper;
    // null if access tokens not exists in shared preferences
    @Nullable public ApiClient authedApiClient;

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

        createAuthedAPIClient(getSession().getAccessTokens());
        SyncService.start(this);

        // copyDatabaseToSDCard();
    }

    public ApplicationComponent getComponent() {
        return mApplicationComponent;
    }

    // Needed to replace the component with a test specific one
    public void setComponent(ApplicationComponent applicationComponent) {
        mApplicationComponent = applicationComponent;
    }

    public boolean createAuthedAPIClient(AccessTokens accessTokens) {
        if (accessTokens != null) {
            authedApiClient = ApiServiceGenerator.createService(ApiClient.class, accessTokens, this);
            return true;
        } else {
            return false;
        }
    }

    public Session getSession() {
        return sessionManager.getSession();
    }

    public synchronized void getSyncService(final OnSyncServiceBoundedListener listener) {
        Observable.create(emitter -> {
            if (syncServiceBounded) {
                listener.onServiceBounded(syncService);
            } else {
                syncServiceConnection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName componentName, IBinder service) {
                        syncService = ((SyncService.SyncServiceBinder) service).getService();
                        listener.onServiceBounded(syncService);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName componentName) {
                        syncService = null;
                        syncServiceBounded = false;
                    }
                };
                syncServiceBounded = bindService(
                        new Intent(this, SyncService.class), syncServiceConnection, BIND_AUTO_CREATE);
            }
        }).subscribeOn(Schedulers.io()).subscribe();
    }

    @Override
    public void onTerminate() {
        if (syncServiceBounded)
            unbindService(syncServiceConnection);

        super.onTerminate();
    }

    public interface OnSyncServiceBoundedListener {
        void onServiceBounded(SyncService service);
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
}
