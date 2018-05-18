package tw.cchi.medthimager.thermalproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.util.Log;

import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.LoadedFrame;
import com.flir.flironesdk.RenderedImage;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.di.NewThread;

public class VisibleImageExtractor implements FrameProcessor.Delegate {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private static final EnumSet<RenderedImage.ImageType> IMAGE_TYPES = EnumSet.of(
            RenderedImage.ImageType.VisibleAlignedRGBA8888Image
            // RenderedImage.ImageType.BlendedMSXRGBA8888Image
    );
    private OnFrameProcessedListener onFrameProcessedListener;

    private FrameProcessor frameProcessor;
    private final ReentrantLock frameProcessorLock = new ReentrantLock();
    private final AtomicInteger proceedTypeCount = new AtomicInteger(0);
    // private volatile Bitmap blendedMSXBitmap;

    public VisibleImageExtractor(Context context) {
        frameProcessor = new FrameProcessor(context, VisibleImageExtractor.this, IMAGE_TYPES);
        // frameProcessor.setImagePalette(RenderedImage.Palette.Iron);
        frameProcessor.setEmissivity(0.98f); // human skin, water, frost
    }

    @NewThread
    public void extractImage(String flirImagePath, OnFrameProcessedListener onFrameProcessedListener) {
        new Thread(() -> {
            Log.d(TAG, "processFrame");
            LoadedFrame loadedFrame;
            try {
                loadedFrame = new LoadedFrame(new File(flirImagePath));
            } catch (Exception e) {
                e.printStackTrace();
                onFrameProcessedListener.onFrameProcessed(null);
                return;
            }

            frameProcessorLock.lock();
            this.onFrameProcessedListener = onFrameProcessedListener;
            this.proceedTypeCount.set(0);

            Log.d(TAG, "invoke: frameProcessor.processFrame");
            frameProcessor.processFrame(loadedFrame);
        }).start();
    }

    @Override
    public void onFrameProcessed(RenderedImage renderedImage) {
        Log.d(TAG, "onFrameProcessed: " + renderedImage.imageType());

        if (renderedImage.imageType() == RenderedImage.ImageType.VisibleAlignedRGBA8888Image) {
            Bitmap visibleBitmap = Bitmap.createBitmap(renderedImage.width(), renderedImage.height(), Bitmap.Config.ARGB_8888);
            visibleBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(renderedImage.pixelData()));
            onFrameProcessedListener.onFrameProcessed(visibleBitmap);
        }
        // } else if (renderedImage.imageType() == RenderedImage.ImageType.BlendedMSXRGBA8888Image) {
        //     blendedMSXBitmap = Bitmap.createBitmap(renderedImage.width(), renderedImage.height(), Bitmap.Config.ARGB_8888);
        //     blendedMSXBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(renderedImage.pixelData()));

        if (proceedTypeCount.addAndGet(1) == IMAGE_TYPES.size()) {
            // Map<RenderedImage.ImageType, RenderedImage> renderedImageMap = frameProcessor.getProcessedFrames(loadedFrame);
            frameProcessorLock.unlock();
        }
    }

    /* public Bitmap getBlendedMSXBitmap() {
        return blendedMSXBitmap;
    } */


    public interface OnFrameProcessedListener {
        /**
         * @param visibleImage null if error occurred
         */
        void onFrameProcessed(@Nullable Bitmap visibleImage);
    }
}
