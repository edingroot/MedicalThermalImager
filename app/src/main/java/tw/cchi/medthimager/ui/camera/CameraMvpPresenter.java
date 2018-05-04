package tw.cchi.medthimager.ui.camera;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import tw.cchi.medthimager.db.Patient;
import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface CameraMvpPresenter<V extends CameraMvpView> extends MvpPresenter<V> {

    boolean startDeviceDiscovery();

    void checkAndConnectSimDevice();

    void checkReconnectSimDevice();

    void frameStreamControl(boolean start);

    void performTune();

    void onActivityStop();

    boolean triggerImageCapture();

    void startContiShooting(ContiShootParameters contiShootParameters);

    void finishContiShooting(boolean showMessageByToast);

    void exportAllRecordsToCSV();

    // --------------------------------- Getter / Setter / Updates ------------------------------- //

    Patient getCurrentPatient();

    void setCurrentPatient(String patientUUID);

    boolean isDeviceAttached();

    boolean isContiShootingMode();

    boolean isOpacityMaskAttached();

    @NewThread
    void setOpacityMask(String imagePath);

    @NewThread @BgThreadCapable
    void updateThermalImageView(Bitmap frame);

    void updateThermalSpotTemp(int thermalViewX, int thermalViewY);

    @NewThread @BgThreadCapable
    void updateThermalSpotTemp();

}
