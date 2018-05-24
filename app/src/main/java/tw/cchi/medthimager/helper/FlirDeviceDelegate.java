package tw.cchi.medthimager.helper;

import com.flir.flironesdk.Device;

import javax.inject.Inject;

public class FlirDeviceDelegate implements Device.Delegate {

    private Device.Delegate listener;

    @Inject
    public FlirDeviceDelegate() {
    }

    public void setListener(Device.Delegate listener) {
        this.listener = listener;
    }

    @Override
    public void onTuningStateChanged(Device.TuningState tuningState) {
        if (listener != null)
            listener.onTuningStateChanged(tuningState);
    }

    @Override
    public void onAutomaticTuningChanged(boolean b) {
        if (listener != null)
            listener.onAutomaticTuningChanged(b);
    }

    @Override
    public void onDeviceConnected(Device device) {
        if (listener != null)
            listener.onDeviceConnected(device);
    }

    @Override
    public void onDeviceDisconnected(Device device) {
        if (listener != null)
            listener.onDeviceDisconnected(device);
    }
}
