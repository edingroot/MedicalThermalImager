package tw.cchi.flironedemo1;

import android.os.Environment;

import java.io.File;

public class AppUtils {

    public static String getExternalStorageDir() {
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirEx1/rawtemp");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

}
