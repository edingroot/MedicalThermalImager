package tw.cchi.medthimager.thermalproc;

import android.content.Context;
import android.graphics.Bitmap;

import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.LoadedFrame;
import com.flir.flironesdk.RenderedImage;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.EnumSet;

import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.utils.ImageUtils;

public class VisibleImageMask implements FrameProcessor.Delegate {
    private static final EnumSet<RenderedImage.ImageType> IMAGE_TYPES = EnumSet.of(
            RenderedImage.ImageType.VisibleAlignedRGBA8888Image
//            RenderedImage.ImageType.BlendedMSXRGBA8888Image
    );
    private final RawThermalDump rawThermalDump;
    private OnFrameProcessedListener onFrameProcessedListener;
    private File frameFile;
    private LoadedFrame loadedFrame;

    private FrameProcessor frameProcessor;
    private volatile int proceedTypes = 0;
    private volatile Bitmap visibleBitmap;
//    private volatile Bitmap blendedMSXBitmap;

    /**
     * Please use {@link #openVisibleImage(RawThermalDump, String)} to get a new instance.
     */
    private VisibleImageMask(RawThermalDump rawThermalDump) {
        this.rawThermalDump = rawThermalDump;
    }

    public static VisibleImageMask openVisibleImage(RawThermalDump rawThermalDump, String imagePath) {
        VisibleImageMask mask = new VisibleImageMask(rawThermalDump);

        try {
            mask.frameFile = new File(imagePath);
            mask.loadedFrame = new LoadedFrame(mask.frameFile);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return mask;
    }

    @NewThread
    public void processFrame(final Context context, OnFrameProcessedListener onFrameProcessedListener) {
        this.onFrameProcessedListener = onFrameProcessedListener;
        this.proceedTypes = 0;

        new Thread(() -> {
            frameProcessor = new FrameProcessor(context, VisibleImageMask.this, IMAGE_TYPES);
            frameProcessor.setImagePalette(loadedFrame.getPreviewPalette());
//                frameProcessor.setImagePalette(RenderedImage.Palette.Iron);
            frameProcessor.setEmissivity(0.98f); // human skin, water, frost
            frameProcessor.processFrame(loadedFrame);
        }).start();
    }

    public RawThermalDump getLinkedRawThermalDump() {
        return rawThermalDump;
    }

    @Override
    public void onFrameProcessed(RenderedImage renderedImage) {
        if (renderedImage.imageType() == RenderedImage.ImageType.VisibleAlignedRGBA8888Image) {
            visibleBitmap = Bitmap.createBitmap(renderedImage.width(), renderedImage.height(), Bitmap.Config.ARGB_8888);
            visibleBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(renderedImage.pixelData()));

//        } else if (renderedImage.imageType() == RenderedImage.ImageType.BlendedMSXRGBA8888Image) {
//            blendedMSXBitmap = Bitmap.createBitmap(renderedImage.width(), renderedImage.height(), Bitmap.Config.ARGB_8888);
//            blendedMSXBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(renderedImage.pixelData()));
        }

        if (++proceedTypes == IMAGE_TYPES.size()) {
            // Map<RenderedImage.ImageType, RenderedImage> renderedImageMap = frameProcessor.getProcessedFrames(loadedFrame);
            onFrameProcessedListener.onFrameProcessed(this);
        }
    }

    /**
     * Warning: the resulted visible light image size(340*454) is not same as flir jpg image(480*640)
     */
    public Bitmap getVisibleBitmap() {
        return visibleBitmap;
    }

    /**
     * Generate zoomed & aligned (with black background) visible light image.
     */
    public Bitmap getAlignedVisibleBitmap() {
        if (visibleBitmap == null)
            return null;

        Mat source = new Mat();
        Utils.bitmapToMat(visibleBitmap, source);

        // Convert offsets from number of thermal pixels to image bitmap pixels
        double ratio = 0.1 * source.rows() / rawThermalDump.getHeight();
        int imageOffsetX = (int) (rawThermalDump.getVisibleOffsetX() * ratio);
        int imageOffsetY = (int) (rawThermalDump.getVisibleOffsetY() * ratio);

        // Transform (shift)
        Mat shiftedMat = new Mat(source.size(), source.type());
        Imgproc.warpAffine(source, shiftedMat,
            ImageUtils.getShiftTransFormMatrix(imageOffsetX, imageOffsetY), source.size());

        // Resize (zoom)
//        Mat zoomedMat = new Mat(rawThermalDump.get)
//        Imgproc.resize();

        // Create output bitmap
        Bitmap resultBitmap = Bitmap.createBitmap(
            visibleBitmap.getWidth(), visibleBitmap.getHeight(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(shiftedMat, resultBitmap);

        return resultBitmap;
    }

//    public Bitmap getBlendedMSXBitmap() {
//        return blendedMSXBitmap;
//    }

    public interface OnFrameProcessedListener {
        void onFrameProcessed(VisibleImageMask maskInstance);
    }
}
