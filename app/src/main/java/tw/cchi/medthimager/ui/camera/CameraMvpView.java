package tw.cchi.medthimager.ui.camera;

import android.graphics.Bitmap;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.RenderedImage;

import io.reactivex.Observable;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.ui.base.MvpView;

public interface CameraMvpView extends MvpView {

    Observable<Object> getThermalImageViewGlobalLayouts();

    ThermalSpotsHelper createThermalSpotsHelper(RenderedImage renderedImage);

    boolean isActivityStopping();

    void setPatientStatusText(String patientName);

    void setDeviceConnected();

    void setDeviceDisconnected();

    void setDeviceChargingState(Device.BatteryChargingState batteryChargingState);

    void setDeviceTuningState(Device.TuningState tuningState);

    void setDeviceBatteryPercentage(byte percentage);

    void setSpotsControlEnabled(boolean enabled);

    void updateThermalImageView(final Bitmap frame);

    int getThermalImageViewHeight();

    void animateFlash();

    void setSingleShootMode();

    void setContinuousShootMode(int capturedCount, int totalCaptures);

    void updateContinuousShootCountdown(int value);

}
