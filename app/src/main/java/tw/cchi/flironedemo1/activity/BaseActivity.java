package tw.cchi.flironedemo1.activity;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {
    private static Activity instance;

    public BaseActivity() {
        instance = this;
    }

    public static Activity getInstance() {
        return instance;
    }

}
