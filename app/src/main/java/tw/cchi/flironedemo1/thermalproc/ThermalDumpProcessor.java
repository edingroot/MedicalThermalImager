package tw.cchi.flironedemo1.thermalproc;

import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import tw.cchi.flironedemo1.Config;

import static org.opencv.imgproc.Imgproc.pointPolygonTest;

public class ThermalDumpProcessor {
    public int thermalHistMin = 0; // thermalValue10 = 0 ignored
    public int thermalHistMax = 0;

    private final static int MAX_ALLOWED = 2731 + 1200; // 120 deg Celsius
    private final static double FILTER_RATIO_LOW = 0.005; // Set to 0 to disable
    private final static double FILTER_RATIO_HIGH = 0.0005; // Set to 0 to disable
    private int width = 0;
    private int height = 0;
    private int pixelCount = 0;
    private int[] thermalValues10; // 0.1K = 1 (different from RawThermalDump)
    private int[] thermalHist;
    private volatile Mat generatedImage = null;

    public ThermalDumpProcessor(RawThermalDump thermalDump) {
        OpenCVLoader.initDebug();

        this.width = thermalDump.width;
        this.height = thermalDump.height;
        this.pixelCount = thermalDump.thermalValues.length;
        this.thermalValues10 = new int[pixelCount];

        // Load thermalValues10 & Calculate thermalHist (same as this.updateThermalHist())
        thermalHist = new int[MAX_ALLOWED];
        thermalHistMin = pixelCount;
        thermalHistMax = 0;
        for (int i = 0; i < pixelCount; i++) {
            thermalValues10[i] = thermalDump.thermalValues[i] / 10;

            thermalHist[thermalValues10[i]]++;
            if (thermalValues10[i] != 0 && thermalValues10[i] < thermalHistMin)
                thermalHistMin = thermalValues10[i];
            if (thermalValues10[i] > thermalHistMax)
                thermalHistMax = thermalValues10[i];
        }
    }

    // Would be lossy compared to the original thermalDump input (due to thermalValues10 conversion)
    public RawThermalDump getThermalDump() {
        int[] thermalValues = new int[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            thermalValues[i] = thermalValues10[i] * 10;
        }
        return new RawThermalDump(width, height, thermalValues);
    }

    public void autoFilter() {
        int minPixelThreshold;

        Log.i(Config.TAG, String.format("autoFilter - before: min=%d, max=%d", thermalHistMin, thermalHistMax));

        minPixelThreshold = (int) (pixelCount * FILTER_RATIO_LOW);
        for (int i = thermalHistMin; i <= thermalHistMax; i++) {
            if (thermalHist[i] < minPixelThreshold) {
                thermalHist[i] = 0;
                thermalHistMin = i;
            } else {
                break;
            }
        }

        minPixelThreshold = (int) (pixelCount * FILTER_RATIO_HIGH);
        for (int i = thermalHistMax; i >= 0; i--) {
            if (thermalHist[i] < minPixelThreshold) {
                thermalHist[i] = 0;
                thermalHistMax = i;
            } else {
                break;
            }
        }

        Log.i(Config.TAG, String.format("autoFilter - after: min=%d, max=%d", thermalHistMin, thermalHistMax));

        // Filter thermalValues10
        for (int i = 0; i < pixelCount; i++) {
            if (thermalValues10[i] < thermalHistMin) {
                thermalValues10[i] = thermalHistMin;
                thermalHist[thermalHistMin]++;
            } else if (thermalValues10[i] > thermalHistMax) {
                thermalValues10[i] = thermalHistMax;
                thermalHist[thermalHistMax]++;
            }
        }

        generatedImage = null;
    }

    public void filterBelow(int thermalThreshold10K) {
        if (thermalThreshold10K <= thermalHistMin) {
            Log.e(Config.TAG, String.format("filterBelow: thermalHistMin=%d, newThreshold=%d, ignore.", thermalHistMin, thermalThreshold10K));
            return;
        }

        // Filter thermalValues10
        thermalHistMin = thermalThreshold10K;
        for (int i = 0; i < pixelCount; i++) {
            if (thermalValues10[i] < thermalHistMin) {
                thermalHist[thermalValues10[i]]--;
                thermalHist[thermalHistMin]++;
                thermalValues10[i] = thermalHistMin;
            }
        }

        generatedImage = null;
    }

    public void filterAbove(int thermalThreshold10K) {
        if (thermalThreshold10K >= thermalHistMax) {
            Log.e(Config.TAG, String.format("filterAbove: thermalHistMax=%d, newThreshold=%d, ignore.", thermalHistMax, thermalThreshold10K));
            return;
        }

        // Filter thermalValues10
        thermalHistMax = thermalThreshold10K;
        for (int i = 0; i < pixelCount; i++) {
            if (thermalValues10[i] > thermalHistMax) {
                thermalHist[thermalValues10[i]]--;
                thermalHist[thermalHistMax]++;
                thermalValues10[i] = thermalHistMax;
            }
        }

        generatedImage = null;
    }

    public void filterByContour(MatOfPoint contour) {
        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (-1 == pointPolygonTest(contour2f, new Point(col, row), false)) {
                    thermalValues10[col + row * width] = 0;
                }
            }
        }
        updateThermalHist();
        generatedImage = null;
    }

    public Mat getGeneratedImage() {
        if (generatedImage == null) {
            generateThermalImage();
        }
        return generatedImage;
    }

    /**
     * Get generated image with contrast adjusted with contrastRatio
     * @param contrastRatio Enhance contrast if > 1 and vise versa.
     */
    public Mat getGeneratedImage(double contrastRatio) {
        if (generatedImage == null) {
            generateThermalImage();
        }
        Mat result = generatedImage.clone();
        // convertTo: last 2 params are the alpha and beta values
        generatedImage.convertTo(result, -1, contrastRatio, 0);
        return result;
    }

    private synchronized void generateThermalImage() {
        generatedImage = new Mat(height, width, CvType.CV_8U);

        Log.i(Config.TAG, String.format("generateThermalImage - min=%d, max=%d", thermalHistMin, thermalHistMax));

        // Generate thermal LUT
        short[] thermalLUT = new short[thermalHistMax + 1]; // thermalLUT[tempK] = grayLevel (0~255)
        double increasePerK = 255.0 / (thermalHistMax - thermalHistMin);
        double sum = 0;
        for (int i = thermalHistMin; i <= thermalHistMax; i++) {
            thermalLUT[i] = sum > 255 ? (short) 255 : (short) sum;
            sum += increasePerK;
        }

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (thermalValues10[col + row * width] == 0)
                    generatedImage.put(row, col, 0);
                else
                    generatedImage.put(row, col, thermalLUT[thermalValues10[col + row * width]]);
            }
        }
    }

    private void updateThermalHist() {
        thermalHist = new int[MAX_ALLOWED];
        thermalHistMin = pixelCount;
        thermalHistMax = 0;
        for (int i = 0; i < pixelCount; i++) {
            thermalHist[thermalValues10[i]]++;
            if (thermalValues10[i] != 0 && thermalValues10[i] < thermalHistMin)
                thermalHistMin = thermalValues10[i];
            if (thermalValues10[i] > thermalHistMax)
                thermalHistMax = thermalValues10[i];
        }
    }

}