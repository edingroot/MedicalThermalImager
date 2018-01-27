package tw.cchi.flironedemo1.thermalproc;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.*;

import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.Config;

public class ThermalDumpProcessor {
    private final static int MAX_ALLOWED = 2731 + 1200; // 120 deg Celsius
    private final static double FILTER_RATIO_LOW = 0.005; // Set to 0 to disable
    private final static double FILTER_RATIO_HIGH = 0.0005; // Set to 0 to disable

    private int width = 0;
    private int height = 0;
    private int pixelCount = 0;
    private int[] thermalValues10; // 0.1K = 1 (different from RawThermalDump)
    private int[] thermalHist; // thermalHist[thermalValue10] = pixelCount
    private int minThermalValue = 0; // thermalValue10 = 0 ignored
    private int maxThermalValue = 0;

    private volatile Mat generatedImage = null;

    static {
        OpenCVLoader.initDebug(); // [For Android]
        // System.loadLibrary(Core.NATIVE_LIBRARY_NAME); // [For native java]
    }

    public ThermalDumpProcessor(RawThermalDump thermalDump) {
        int[] thermalValues = thermalDump.getThermalValues();

        this.width = thermalDump.getWidth();
        this.height = thermalDump.getHeight();
        this.pixelCount = thermalValues.length;
        this.thermalValues10 = new int[pixelCount];

        // Load thermalValues10 & calculate thermalHist (same as this.updateThermalHist())
        thermalHist = new int[MAX_ALLOWED];
        minThermalValue = Integer.MAX_VALUE;
        maxThermalValue = Integer.MIN_VALUE;
        for (int i = 0; i < pixelCount; i++) {
            thermalValues10[i] = thermalValues[i] / 10;
            thermalHist[thermalValues10[i]]++;

            if (thermalValues10[i] != 0 && thermalValues10[i] < minThermalValue)
                minThermalValue = thermalValues10[i];
            if (thermalValues10[i] > maxThermalValue)
                maxThermalValue = thermalValues10[i];
        }
    }

    // Would be lossy compared to the original thermalDump input (due to the thermalValues10 conversion)
    public RawThermalDump getThermalDump() {
        int[] thermalValues = new int[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            thermalValues[i] = thermalValues10[i] * 10;
        }
        return new RawThermalDump(1, width, height, thermalValues);
    }

    public void autoFilter() {
        int minPixelThreshold;

        Log.i(Config.TAG, String.format("autoFilter - before: min=%d, max=%d\n", minThermalValue, maxThermalValue));

        minPixelThreshold = (int) (pixelCount * FILTER_RATIO_LOW);
        for (int i = minThermalValue; i <= maxThermalValue; i++) {
            if (thermalHist[i] < minPixelThreshold) {
                thermalHist[i] = 0;
                minThermalValue = i;
            } else {
                break;
            }
        }

        minPixelThreshold = (int) (pixelCount * FILTER_RATIO_HIGH);
        for (int i = maxThermalValue; i >= 0; i--) {
            if (thermalHist[i] < minPixelThreshold) {
                thermalHist[i] = 0;
                maxThermalValue = i;
            } else {
                break;
            }
        }

        Log.i(Config.TAG, String.format("autoFilter - after: min=%d, max=%d\n", minThermalValue, maxThermalValue));

        // Filter thermalValues10
        for (int i = 0; i < pixelCount; i++) {
            if (thermalValues10[i] < minThermalValue) {
                thermalValues10[i] = minThermalValue;
                thermalHist[minThermalValue]++;
            } else if (thermalValues10[i] > maxThermalValue) {
                thermalValues10[i] = maxThermalValue;
                thermalHist[maxThermalValue]++;
            }
        }

        generatedImage = null;
    }

    public void filterBelow(int thermalThreshold10K) {
        if (thermalThreshold10K <= minThermalValue) {
            Log.i(Config.TAG, String.format("filterBelow: minThermalValue=%d, newThreshold=%d, ignore.\n", minThermalValue, thermalThreshold10K));
            return;
        }

        // Filter thermalValues10
        minThermalValue = thermalThreshold10K;
        if (minThermalValue > maxThermalValue)
            maxThermalValue = minThermalValue;
        for (int i = 0; i < pixelCount; i++) {
            if (thermalValues10[i] < minThermalValue) {
                thermalHist[thermalValues10[i]]--;
                thermalHist[minThermalValue]++;
                thermalValues10[i] = minThermalValue;
            }
        }

        generatedImage = null;
    }

    public void filterAbove(int thermalThreshold10K) {
        if (thermalThreshold10K >= maxThermalValue) {
            return;
        }

        // Filter thermalValues10
        maxThermalValue = thermalThreshold10K;
        if (maxThermalValue < minThermalValue)
            minThermalValue = maxThermalValue;
        for (int i = 0; i < pixelCount; i++) {
            if (thermalValues10[i] > maxThermalValue) {
                thermalHist[thermalValues10[i]]--;
                thermalHist[maxThermalValue]++;
                thermalValues10[i] = maxThermalValue;
            }
        }

        generatedImage = null;
    }

    public void filterFromContour(MatOfPoint contour) {
        for (Point point : AppUtils.getPointsOutsideContour(contour, generatedImage.size())) {
            thermalValues10[(int) (point.x + point.y * width)] = 0;
        }
        updateThermalHist();
        generatedImage = null;
    }

    /**
     * [For Android] Get generated image with contrast adjusted with contrastRatio.
     *
     * @param contrastRatio Enhance contrast if > 1 and vise versa.
     */
    public Bitmap getBitmap(double contrastRatio) {
        Bitmap resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Utils.matToBitmap(getImageMat(contrastRatio), resultBmp);
        return resultBmp;
    }

    /**
     * Get generated image Mat with contrast adjusted with contrastRatio.
     *
     * @param contrastRatio Enhance contrast if > 1 and vise versa.
     */
    public Mat getImageMat(double contrastRatio) {
        if (generatedImage == null) {
            generateThermalImage();
        }

        if (contrastRatio == 1) {
            return generatedImage;
        } else {
            Mat result = generatedImage.clone();
            // convertTo: last 2 params are the alpha and beta values
            generatedImage.convertTo(result, -1, contrastRatio, 0);
            return result;
        }
    }

    /**
     * Generate grayscale thermal image from temperature values
     *
     * @return
     */
    public synchronized Mat generateThermalImage() {
        return generateThermalImage(
                (minThermalValue - 2731) / 10.0f,
                (maxThermalValue - 2731) / 10.0f
        );
    }

    /**
     * Generate grayscale thermal image from temperature values
     * - The interval [temp0, temp255] should NOT be too large, which may cause the generated image only has few colors.
     *
     * @param temp0 The minimum temperature of the histogram left most index (0/255)
     * @param temp255 The minimum temperature of the histogram right most index (255/255)
     * @return
     */
    public synchronized Mat generateThermalImage(float temp0, float temp255) {
        generatedImage = new Mat(height, width, CvType.CV_8UC1);
        int thermalValue0 = (int) (temp0 * 10) + 2731;
        int thermalValue255 = (int) (temp255 * 10) + 2731;
        double hopPer10K = 254.0 / (thermalValue255 - thermalValue0);

        // Generate thermalValue-grayLevel LUT: thermalLUT[temp10K] = grayLevel (0~255)
        short[] thermalLUT = new short[1 + (maxThermalValue > thermalValue255 ? maxThermalValue : thermalValue255)];

        // We reserve grayLevel=0 for identifying image moved
        for (int i = minThermalValue; i < thermalValue0; i++) {
            thermalLUT[i] = 1;
        }

        // Effective range: [1, 254]
        double sum = 1;
        for (int i = thermalValue0; i <= thermalValue255; i++) {
            thermalLUT[i] = sum > 255 ? (short) 255 : (short) sum;
            sum += hopPer10K;
        }

        for (int i = thermalValue255 + 1; i <= maxThermalValue; i++) {
            thermalLUT[i] = 255;
        }

        // Generate image from LUT
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (thermalValues10[col + row * width] == 0)
                    generatedImage.put(row, col, 0);
                else
                    generatedImage.put(row, col, thermalLUT[thermalValues10[col + row * width]]);
            }
        }

        return generatedImage;
    }

    private void updateThermalHist() {
        thermalHist = new int[MAX_ALLOWED];
        minThermalValue = pixelCount;
        maxThermalValue = 0;
        for (int i = 0; i < pixelCount; i++) {
            thermalHist[thermalValues10[i]]++;
            if (thermalValues10[i] != 0 && thermalValues10[i] < minThermalValue)
                minThermalValue = thermalValues10[i];
            if (thermalValues10[i] > maxThermalValue)
                maxThermalValue = thermalValues10[i];
        }
    }

}
