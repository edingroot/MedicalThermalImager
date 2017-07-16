package tw.cchi.flironedemo1.thermalproc;


import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.flir.flironesdk.RenderedImage;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class ThermalAnalyzer {
    private Context context;
    private RenderedImage renderedImage;

    public ThermalAnalyzer(Context context, RenderedImage renderedImage) {
        this.context = context;
        this.renderedImage = renderedImage;
    }

    public boolean exportToFiles(int[] thermalPixelValues) {
        if (thermalPixelValues == null)
            thermalPixelValues = renderedImage.thermalPixelValues();

        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File (sdCard.getAbsolutePath() + "/flirEx1/rawtemp/");
        dir.mkdirs();

        try {
            String filename = "rawtemp_" + System.currentTimeMillis() + ".dat";
//            PrintWriter writer = new PrintWriter(dir.getAbsolutePath() + "/" + filename, "UTF-8");
//            writer.write(this.serializePixels());
//            writer.close();
            FileUtils.writeByteArrayToFile(new File(dir.getAbsolutePath() + "/" + filename), serializePixels(thermalPixelValues));
        } catch (Exception e) {
            Log.e("exportToFiles", "Exception: " + e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * First 0~1 and 2~3 bytes are width and height, and the following each 2 byte represents the temperature in 100*K ( /100 -273.15C)
     * @return
     */
    private byte[] serializePixels(int[] thermalPixelValues) {
        int width = renderedImage.width(), height = renderedImage.height(), length = width * height;
        byte[] bytes = new byte[length * 2 + 4];

        bytes[0] = (byte) (width & 0xff);
        bytes[1] = (byte) ((width >> 8) & 0xff);
        bytes[2] = (byte) (height & 0xff);
        bytes[3] = (byte) ((height >> 8) & 0xff);

        for (int i = 0; i < length; i++) {
            bytes[4 + i * 2] = (byte) (thermalPixelValues[i] & 0xff);
            bytes[4 + i * 2 + 1] = (byte) ((thermalPixelValues[i] >> 8) & 0xff);
        }

        return bytes;
    }

}
