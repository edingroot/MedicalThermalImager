package tw.cchi.medthimager;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

import tw.cchi.medthimager.di.component.ApplicationComponent;
import tw.cchi.medthimager.di.component.DaggerApplicationComponent;
import tw.cchi.medthimager.di.module.ApplicationModule;

/**
 * This is able to be access globally in whole app
 */
public class MvpApplication extends Application {
    private ApplicationComponent mApplicationComponent;

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

        // copyDatabaseToSDCard();
    }

    public ApplicationComponent getComponent() {
        return mApplicationComponent;
    }

    // Needed to replace the component with a test specific one
    public void setComponent(ApplicationComponent applicationComponent) {
        mApplicationComponent = applicationComponent;
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
