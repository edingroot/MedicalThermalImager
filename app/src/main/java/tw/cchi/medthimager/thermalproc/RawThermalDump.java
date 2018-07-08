package tw.cchi.medthimager.thermalproc;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

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
import java.util.Date;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.util.CommonUtils;

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
 *  (M+51) ~ (M+82)  patient CUID (32 ascii chars, no '-')
 *  (M+83) ~ (M+90)  -
 *  (M+91)           number of thermal spot markers (up to 20 spot markers)
 *  (M+92) ~ (M+171) thermal spot marker #1 ~ #20, (x: 2 bytes, y: 2 bytes)
 *  (M+172)          EOF
 *
 * File format v4:
 *  0 ~ 1              width (number of columns)
 *  2 ~ 3              height (number of rows)
 *  4                  file format version (=4)
 *  5 ~ M              each thermal pixel is stored by 2 bytes (M=2*width*height + 5 - 1)
 *  (M+1)   ~ (M+2)    visible image alignment X offset (dump pixel offset * 10)
 *  (M+3)   ~ (M+4)    visible image alignment Y offset (dump pixel offset * 10)
 *  (M+5)   ~ (M+10)   -
 *  (M+11)  ~ (M+50)   title tag (up to 40 ascii chars)
 *  (M+51)  ~ (M+82)   patient CUID (32 ascii chars, no '-')
 *  (M+83)  ~ (M+86)   capture timestamp (unix timestamp as 4 bytes number; int in java, long in C)
 *  (M+87)  ~ (M+90)   -
 *  (M+91)             number of thermal spot markers (up to 20 spot markers)
 *  (M+92)  ~ (M+171)  thermal spot marker #1 ~ #20, (x: 2 bytes, y: 2 bytes)
 *  (M+172)            -
 *  (M+173) ~ (M+204)  record UUID (32 ascii chars, no '-')
 *  (M+205)            EOF
 */
public class RawThermalDump implements Disposable {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private int formatVersion = 4;
    private String recordUuid = null;
    private String patientCuid = null;
    private String title = null;
    private int width;
    private int height;
    private int[] thermalValues; // 0.01K = 1, (C = val/100 - 273.15)
    private int visibleOffsetX = 0;
    private int visibleOffsetY = 0;
    private ArrayList<Point> spotMarkers = new ArrayList<>();
    private int maxValue = -1;
    private int minValue = -1;
    private String filepath;
    private int captureTimestamp;

    // [Android Only]
    private VisibleImageMask visibleImageMask;
    private boolean disposed = false;

    /**
     * [Android]
     */
    public RawThermalDump(RenderedImage renderedImage, String title, Date captureTime) {
        this.width = renderedImage.width();
        this.height = renderedImage.height();
        this.thermalValues = renderedImage.thermalPixelValues();
        this.title = title;
        this.setCaptureTimestamp(captureTime);
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

        int width = bytes2SignedInt(bytes, 0, 2);
        int height = bytes2SignedInt(bytes, 2, 2);

        // Validate and identify file format version
        boolean versionMatched = false;
        for (int i = 1; i <= 4; i++) {
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
            thermalValues[i] = bytes2SignedInt(bytes, byteIndex, 2);
        }

        RawThermalDump rawThermalDump = new RawThermalDump(formatVersion, width, height, thermalValues);
        rawThermalDump.setFilepath(filepath);

        if (formatVersion >= 2) {
            rawThermalDump.visibleOffsetX = bytes2SignedInt(bytes, M + 1, 2);
            rawThermalDump.visibleOffsetY = bytes2SignedInt(bytes, M + 3, 2);
        }

        if (formatVersion >= 3) {
            // Read title tag
            int eofIndex = M + 11;
            while (eofIndex <= M + 50 && bytes[eofIndex] != 0) eofIndex++;
            if (eofIndex != M + 11) {
                rawThermalDump.title = new String(Arrays.copyOfRange(bytes, M + 11, eofIndex));
            }

            // Read patient CUID
            if (bytes[M + 51] != 0) {
                rawThermalDump.patientCuid = new String(Arrays.copyOfRange(bytes, M + 51, M + 82 + 1));
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
                            bytes2SignedInt(bytes, index, 2),
                            bytes2SignedInt(bytes, index + 2, 2));
                    rawThermalDump.spotMarkers.add(point);
                }
            }
        }

        if (formatVersion >= 4) {
            // Read capture timestamp
            rawThermalDump.captureTimestamp = bytes2SignedInt(bytes, M + 83, 4);

            // Read record UUID
            if (bytes[M + 173] != 0) {
                rawThermalDump.recordUuid = new String(Arrays.copyOfRange(bytes, M + 173, M + 204 + 1));
            }
        }

        return rawThermalDump;
    }

    public void saveAsync() {
        Observable.create(emitter -> this.save())
            .subscribeOn(Schedulers.io())
            .subscribe();
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
        byte[] bytes = new byte[FileFormat.getByteLength(formatVersion, width, height)];
        int thermalPixelOffset = FileFormat.getThermalPixelOffset(formatVersion);
        int M = width * height * 2 + thermalPixelOffset - 1;

        signedInt2Bytes(width, bytes, 0, 2);
        signedInt2Bytes(height, bytes, 2, 2);

        if (formatVersion >= 3) {
            bytes[4] = (byte) formatVersion;
        }

        for (int i = 0; i < width * height; i++) {
            signedInt2Bytes(thermalValues[i], bytes, thermalPixelOffset + i * 2, 2);
        }

        // Visible image offset
        if (formatVersion >= 2) {
            signedInt2Bytes(visibleOffsetX, bytes, M + 1, 2);
            signedInt2Bytes(visibleOffsetY, bytes, M + 3, 2);
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

            // Patient CUID
            if (patientCuid != null) {
                byte[] patientCuidBytes = patientCuid.getBytes();
                for (int i = 0; i < 32; i++) {
                    bytes[M + 51 + i] = patientCuidBytes[i];
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
                signedInt2Bytes((int) point.x, bytes, index, 2);
                signedInt2Bytes((int) point.y, bytes, index + 2, 2);
            }
        }

        if (formatVersion >= 4) {
            // Capture timestamp
            signedInt2Bytes(captureTimestamp, bytes, M + 83, 4);

            // Record UUID
            if (recordUuid != null) {
                byte[] recordUuidBytes = recordUuid.getBytes();
                for (int i = 0; i < 32; i++) {
                    bytes[M + 173 + i] = recordUuidBytes[i];
                }
            } else {
                // Set the first char as NULL
                bytes[M + 173] = 0;
            }
        }

        try {
            FileUtils.writeByteArrayToFile(new File(filepath), bytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        setFilepath(filepath);
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
        // this.title = generateTitleFromFilepath(filepath);
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

    public String getPatientCuid() {
        return patientCuid == null ? null : dashUuid(patientCuid);
    }

    public boolean setPatientCuid(String patientCuid) {
        patientCuid = undashUuid(patientCuid);
        if (patientCuid.length() != 32)
            return false;

        if (formatVersion < 3) formatVersion = 3;
        this.patientCuid = patientCuid;
        return true;
    }

    public String getRecordUuid() {
        return recordUuid == null ? null : dashUuid(recordUuid);
    }

    public boolean setRecordUuid(String recordUuid) {
        recordUuid = undashUuid(recordUuid);
        if (recordUuid.length() != 32)
            return false;

        if (formatVersion < 4) formatVersion = 4;
        this.recordUuid = recordUuid;
        return true;
    }

    public Date getCaptureTimestamp() {
        return captureTimestamp == 0 ? null : new Date(captureTimestamp * 1000L);
    }

    public void setCaptureTimestamp(Date date) {
        this.captureTimestamp = (int) (date.getTime() / 1000L);
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

    @Override
    public String toString() {
        String filepath = getFilepath();
        return filepath == null ? super.toString() : getClass().getName() + "@" + filepath;
    }


    private static void signedInt2Bytes(int number, byte[] bytes, int startFrom, int bytesN) {
        switch (bytesN) {
            case 2:
                bytes[startFrom] = (byte) (number & 0xff); // lsb
                bytes[startFrom + 1] = (byte) ((number & 0xff00) >> 8); // msb
                break;

            case 4:
                bytes[startFrom] = (byte) (number & 0xff); // lsb
                bytes[startFrom + 1] = (byte) ((number & 0xff00) >> 8);
                bytes[startFrom + 2] = (byte) ((number & 0xff00) >> 16);
                bytes[startFrom + 3] = (byte) ((number & 0xff00) >> 24); // msb
                break;

            default:
                throw new RuntimeException("Unsupported number of bytes: " + bytesN);
        }
    }

    private static int bytes2SignedInt(byte[] bytes, int startFrom, int bytesN) {
        int result = 0;

        switch (bytesN) {
            case 2:
                result = (bytes[startFrom + 1] & 0xff) << 8 | (bytes[startFrom] & 0xff);
                // negative number
                if ((bytes[startFrom + 1] & 0x80) == 0x80)
                    result = -1 * ((~result & 0xffff) + 1); // 2's complement
                break;

            case 4:
                result = (bytes[startFrom + 3] & 0xff) << 24 | (bytes[startFrom + 2] & 0xff) << 16 |
                        (bytes[startFrom + 1] & 0xff) << 8 | (bytes[startFrom] & 0xff);
                // negative number
                if ((bytes[startFrom + 1] & 0x8000) == 0x8000)
                    result = -1 * (~result + 1); // 2's complement
                break;

            default:
                throw new RuntimeException("Unsupported number of bytes: " + bytesN);
        }

        return result;
    }

    private static boolean isPureAscii(String v) {
        CharsetEncoder asciiEncoder = Charset.forName("US-ASCII").newEncoder(); // or "ISO-8859-1" for ISO Latin 1
        return asciiEncoder.canEncode(v);
    }

    private static String dashUuid(String uuid) {
        return uuid.substring(0, 8) + '-' + uuid.substring(8, 12) + '-' +
                uuid.substring(12, 16) + '-' + uuid.substring(16, 20) + '-' +
                uuid.substring(20);
    }

    private static String undashUuid(String uuid) {
        return uuid.replace("-", "");
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
                    4   + width * height * 2, // v1
                    8   + width * height * 2, // v2
                    176 + width * height * 2, // v3
                    209 + width * height * 2  // v4
            };
            if (formatVersion < 1 || formatVersion > lengths.length)
                return -1;
            else
                return lengths[formatVersion - 1];
        }

        private static int getThermalPixelOffset(int formatVersion) {
            int[] offsets = new int[]{4, 4, 5, 5};
            if (formatVersion < 1 || formatVersion > offsets.length)
                throw new Error("Incorrect format version");
            else
                return offsets[formatVersion - 1];
        }
    }


    // ---------------------- [Android] ---------------------- //

    public void attachVisibleImageMask(Bitmap visibleImage, int defaultOffsetX, int defaultOffsetY) {
        Log.i(TAG, String.format("[attachVisibleImageMask] offset=(%d, %d)", visibleOffsetX, visibleOffsetY));

        if (visibleImageMask != null) {
            visibleImageMask.dispose();
        }
        visibleImageMask = new VisibleImageMask(this, visibleImage);

        // If visible image offset wasn't set, set from the default value
        if (visibleOffsetX == 0 && visibleOffsetY == 0 &&
                defaultOffsetX != 0 && defaultOffsetY != 0) {
            visibleOffsetX = defaultOffsetX;
            visibleOffsetY = defaultOffsetY;
            saveAsync();
        }
    }

    public void detachVisibleImageMask() {
        if (visibleImageMask != null) {
            visibleImageMask.dispose();
            visibleImageMask = null;
        }
    }

    @Nullable
    public VisibleImageMask getVisibleImageMask() {
        return visibleImageMask;
    }

    @Override
    public void dispose() {
        detachVisibleImageMask();
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
