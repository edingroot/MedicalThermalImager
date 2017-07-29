package tw.cchi.flironedemo1.thermalproc;


import org.opencv.core.Mat;

public class PointProcessor {

    public static void setMinAndMax(Mat img, int min, int max) {
        int[] lut = new int[256];
        int targetRange = max - min;

        for (int i = 0; i < 256; i++) {
            int v = i - min;
            v = (int) (256.0 * v / targetRange);

            if (v < 0) {
                v = 0;
            } else if (v > 255) {
                v = 255;
            }

            lut[i] = v;
        }

        // Apply lookup table
        for (int row = 0; row < img.rows(); row++) {
            for (int col = 0; col < img.cols(); col++) {
                int v = (int) img.get(row, col)[0];
                img.put(row, col, lut[v]);
            }
        }
    }

}
