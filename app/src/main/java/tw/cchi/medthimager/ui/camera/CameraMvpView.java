package tw.cchi.medthimager.ui.camera;

import android.graphics.Bitmap;

import com.flir.flironesdk.Device;

import tw.cchi.medthimager.ui.base.MvpView;

public interface CameraMvpView extends MvpView {

    void updateForDeviceConnected();

    void updateForDeviceDisconnected();

    void updateForDeviceChargingState(Device.BatteryChargingState batteryChargingState);

    void updateForDeviceTuningState(Device.TuningState tuningState);

    void updateDeviceBatteryPercentage(byte percentage);

    void setThermalImageViewBitmap(final Bitmap frame);

    void setThermalSpotTemp(double temperature);

    void animateFlash();

    void setCameraMode();

    void setContinuousShootMode();


    int getThermalImageViewWidth();

}
