package tw.cchi.medthimager.utils;

import android.graphics.Bitmap;

import java.io.FileOutputStream;
import java.io.IOException;

public class ImageUtils {

    /**
     * Save bitmap to PNG file.
     */
    public static boolean saveBitmap(Bitmap bitmap, String filename) {
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(filename);

            // PNG is a lossless format, the compression factor (100) is ignored
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

}
