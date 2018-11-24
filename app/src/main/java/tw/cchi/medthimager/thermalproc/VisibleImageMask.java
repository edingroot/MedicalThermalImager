package tw.cchi.medthimager.thermalproc;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.lang.ref.WeakReference;

import io.reactivex.disposables.Disposable;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.util.ImageUtils;

public class VisibleImageMask implements Disposable {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private WeakReference<RawThermalDump> rawThermalDumpRef;
    private Bitmap visibleBitmap;
    private volatile boolean disposed = false;

    public VisibleImageMask(RawThermalDump rawThermalDump, Bitmap visibleBitmap) {
        this.rawThermalDumpRef = new WeakReference<>(rawThermalDump);
        this.visibleBitmap = visibleBitmap;
    }

    public RawThermalDump getLinkedRawThermalDump() {
        return rawThermalDumpRef.get();
    }

    /**
     * Warning: the resulted visible light image size(340*454) is not same as
     *          flir jpg image(480*640) in flirone sdk v2.
     */
    public Bitmap getVisibleBitmap() {
        return visibleBitmap;
    }

    /**
     * Generate zoomed & aligned (with black background) visible light image.
     */
    @Nullable
    public Bitmap getAlignedVisibleBitmap() {
        if (rawThermalDumpRef.get() == null || visibleBitmap == null)
            return null;

        Mat source = new Mat();
        Utils.bitmapToMat(visibleBitmap, source);

        // Convert offsets from number of thermal pixels to image bitmap pixels
        double ratio = 0.1 * source.rows() / rawThermalDumpRef.get().getHeight();
        int imageOffsetX = (int) (rawThermalDumpRef.get().getVisibleOffsetX() * ratio);
        int imageOffsetY = (int) (rawThermalDumpRef.get().getVisibleOffsetY() * ratio);

        // Transform (shifting)
        Mat shiftedMat = new Mat(source.size(), source.type());
        Imgproc.warpAffine(
                source, shiftedMat,
                ImageUtils.getShiftTransformationMatrix(imageOffsetX, imageOffsetY), source.size());

        // Resize (zoom)
        // Mat zoomedMat = new Mat(rawThermalDump.get)
        // Imgproc.resize();

        // Create output bitmap
        Bitmap resultBitmap = Bitmap.createBitmap(
            visibleBitmap.getWidth(), visibleBitmap.getHeight(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(shiftedMat, resultBitmap);

        return resultBitmap;
    }

    @Override
    public void dispose() {
        rawThermalDumpRef = new WeakReference<>(null);
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
