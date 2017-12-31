package tw.cchi.flironedemo1.thermalproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Toast;

import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.LoadedFrame;
import com.flir.flironesdk.RenderedImage;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Map;

public class VisibleImageMask implements FrameProcessor.Delegate {
    private final RawThermalDump rawThermalDump;
    private BitmapUpdateListener bitmapUpdateListener;
    private File frameFile;
    private LoadedFrame loadedFrame;

    private FrameProcessor frameProcessor;
    private volatile Bitmap bitmap;

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

    public void processFrame(final Context context, BitmapUpdateListener bitmapUpdateListener) {
        this.bitmapUpdateListener = bitmapUpdateListener;

        new Thread(new Runnable() {
            @Override
            public void run() {
                frameProcessor = new FrameProcessor(
                        context, VisibleImageMask.this,
                        EnumSet.of(
//                                loadedFrame.getPreviewImageType(),
                                RenderedImage.ImageType.VisibleAlignedRGBA8888Image
                        )
                );
                 frameProcessor.setImagePalette(loadedFrame.getPreviewPalette());
//                frameProcessor.setImagePalette(RenderedImage.Palette.Iron);
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
            bitmap = Bitmap.createBitmap(
                    renderedImage.width(), renderedImage.height(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(renderedImage.pixelData()));
            bitmapUpdateListener.onBitmapUpdate(this);
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    //    public void onAllFramesProcessed() {
//        Map<RenderedImage.ImageType, RenderedImage> renderedImageMap = frameProcessor.getProcessedFrames(loadedFrame);
//        RenderedImage msxRenderedImage = renderedImageMap.get(RenderedImage.ImageType.BlendedMSXRGBA8888Image);
//    }

    public interface BitmapUpdateListener {
        void onBitmapUpdate(VisibleImageMask maskInstance);
    }
}
