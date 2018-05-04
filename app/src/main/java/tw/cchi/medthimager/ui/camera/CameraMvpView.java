package tw.cchi.medthimager.ui.camera;

import android.graphics.Bitmap;

import com.flir.flironesdk.Device;

import tw.cchi.medthimager.ui.base.MvpView;

public interface CameraMvpView extends MvpView {

    void setPatientStatusText(String patientName);

    void setDeviceConnected();

    void setDeviceDisconnected();

    void setDeviceChargingState(Device.BatteryChargingState batteryChargingState);

    void setDeviceTuningState(Device.TuningState tuningState);

    void setDeviceBatteryPercentage(byte percentage);

    void setThermalImageViewBitmap(final Bitmap frame);

    void setThermalSpotTemp(double temperature);

    void animateFlash();

    void setSingleShootMode();

    void setContinuousShootMode(int capturedCount, int totalCaptures);


    int getThermalImageViewWidth();

}
