package tw.cchi.medthimager.thermalproc;

import android.content.Context;
import android.graphics.Bitmap;

import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.LoadedFrame;
import com.flir.flironesdk.RenderedImage;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.EnumSet;

import tw.cchi.medthimager.di.NewThread;

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

        new Thread(new Runnable() {
            @Override
            public void run() {
                frameProcessor = new FrameProcessor(context, VisibleImageMask.this, IMAGE_TYPES);
                frameProcessor.setImagePalette(loadedFrame.getPreviewPalette());
//                frameProcessor.setImagePalette(RenderedImage.Palette.Iron);
                frameProcessor.setEmissivity(0.98f); // human skin, water, frost
                frameProcessor.processFrame(loadedFrame);
            }
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

    public Bitmap getVisibleBitmap() {
        return visibleBitmap;
    }

//    public Bitmap getBlendedMSXBitmap() {
//        return blendedMSXBitmap;
//    }

    public interface OnFrameProcessedListener {
        void onFrameProcessed(VisibleImageMask maskInstance);
    }
}
