package tw.cchi.medthimager.helper;

import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;

import javax.inject.Inject;

public class FlirFrameProcessorDelegate implements FrameProcessor.Delegate {

    private FrameProcessor.Delegate listener;

    @Inject
    public FlirFrameProcessorDelegate() {
    }

    public void setListener(FrameProcessor.Delegate listener) {
        this.listener = listener;
    }


    @Override
    public void onFrameProcessed(RenderedImage renderedImage) {
        if (listener != null)
            listener.onFrameProcessed(renderedImage);
    }
}
