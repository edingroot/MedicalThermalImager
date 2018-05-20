package tw.cchi.medthimager.thermalproc;

import android.graphics.Bitmap;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.contrib.Contrib;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.utils.ImageUtils;

public class ThermalDumpProcessor {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private final static int MAX_TEMP_ALLOWED = 2731 + 1200; // 120 deg Celsius
    private final static int COLOR_TEMP_MIN = 10; // 10 deg Celsius
    private final static int COLOR_TEMP_MAX = 40; // 40 deg Celsius
    private final static double FILTER_RATIO_LOW = 0.005; // Set to 0 to disable
    private final static double FILTER_RATIO_HIGH = 0.0005; // Set to 0 to disable

    private int width = 0;
    private int height = 0;
    private int pixelCount = 0;
    private int[] thermalValues10; // 0.1K = 1 (different from RawThermalDump)
    private int[] thermalHist; // thermalHist[thermalValue10] = pixelCount
    private int minThermalValue = 0; // thermalValue10 = 0 ignored
    private int maxThermalValue = 0;

    private Mat generatedImage = null;

    static {
        // [Android]
        OpenCVLoader.initDebug();

//        // [Native Java]
//        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        System.loadLibrary("NativeLibs");
    }

    public ThermalDumpProcessor(RawThermalDump thermalDump) {
        int[] thermalValues = thermalDump.getThermalValues();

        this.width = thermalDump.getWidth();
        this.height = thermalDump.getHeight();
        this.pixelCount = thermalValues.length;
        this.thermalValues10 = new int[pixelCount];
        this.thermalHist = new int[MAX_TEMP_ALLOWED];

        // Load thermalValues10 & calculate thermalHist
        cvtThermalValues10Native(thermalValues);
        updateThermalHistNative();
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
        if (thermalThreshold10K <= minThermalValue)
            return;

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
        if (thermalThreshold10K >= maxThermalValue)
            return;

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
        for (Point point : ImageUtils.getPointsOutsideContour(contour, generatedImage.size())) {
            thermalValues10[(int) (point.x + point.y * width)] = 0;
        }
        updateThermalHistNative();
        generatedImage = null;
    }

    /**
     * [For Android] Get generated image with contrast adjusted with contrastRatio.
     *
     * @param contrastRatio Enhance contrast if > 1 and vise versa.
     */
    public Bitmap getBitmap(double contrastRatio, boolean colored) {
        Mat resultMat = getImageMat(contrastRatio, colored);
        Bitmap resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        if (colored)
            Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_BGR2RGB);

        Utils.matToBitmap(resultMat, resultBmp);
        return resultBmp;
    }

    public Mat getImageMat() {
        return getImageMat(1, false);
    }

    /**
     * Get generated image Mat with contrast adjusted with contrastRatio.
     *
     * @param contrastRatio Enhance contrast if > 1 and vise versa.
     */
    public Mat getImageMat(double contrastRatio, boolean colored) {
        if (generatedImage == null) {
            generateThermalImage();
        }

        Mat result = generatedImage.clone();
        if (contrastRatio != 1) {
            // convertTo: last 2 params are the alpha and beta values
             generatedImage.convertTo(result, -1, contrastRatio, 0);
        }

        if (colored) {
            Core.bitwise_not(result, result);
            Contrib.applyColorMap(result, result, Contrib.COLORMAP_RAINBOW);
        }

        return result;
    }

    /**
     * Generate grayscale thermal image from temperature values
     */
    public void generateThermalImage() {
        /* generateThermalImage(
                (minThermalValue - 2731) / 10.0f,
                (maxThermalValue - 2731) / 10.0f); */
        generateThermalImage(COLOR_TEMP_MIN, COLOR_TEMP_MAX);
    }

    /**
     * Generate grayscale thermal image from temperature values
     * - The interval [temp0, temp255] should NOT be too large, which may cause the generated image only has few colors.
     *
     * @param temp0 The minimum temperature of the histogram left most index (0/255)
     * @param temp255 The minimum temperature of the histogram right most index (255/255)
     */
    public void generateThermalImage(float temp0, float temp255) {
        generatedImage = new Mat(height, width, CvType.CV_8UC1);
        generateThermalImageNative(temp0, temp255, generatedImage.getNativeObjAddr());
    }

    private synchronized native void generateThermalImageNative(float temp0, float temp255, long resultMatAddr);

    private synchronized native void cvtThermalValues10Native(int[] thermalValues);

    private synchronized native void updateThermalHistNative();
}
