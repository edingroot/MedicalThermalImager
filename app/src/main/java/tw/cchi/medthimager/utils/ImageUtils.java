package tw.cchi.medthimager.utils;

import android.graphics.Bitmap;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static org.opencv.core.Core.FILLED;
import static org.opencv.imgproc.Imgproc.drawContours;

public class ImageUtils {

    /**
     * [Android] Save bitmap to PNG file.
     */
    public static boolean saveBitmap(Bitmap bitmap, String filename) {
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(filename);

            // PNG is a lossless format, the compression factor (100) is ignored
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

    /**
     * Get geometric transform matrix of shift operation. (can be applied in Imgproc.warpAffine())
     *
     * Ref: https://docs.opencv.org/3.0-beta/doc/py_tutorials/py_imgproc/py_geometric_transformations/py_geometric_transformations.html
     */
    public static Mat getShiftTransFormMatrix(int dX, int dY) {
        Mat mat = new Mat(2, 3, CvType.CV_32F);
        int[][] M = new int[][]{
            {1, 0, dX},
            {0, 1, dY}
        };

        for (int i = 0; i < M.length; i++) {
            for (int j = 0; j < M[i].length; j++) {
                mat.put(i, j, M[i][j]);
            }
        }

        return mat;
    }

    public static Point[] getPointsInsideContour(MatOfPoint contour, Size imageSize) {
        Mat mask = Mat.zeros(imageSize, CvType.CV_8UC1);
        ArrayList<MatOfPoint> contours = new ArrayList<>();

        contours.add(contour);
        drawContours(mask, contours, 0, new Scalar(255), FILLED);

        Mat insidePointsMat = new Mat(contour.size(), contour.type());
        Core.findNonZero(mask, insidePointsMat);

        return new MatOfPoint(insidePointsMat).toArray();
    }

    public static Point[] getPointsOutsideContour(MatOfPoint contour, Size imageSize) {
        Mat mask = new Mat(imageSize, CvType.CV_8UC1);
        ArrayList<MatOfPoint> contours = new ArrayList<>();

        contours.add(contour);
        mask.setTo(new Scalar(255));
        drawContours(mask, contours, 0, new Scalar(0), FILLED);

        Mat insidePointsMat = new Mat(contour.size(), contour.type());
        Core.findNonZero(mask, insidePointsMat);

        return new MatOfPoint(insidePointsMat).toArray();
    }

}
