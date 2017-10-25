package tw.cchi.flironedemo1;

import android.os.Environment;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.Core.FILLED;
import static org.opencv.imgproc.Imgproc.drawContours;

public class AppUtils {

    public static String getExportsDir() {
        // String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirEx1");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    /**
     * Effective range: min <= val < max
     * @param val
     * @param min
     * @param max
     * @return
     */
    public static int trimByRange(int val, int min, int max) {
        if (val < min)
            return min;
        else if (val >= max)
            return max - 1;
        else
            return val;
    }

    public static void removeListDuplication(List<String> list, List<String> compareTarget) {
        for (String str : list) {
            if (compareTarget.contains(str)) {
                list.remove(str);
            }
        }
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
        mask.setTo(new Scalar(255));
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        contours.add(contour);
        drawContours(mask, contours, 0, new Scalar(0), FILLED);
        Mat insidePointsMat = new Mat(contour.size(), contour.type());
        Core.findNonZero(mask, insidePointsMat);
        return new MatOfPoint(insidePointsMat).toArray();
    }

}
