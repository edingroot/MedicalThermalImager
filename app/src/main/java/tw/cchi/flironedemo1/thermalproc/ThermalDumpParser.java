package tw.cchi.flironedemo1.thermalproc;


import android.util.Log;

import com.flir.flironesdk.RenderedImage;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ThermalDumpParser {

    public static boolean saveRawThermalDump(RenderedImage renderedImage, String filename) {
        try {
            FileUtils.writeByteArrayToFile(new File(filename), serializePixels(renderedImage));
        } catch (Exception e) {
            Log.e("saveRawThermalDump", "Exception: " + e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static RawThermalDump readRawThermalDump(String filepath) {
        byte[] bytes = readAllBytesFromFile(filepath);
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        int width = twoBytesValue(bytes[0], bytes[1]);
        int height = twoBytesValue(bytes[2], bytes[3]);

        if (bytes.length != 4 + width * height * 2)
            return null;

        int[] thermalValues = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int byteIndex = 4 + i * 2;
            thermalValues[i] = twoBytesValue(bytes[byteIndex], bytes[byteIndex + 1]);
        }

        return new RawThermalDump(width, height, thermalValues);
    }

    /**
     * First 0~1 and 2~3 bytes are width and height, and the following each 2 byte represents the temperature in 100*K ( /100 -273.15C)
     * @return
     */
    private static byte[] serializePixels(RenderedImage renderedImage) {
        int width = renderedImage.width(), height = renderedImage.height(), length = width * height;
        byte[] bytes = new byte[length * 2 + 4];

        bytes[0] = (byte) (width & 0xff);
        bytes[1] = (byte) ((width >> 8) & 0xff);
        bytes[2] = (byte) (height & 0xff);
        bytes[3] = (byte) ((height >> 8) & 0xff);

        int[] thermalPixelValues = renderedImage.thermalPixelValues();
        for (int i = 0; i < length; i++) {
            bytes[4 + i * 2] = (byte) (thermalPixelValues[i] & 0xff);
            bytes[4 + i * 2 + 1] = (byte) ((thermalPixelValues[i] >> 8) & 0xff);
        }

        return bytes;
    }

    private static int twoBytesValue(byte msb, byte lsb) {
        // Make bytes unsigned
        int imsb = 0xff & (int) msb;
        int ilsb = 0xff & (int) lsb;
        return ilsb << 8 | imsb;
    }

    private static byte[] readAllBytesFromFile(String filepath) {
        File file = new File(filepath);
        int size = (int) file.length();
        byte[] bytes = new byte[size];

        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
