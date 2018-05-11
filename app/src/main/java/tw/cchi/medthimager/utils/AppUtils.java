package tw.cchi.medthimager.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.Nullable;

import java.io.File;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import tw.cchi.medthimager.R;

public final class AppUtils {

    private AppUtils() {
        // This utility class is not publicly instantiable
    }

    @SuppressLint("HardwareIds")
    public static String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Ref: https://stackoverflow.com/a/39144299
     * Works in Android 6.0 and 7.0
     */
    @Nullable
    public static String getWlanMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0"))
                    continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null)
                    return null;

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes)
                    res1.append(String.format("%02X:", b));

                if (res1.length() > 0)
                    res1.deleteCharAt(res1.length() - 1);

                return res1.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return null;
    }

    public static String getExportsDir() {
        // String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirEx1");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    /**
     * Google Play's pre-launch report is also running in firebase test lab.
     */
    public static boolean checkIsRunningInFirebaseTestLab(Context context) {
        String firebaseTestLab = Settings.System.getString(context.getContentResolver(), "firebase.test.lab");
        return "true".equals(firebaseTestLab);
    }

    public static void sendBroadcastToMedia(Context context, String filePath) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(filePath));
        intent.setData(uri);
        context.sendBroadcast(intent);
    }

    public static ProgressDialog showLoadingDialog(final Activity activity) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.show();
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        progressDialog.setContentView(R.layout.progress_dialog);
        progressDialog.setIndeterminate(true);

        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnCancelListener(dialog -> activity.finish());

        return progressDialog;
    }
}
