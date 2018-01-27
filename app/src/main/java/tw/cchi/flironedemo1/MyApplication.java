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
    }

    public static MyApplication getInstance() {
        // no need to consider whether instance is null or not here
        return instance;
    }

}
