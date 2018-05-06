package tw.cchi.medthimager.ui.camera;

import com.flir.flironesdk.RenderedImage;

import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface CameraMvpPresenter<V extends CameraMvpView> extends MvpPresenter<V> {

    boolean startDeviceDiscovery();

    void unregisterFlir();

    void checkAndConnectSimDevice();

    void checkReconnectSimDevice();

    void frameStreamControl(boolean start);

    void performTune();

    boolean triggerImageCapture();

    void startContiShooting(ContiShootParameters contiShootParameters);

    void finishContiShooting(boolean showMessageByToast);

    void addThermalSpot();

    void removeLastThermalSpot();

    void clearThermalSpots();

    void exportAllRecordsToCSV();

    // --------------------------------- Getter / Setter / Updates ------------------------------- //

    String getCurrentPatientUuid();

    void setCurrentPatient(String patientUUID);

    boolean isDeviceAttached();

    boolean isContiShootingMode();

    boolean isOpacityMaskAttached();

    @NewThread
    void setOpacityMask(String imagePath);

    @NewThread @BgThreadCapable
    void updateThermalImageView(RenderedImage renderedImage);

}
