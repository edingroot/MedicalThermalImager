package tw.cchi.medthimager.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

    public static void sendBroadcastToMedia(Context context, String filePath) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(filePath));
        intent.setData(uri);
        context.sendBroadcast(intent);
    }
}
