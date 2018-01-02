package tw.cchi.flironedemo1.thermalproc;

import com.flir.flironesdk.RenderedImage;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import tw.cchi.flironedemo1.Config;

/**
 * File format V1:
 *  Bytes / Data (MSB, LSB), integer
 *  1 ~ 2		width (number of columns)
 *  3 ~ 4		height (number of rows)
 *  5 ~ N		each thermal pixel is stored by 2 bytes (N=width*height*2 + 4)
 *  (N+1)		EOF
 *
 * File format V2:
 *  1 ~ 2			width (number of columns)
 *  3 ~ 4			height (number of rows)
 *  5 ~ M			each thermal pixel is stored by 2 bytes (M=2*width*height + 4)
 *  (M+1) ~ (M+2)	visible image alignment X offset
 *  (M+3) ~ (M+4)	visible image alignment Y offset
 *  (M+5)			EOF
 */
public class RawThermalDump {
    private int formatVersion = 1;
    private int width;
    private int height;
    private int visibleOffsetX = 0;
    private int visibleOffsetY = 0;
    private int[] thermalValues; // 0.01K = 1, (C = val/100 - 273.15)
    private int maxValue = -1;
    private int minValue = -1;
    private String filepath;
    private String title;
    private VisibleImageMask visibleImageMask; // [Android Only]

    // [Android Only]
    public RawThermalDump(RenderedImage renderedImage) {
        this.width = renderedImage.width();
        this.height = renderedImage.height();
        this.thermalValues = renderedImage.thermalPixelValues();
    }

    public RawThermalDump(int width, int height, int[] thermalValues) {
        this.width = width;
        this.height = height;
        this.thermalValues = thermalValues;
    }

    public RawThermalDump(int width, int height, int[] thermalValues, int visibleOffsetX, int visualDeltaY) {
        this(width, height, thermalValues);
        this.visibleOffsetX = visibleOffsetX;
        this.visibleOffsetY = visualDeltaY;
        this.formatVersion = 2;
    }

    public static RawThermalDump readFromDumpFile(String filepath) {
        int formatVersion;
        byte[] bytes = readAllBytesFromFile(filepath);
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        int width = bytes2UnsignedInt(bytes[0], bytes[1]);
        int height = bytes2UnsignedInt(bytes[2], bytes[3]);

        if (bytes.length == 4 + width * height * 2)
            formatVersion = 1;
        else if (bytes.length == 8 + width * height * 2)
            formatVersion = 2;
        else
            return null;

        int[] thermalValues = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int byteIndex = 4 + i * 2;
            thermalValues[i] = bytes2UnsignedInt(bytes[byteIndex], bytes[byteIndex + 1]);
        }

        RawThermalDump rawThermalDump;
        if (formatVersion == 1) {
            rawThermalDump = new RawThermalDump(width, height, thermalValues);
        } else {
            int index = 4 + 2 * width * height;
            int visualDeltaX = bytes2UnsignedInt(bytes[index], bytes[index + 1]);
            int visualDeltaY = bytes2UnsignedInt(bytes[index + 2], bytes[index + 3]);
            rawThermalDump = new RawThermalDump(width, height, thermalValues, visualDeltaX, visualDeltaY);
        }
        rawThermalDump.setFilepath(filepath);

        return rawThermalDump;
    }

    public synchronized boolean saveToFile(String filepath) {
        int length = formatVersion == 1 ? 4 + 2 * width * height : 8 + 2 * width * height;
        byte[] bytes = new byte[length];

        bytes[0] = (byte) (width & 0xff);
        bytes[1] = (byte) ((width >> 8) & 0xff);
        bytes[2] = (byte) (height & 0xff);
        bytes[3] = (byte) ((height >> 8) & 0xff);

        for (int i = 0; i < width * height; i++) {
            bytes[4 + i * 2] = (byte) (thermalValues[i] & 0xff);
            bytes[4 + i * 2 + 1] = (byte) ((thermalValues[i] >> 8) & 0xff);
        }

        if (formatVersion == 2) {
            int index = 4 + 2 * width * height;
            bytes[index] = (byte) (visibleOffsetX & 0xff);
            bytes[index + 1] = (byte) ((visibleOffsetX >> 8) & 0xff);
            bytes[index + 2] = (byte) (visibleOffsetY & 0xff);
            bytes[index + 3] = (byte) ((visibleOffsetY >> 8) & 0xff);
        }

        try {
            FileUtils.writeByteArrayToFile(new File(filepath), bytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getVisibleOffsetX() {
        return visibleOffsetX;
    }

    public void setVisibleOffsetX(int visibleOffsetX) {
        this.formatVersion = 2;
        this.visibleOffsetX = visibleOffsetX;
    }

    public int getVisibleOffsetY() {
        return visibleOffsetY;
    }

    public void setVisibleOffsetY(int visibleOffsetY) {
        this.formatVersion = 2;
        this.visibleOffsetY = visibleOffsetY;
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

        // Generate title; filenameEx: 1008-161008_2_raw.dat
        String filename = new File(filepath).getName();
        String fileType = filename.substring(filename.lastIndexOf("_") + 1, filename.lastIndexOf("."));
        // Ignore showing milliseconds on title
        title = String.format("%s/%s %s:%s:%s-%s",
                filename.substring(0, 2),
                filename.substring(2, 4),
                filename.substring(5, 7),
                filename.substring(7, 9),
                filename.substring(9, 11),
                filename.substring(12, 13)
        );
        if (fileType.equals("raw-reged"))
            title += "R";
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
                if (val < minValue && val != 0)
                    minValue = val;
            }
        }
        return (float) (minValue - 27315) / 100;
    }

    /**
     *
     * @param x
     * @param y
     * @return temperature in degree Celsius or -1 for empty pixel
     */
    public float getTemperatureAt(int x, int y) {
        int index = y * width + x;
        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        int thermalValue = thermalValues[index];

        return thermalValue == 0 ? -1 : (float) (thermalValue - 27315) / 100;
    }

    /**
     *
     * @param x
     * @param y
     * @return temperature in Kelvin scale or -1 for empty pixel
     */
    public float getTemperatureKAt(int x, int y) {
        int index = y * width + x;
        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        int thermalValue = thermalValues[index];

        return thermalValue == 0 ? -1 : thermalValue / 100.0f;
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


    // ---------------------- [Android Only] ---------------------- //

    public boolean attachVisibleImageMask() {
        if (visibleImageMask != null) {
            return true;
        } else {
            String visualImagePath = filepath.substring(0, filepath.lastIndexOf("_"))
                    + Config.POSTFIX_FLIR_IMAGE + ".jpg";
            visibleImageMask = VisibleImageMask.openVisibleImage(this, visualImagePath);
            return visibleImageMask != null;
        }
    }

    public boolean isVisibleImageAttached() {
        return visibleImageMask != null;
    }

    public VisibleImageMask getVisibleImageMask() {
        return visibleImageMask;
    }
}
