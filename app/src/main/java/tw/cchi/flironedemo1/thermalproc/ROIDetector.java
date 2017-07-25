package tw.cchi.flironedemo1.thermalproc;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.CvType.CV_8U;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_NONE;
import static org.opencv.imgproc.Imgproc.RETR_EXTERNAL;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.dilate;
import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.threshold;

public class ROIDetector {
    private Mat src;

    public ROIDetector(Mat src) {
        this.src = src;
    }

    public Mat identifyContours(int threshold) {
        Mat dst = new Mat(src.size(), CV_8U, new Scalar(0));
        threshold(src, dst, threshold, 255, THRESH_BINARY);

        // Dilate
        dilate(dst, dst, new Mat());

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        findContours(dst, contours, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_NONE);
        // Draw
        // Mat result = new Mat(dst.size(), CV_8U, new Scalar(0));
        Mat result = src.clone();
        drawContours(result, contours, -1, new Scalar(255), 1);

        return result;
    }
}
