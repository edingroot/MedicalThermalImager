package tw.cchi.medthimager.ui.camera;

import android.graphics.Bitmap;

import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface CameraMvpPresenter<V extends CameraMvpView> extends MvpPresenter<V> {

    boolean startDeviceDiscovery();

    void checkConnectSimDevice();

    void checkReconnectSimDevice();

    void frameStreamControl(boolean start);

    void performTune();

    void onActivityStop();

    void triggerImageCapture();

    void exportAllRecordsToCSV();

    // --------------------------------- Getter / Setter / Updates ------------------------------- //

    String getCurrentPatient();

    void setCurrentPatient(String patientUUID);

    boolean isContiShotting();

    boolean isOpacityMaskAttached();

    @NewThread
    void setOpacityMask(String imagePath);

    @NewThread @BgThreadCapable
    void updateThermalImageView(Bitmap frame);

    void updateThermalSpotTemp(int thermalViewX, int thermalViewY);

    @NewThread @BgThreadCapable
    void updateThermalSpotTemp();

}
