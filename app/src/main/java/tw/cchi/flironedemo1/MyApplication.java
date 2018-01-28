package tw.cchi.flironedemo1;

import android.app.Application;

import tw.cchi.flironedemo1.db.AppDatabase;

/**
 * This is able to be access globally in whole app
 */
public class MyApplication extends Application {
    private static MyApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        System.out.println("Database path: " + getDatabasePath(AppDatabase.DATABASE_NAME).getAbsolutePath());
    }

    public static MyApplication getInstance() {
        // no need to consider whether instance is null or not here
        return instance;
    }

}
