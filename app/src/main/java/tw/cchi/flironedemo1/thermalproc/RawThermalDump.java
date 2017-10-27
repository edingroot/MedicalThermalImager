package tw.cchi.flironedemo1.thermalproc;

import android.util.Log;

import com.flir.flironesdk.RenderedImage;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class RawThermalDump {
    public int width;
    public int height;
    private int[] thermalValues;
    private int maxValue = -1;
    private int minValue = -1;
    private String filepath;
    private String title;

    public RawThermalDump(int width, int height, int[] thermalValues) {
        this.width = width;
        this.height = height;
        this.thermalValues = thermalValues;
    }

    public RawThermalDump(RenderedImage renderedImage) {
        this.width = renderedImage.width();
        this.height = renderedImage.height();
        this.thermalValues = renderedImage.thermalPixelValues();
    }

    public static RawThermalDump readFromFile(String filepath) {
        byte[] bytes = readAllBytesFromFile(filepath);
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        int width = bytes2UnsignedInt(bytes[0], bytes[1]);
        int height = bytes2UnsignedInt(bytes[2], bytes[3]);

        if (bytes.length != 4 + width * height * 2)
            return null;

        int[] thermalValues = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int byteIndex = 4 + i * 2;
            thermalValues[i] = bytes2UnsignedInt(bytes[byteIndex], bytes[byteIndex + 1]);
        }

        RawThermalDump rawThermalDump = new RawThermalDump(width, height, thermalValues);
        rawThermalDump.setFilepath(filepath);
        return rawThermalDump;
    }

    /**
     * File format: first 0~1 and 2~3 bytes are width and height, and the following each 2 byte represents the temperature in 100*K ( /100 -273.15C)
     * @param filepath
     * @return
     */
    public boolean saveToFile(String filepath) {
        int length = width * height;
        byte[] bytes = new byte[length * 2 + 4];

        bytes[0] = (byte) (width & 0xff);
        bytes[1] = (byte) ((width >> 8) & 0xff);
        bytes[2] = (byte) (height & 0xff);
        bytes[3] = (byte) ((height >> 8) & 0xff);

        for (int i = 0; i < length; i++) {
            bytes[4 + i * 2] = (byte) (thermalValues[i] & 0xff);
            bytes[4 + i * 2 + 1] = (byte) ((thermalValues[i] >> 8) & 0xff);
        }

        try {
            FileUtils.writeByteArrayToFile(new File(filepath), bytes);
        } catch (Exception e) {
            Log.e("saveRawThermalDump", "Exception: " + e.toString());
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public int[] getThermalValues() {
        return thermalValues;
    }

    public void setThermalValues(int[] thermalValues) {
        this.thermalValues = thermalValues;
        maxValue = minValue = -1;
    }

    public String getFilepath() {
        return filepath;
    }

    private void setFilepath(String filepath) {
        this.filepath = filepath;

        // Generate title; filenameEx: 2017-10-08-16-10-08_rawThermal.dat
        String filename = new File(filepath).getName();
        int startIndex = "2017-".length();
        title = String.format("%s/%s %s:%s:%s",
                filename.substring(startIndex, startIndex + 2),
                filename.substring(startIndex + 3, startIndex + 5),
                filename.substring(startIndex + 6, startIndex + 8),
                filename.substring(startIndex + 9, startIndex + 11),
                filename.substring(startIndex + 12, startIndex + 14)
        );
    }

    public String getTitle() {
        return title;
    }

    public float getMaxTemperature() {
        if (maxValue == -1) {
            for (int val : thermalValues) {
                if (val > maxValue)
                    maxValue = val;
            }
        }
        return (float) (maxValue - 27315) / 100;
    }

    public float getMinTemperature() {
        if (minValue == -1) {
            minValue = Integer.MAX_VALUE;
            for (int val : thermalValues) {
                if (val < minValue)
                    minValue = val;
            }
        }
        return (float) (minValue - 27315) / 100;
    }

    public float getTemperatureAt(int x, int y) {
        int index = y * width + x;

        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        return (float) (thermalValues[index] - 27315) / 100;
    }

    public float getTemperatureKAt(int x, int y) {
        int index = y * width + x;

        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        return thermalValues[index] / 100.0f;
    }

    /**
     * Get the average of the 9 pixels around the point.
     *  - Note: this code is not optimized.
     * @param x
     * @param y
     * @return The average temperature in degree Celsius
     */
    public double getTemperature9Average(int x, int y) {
        int centerPixelIndex = width * y + x;

        int[] centerPixelIndexes = new int[]{
                centerPixelIndex, centerPixelIndex - 1, centerPixelIndex + 1,
                centerPixelIndex - width,
                centerPixelIndex - width - 1,
                centerPixelIndex - width + 1,
                centerPixelIndex + width,
                centerPixelIndex + width - 1,
                centerPixelIndex + width + 1
        };

        double averageTemp = 0;
        for (int i = 0; i < centerPixelIndexes.length; i++) {
            // Remember: all primitives are signed, we want the unsigned value,
            // we've used renderedImage.thermalPixelValues() to get unsigned values
            int pixelValue = thermalValues[centerPixelIndexes[i]];
            averageTemp += (((double) pixelValue) - averageTemp) / ((double) i + 1);
        }
        return (averageTemp / 100) - 273.15;

    }

    private static int bytes2UnsignedInt(byte msb, byte lsb) {
        // Make bytes unsigned
        int iMsb = 0xff & (int) msb;
        int iLsb = 0xff & (int) lsb;
        return iLsb << 8 | iMsb;
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
