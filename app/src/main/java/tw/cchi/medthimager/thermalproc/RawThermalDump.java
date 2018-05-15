package tw.cchi.medthimager.thermalproc;

import android.support.annotation.NonNull;

import com.flir.flironesdk.RenderedImage;

import org.apache.commons.io.FileUtils;
import org.opencv.core.Point;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.utils.CommonUtils;

/**
 * File format v1:
 *  Bytes / Data (LSB, MSB), integer
 *  0 ~ 1		width (number of columns)
 *  2 ~ 3		height (number of rows)
 *  4 ~ M		each thermal pixel is stored by 2 bytes (N=width*height*2 + 4 - 1)
 *  (M+1)		EOF
 *
 * File format v2:
 *  0 ~ 1		     width (number of columns)
 *  2 ~ 3		     height (number of rows)
 *  4 ~ M			 each thermal pixel is stored by 2 bytes (M=2*width*height + 4 - 1)
 *  (M+1) ~ (M+2)	 visible image alignment X offset (dump pixel offset * 10)
 *  (M+3) ~ (M+4)	 visible image alignment Y offset (dump pixel offset * 10)
 *  (M+5)			 EOF
 *
 * File format v3:
 *  0 ~ 1            width (number of columns)
 *  2 ~ 3            height (number of rows)
 *  4                file format version (=3)
 *  5 ~ M            each thermal pixel is stored by 2 bytes (M=2*width*height + 5 - 1)
 *  (M+1) ~ (M+2)    visible image alignment X offset (dump pixel offset * 10)
 *  (M+3) ~ (M+4)    visible image alignment Y offset (dump pixel offset * 10)
 *  (M+5) ~ (M+10)   -
 *  (M+11) ~ (M+50)  title tag (up to 40 ascii chars)
 *  (M+51) ~ (M+82)  patient UUID (32 ascii chars, no '-')
 *  (M+83) ~ (M+90)  -
 *  (M+91)           number of thermal spot markers (up to 20 spot markers)
 *  (M+92) ~ (M+171) thermal spot marker #1 ~ #20, (x: 2 bytes, y: 2 bytes)
 *  (M+172)          EOF
 */
public class RawThermalDump {
    private int formatVersion = 3;
    private String title = null;
    private String patientUUID = null;
    private int width;
    private int height;
    private int[] thermalValues; // 0.01K = 1, (C = val/100 - 273.15)
    private int visibleOffsetX = 0;
    private int visibleOffsetY = 0;
    private ArrayList<Point> spotMarkers = new ArrayList<>();
    private int maxValue = -1;
    private int minValue = -1;
    private String filepath;
    private VisibleImageMask visibleImageMask; // [Android Only]

    /**
     * [Android]
     */
    public RawThermalDump(RenderedImage renderedImage) {
        this.width = renderedImage.width();
        this.height = renderedImage.height();
        this.thermalValues = renderedImage.thermalPixelValues();
    }

    public RawThermalDump(int formatVersion, int width, int height, int[] thermalValues) {
        this.formatVersion = formatVersion;
        this.width = width;
        this.height = height;
        this.thermalValues = thermalValues;
    }

    public static RawThermalDump readFromDumpFile(String filepath) {
        int formatVersion = -1, thermalPixelOffset = -1, M = -1;
        byte[] bytes = readAllBytesFromFile(filepath);
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        int width = twoBytes2SignedInt(bytes[0], bytes[1]);
        int height = twoBytes2SignedInt(bytes[2], bytes[3]);

        // Validate and identify file format version
        boolean versionMatched = false;
        for (int i = 1; i <= 3; i++) {
            if (bytes.length == FileFormat.getByteLength(i, width, height)) {
                formatVersion = i;
                thermalPixelOffset = FileFormat.getThermalPixelOffset(formatVersion);
                M = 2 * width * height + thermalPixelOffset - 1;
                versionMatched = true;
                break;
            }
        }
        if (!versionMatched)
            return null;

        int[] thermalValues = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int byteIndex = thermalPixelOffset + i * 2;
            thermalValues[i] = twoBytes2SignedInt(bytes[byteIndex], bytes[byteIndex + 1]);
        }

        RawThermalDump rawThermalDump = new RawThermalDump(formatVersion, width, height, thermalValues);
        rawThermalDump.setFilepath(filepath);

        if (formatVersion >= 2) {
            rawThermalDump.visibleOffsetX = twoBytes2SignedInt(bytes[M + 1], bytes[M + 2]);
            rawThermalDump.visibleOffsetY = twoBytes2SignedInt(bytes[M + 3], bytes[M + 4]);
        }

        if (formatVersion >= 3) {
            // Read title tag
            int eofIndex = M + 11;
            while (eofIndex <= M + 50 && bytes[eofIndex] != 0) eofIndex++;
            if (eofIndex != M + 11) {
                rawThermalDump.title = new String(Arrays.copyOfRange(bytes, M + 11, eofIndex));
            }

            // Read patient UUID
            if (bytes[M + 51] != 0) {
                rawThermalDump.patientUUID = new String(Arrays.copyOfRange(bytes, M + 51, M + 82 + 1));
            }

            // Read spot marker positions
            int numberOfSpots = bytes[M + 91];
            if (numberOfSpots > 20) {
                return null;
            } else if (numberOfSpots > 0) {
                rawThermalDump.spotMarkers.clear();
                for (int i = 0; i < numberOfSpots; i++) {
                    int index = M + 92 + 4 * i;
                    Point point = new Point(
                            twoBytes2SignedInt(bytes[index], bytes[index + 1]),
                            twoBytes2SignedInt(bytes[index + 2], bytes[index + 3])
                    );
                    rawThermalDump.spotMarkers.add(point);
                }
            }
        }

        return rawThermalDump;
    }

    public synchronized void saveAsync() {
        Observable.create(emitter -> this.save())
            .subscribeOn(Schedulers.computation()).subscribe();
    }

    public synchronized boolean save() {
        if (filepath != null) {
            return saveToFile(filepath);
        } else {
            System.err.println("RawThermalDump: calling save() with null filepath.");
            return false;
        }
    }

    public synchronized boolean saveToFile(String filepath) {
        setFilepath(filepath);

        byte[] bytes = new byte[FileFormat.getByteLength(formatVersion, width, height)];
        int thermalPixelOffset = FileFormat.getThermalPixelOffset(formatVersion);
        int M = width * height * 2 + thermalPixelOffset - 1;

        signedInt2TwoBytes(width, bytes, 0);
        signedInt2TwoBytes(height, bytes, 2);

        if (formatVersion >= 3) {
            bytes[4] = (byte) formatVersion;
        }

        for (int i = 0; i < width * height; i++) {
            signedInt2TwoBytes(thermalValues[i], bytes, thermalPixelOffset + i * 2);
        }

        // Visible image offset
        if (formatVersion >= 2) {
            signedInt2TwoBytes(visibleOffsetX, bytes, M + 1);
            signedInt2TwoBytes(visibleOffsetY, bytes, M + 3);
        }

        if (formatVersion >= 3) {
            // Title tag
            if (title != null) {
                byte[] titleTagBytes = this.title.getBytes();
                for (int i = 0; i < titleTagBytes.length; i++) {
                    bytes[M + 11 + i] = titleTagBytes[i];
                }
                bytes[M + 11 + titleTagBytes.length] = 0; // end-of-string character
            } else {
                // Set the first char as NULL
                bytes[M + 11] = 0;
            }

            // Patient UUID
            if (patientUUID != null) {
                byte[] patientUUIDBytes = this.patientUUID.getBytes();
                for (int i = 0; i < 32; i++) {
                    bytes[M + 51 + i] = patientUUIDBytes[i];
                }
            } else {
                // Set the first char as NULL
                bytes[M + 51] = 0;
            }

            // Spot maker positions
            if (spotMarkers.size() > 20) return false;
            bytes[M + 91] = (byte) spotMarkers.size();
            for (int i = 0; i < spotMarkers.size(); i++) {
                Point point = spotMarkers.get(i);
                int index = M + 92 + 4 * i;
                signedInt2TwoBytes((int) point.x, bytes, index);
                signedInt2TwoBytes((int) point.y, bytes, index + 2);
            }
        }

        try {
            FileUtils.writeByteArrayToFile(new File(filepath), bytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public String getFlirImagePath() {
        return filepath.substring(0, filepath.lastIndexOf("_"))
            + Constants.POSTFIX_FLIR_IMAGE + ".jpg";
    }

    public String getColoredImagePath() {
        return filepath.substring(0, filepath.lastIndexOf("_"))
            + Constants.POSTFIX_COLORED_IMAGE + ".png";
    }

    public String getVisibleImagePath() {
        return filepath.substring(0, filepath.lastIndexOf("_"))
            + Constants.POSTFIX_VISIBLE_IMAGE + ".png";
    }

    public static String generateTitleFromFilepath(String filepath) {
        String title;

        // Generate title; filepathEx: 1008-161008_2_raw.dat
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

        return title;
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
        if (formatVersion < 2) formatVersion = 2;
        this.visibleOffsetX = visibleOffsetX;
    }

    public int getVisibleOffsetY() {
        return visibleOffsetY;
    }

    public void setVisibleOffsetY(int visibleOffsetY) {
        if (formatVersion < 2) formatVersion = 2;
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
        this.title = generateTitleFromFilepath(filepath);
    }

    public String getTitle() {
        return title;
    }

    public boolean setTitle(String title) {
        if (title.length() > 40 || !isPureAscii(title))
            return false;

        if (formatVersion < 3) formatVersion = 3;
        this.title = title;
        return true;
    }

    /**
     * @return Empty ArrayList instance if no spot stored
     */
    public ArrayList<Point> getSpotMarkers() {
        return spotMarkers;
    }

    public boolean setSpotMarkers(@NonNull ArrayList<Point> spotMarkers) {
        if (spotMarkers.size() > 20)
            return false;

        if (formatVersion < 3) formatVersion = 3;
        this.spotMarkers = spotMarkers;
        return true;
    }

    public String getPatientUUID() {
        return patientUUID;
    }

    public boolean setPatientUUID(String patientUUID) {
        patientUUID = patientUUID.replace("-", "");
        if (patientUUID.length() != 32)
            return false;

        if (formatVersion < 3) formatVersion = 3;
        this.patientUUID = patientUUID;
        return true;
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
     *
     * Note: this code is not optimized.
     *
     * @return The average temperature in degree Celsius
     */
    public double getTemperature9Average(int x, int y) {
        int centerPixelIndex = CommonUtils.trimByRange(width * y + x, width + 1, width * (height - 1) - 1);
        int[] centerPixelIndexes = new int[] {
                centerPixelIndex, centerPixelIndex - 1, centerPixelIndex + 1,
                centerPixelIndex - width,
                centerPixelIndex - width - 1,
                centerPixelIndex - width + 1,
                centerPixelIndex + width,
                centerPixelIndex + width - 1,
                centerPixelIndex + width + 1
        };

        int sum = 0;
        for (int i = 0; i < centerPixelIndexes.length; i++) {
            // Remember: all primitives are signed, we want the unsigned value,
            // we've used renderedImage.thermalPixelValues() to get unsigned values
            sum += thermalValues[centerPixelIndexes[i]];
        }
        double averageTemp = (double) sum / centerPixelIndexes.length;

        return (averageTemp / 100) - 273.15;
    }

    private static void signedInt2TwoBytes(int number, byte[] bytes, int startFrom) {
        bytes[startFrom] = (byte) (number & 0xff); // lsb
        bytes[startFrom + 1] = (byte) ((number & 0xff00) >> 8); // msb
    }

    private static int twoBytes2SignedInt(byte lsb, byte msb) {
        int result = (msb & 0xff) << 8 | (lsb & 0xff);
        // negative number
        if ((msb & 0x80) == 0x80)
            result = -1 * ((~result & 0xffff) + 1); // 2's complement
        return result;
    }

    private static boolean isPureAscii(String v) {
        CharsetEncoder asciiEncoder = Charset.forName("US-ASCII").newEncoder(); // or "ISO-8859-1" for ISO Latin 1
        return asciiEncoder.canEncode(v);
    }

    private static byte[] readAllBytesFromFile(String filepath) {
        File file = new File(filepath);
        if (!file.exists()) return null;

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

    private static class FileFormat {
        private static int getByteLength(int formatVersion, int width, int height) {
            final int[] lengths = new int[]{
                    4   + width * height * 2,
                    8   + width * height * 2,
                    176 + width * height * 2
            };
            if (formatVersion < 1 || formatVersion > lengths.length)
                return -1;
            else
                return lengths[formatVersion - 1];
        }

        private static int getThermalPixelOffset(int formatVersion) {
            int[] offsets = new int[]{4, 4, 5};
            if (formatVersion < 1 || formatVersion > offsets.length)
                return -1;
            else
                return offsets[formatVersion - 1];
        }
    }


    // ---------------------- [Android] ---------------------- //

    public boolean attachVisibleImageMask(int defaultOffsetX, int defaultOffsetY) {
        if (visibleImageMask != null) {
            return true;
        } else {
            visibleImageMask = VisibleImageMask.openVisibleImage(
                this, getFlirImagePath()
            );

            // If visible image offset wasn't set, set from the default value
            if (visibleOffsetX == 0 && visibleOffsetY == 0) {
                visibleOffsetX = defaultOffsetX;
                visibleOffsetY = defaultOffsetY;
                saveAsync();
            }

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
