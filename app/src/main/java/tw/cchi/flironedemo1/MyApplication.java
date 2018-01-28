package tw.cchi.flironedemo1;

import android.app.Application;

/**
 * This is able to be access globally in whole app
 */
public class MyApplication extends Application {
    private static MyApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // copyDatabaseToSDCard();
    }

    public static MyApplication getInstance() {
        // no need to consider whether instance is null or not here
        return instance;
    }

    /**
     * copyDatabaseToSDCard: Copy db file to sdcard for development purpose.
     */
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
