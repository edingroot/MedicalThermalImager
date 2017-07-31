package tw.cchi.flironedemo1;

import android.os.Environment;

import java.io.File;

public class AppUtils {

    public static String getExportsDir() {
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirEx1");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    /**
     * Effective range: min <= val < max
     * @param val
     * @param min
     * @param max
     * @return
     */
    public static int trimByRange(int val, int min, int max) {
        if (val < min)
            return min;
        else if (val > max)
            return max;
        else
            return val;
    }

}
