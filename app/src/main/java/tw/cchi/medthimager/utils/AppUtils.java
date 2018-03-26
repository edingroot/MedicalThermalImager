package tw.cchi.medthimager.utils;

import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppUtils {

    private AppUtils() {
        // This utility class is not publicly instantiable
    }

    public static String getExportsDir() {
        // String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirEx1");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    public static String generateCaptureFilename() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMdd-HHmmss-SSS", Locale.getDefault());
        String dateString = sdf.format(new Date());
        return dateString.substring(0, dateString.length() - 2);
    }

}
