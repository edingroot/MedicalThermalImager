package tw.cchi.flironedemo1.activity;

import android.app.Activity;

public class BaseActivity extends Activity {
    private static Activity instance;

    public BaseActivity() {
        instance = this;
    }

    public static Activity getInstance() {
        return instance;
    }

}
