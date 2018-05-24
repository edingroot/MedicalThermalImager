package tw.cchi.medthimager;

import android.app.Application;
import android.support.annotation.Nullable;

import com.squareup.leakcanary.LeakCanary;

import javax.inject.Inject;

import tw.cchi.medthimager.di.component.ApplicationComponent;
import tw.cchi.medthimager.di.component.DaggerApplicationComponent;
import tw.cchi.medthimager.di.module.ApplicationModule;
import tw.cchi.medthimager.helper.FlirDeviceDelegate;
import tw.cchi.medthimager.helper.api.ApiClient;
import tw.cchi.medthimager.helper.api.ApiServiceGenerator;
import tw.cchi.medthimager.helper.pref.PreferencesHelper;
import tw.cchi.medthimager.model.AccessTokens;

/**
 * This is able to be access globally in whole app
 */
public class MvpApplication extends Application {
    private ApplicationComponent mApplicationComponent;

    // Null if access tokens not exists in shared preferences
    @Nullable public ApiClient authedApiClient;
    // This is used to avoid memory leak due to (flir) Deivce.cachedDelegate is a static field
    @Inject public FlirDeviceDelegate flirDeviceDelegate;
    @Inject public PreferencesHelper preferencesHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);

        mApplicationComponent = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this)).build();
        mApplicationComponent.inject(this);

        createAuthedAPIClient();

        // copyDatabaseToSDCard();
    }

    public ApplicationComponent getComponent() {
        return mApplicationComponent;
    }

    // Needed to replace the component with a test specific one
    public void setComponent(ApplicationComponent applicationComponent) {
        mApplicationComponent = applicationComponent;
    }

    public boolean createAuthedAPIClient() {
        AccessTokens accessTokens = preferencesHelper.getAccessTokens();
        if (accessTokens != null) {
            authedApiClient = ApiServiceGenerator.createService(ApiClient.class, accessTokens, getApplicationContext());
            return true;
        } else {
            return false;
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

}
