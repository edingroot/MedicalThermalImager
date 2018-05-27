package tw.cchi.medthimager.ui.camera;

import com.flir.flironesdk.RenderedImage;

import tw.cchi.medthimager.util.annotation.BgThreadCapable;
import tw.cchi.medthimager.util.annotation.NewThread;
import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface CameraMvpPresenter<V extends CameraMvpView> extends MvpPresenter<V> {

    void loadSettings();

    boolean startDeviceDiscovery();

    void unregisterFlir();

    void checkAndConnectSimDevice();

    void checkReconnectSimDevice();

    void frameStreamControl(boolean start);

    void performTune();

    boolean triggerImageCapture();

    void startContiShooting(ContiShootParameters contiShootParameters);

    @BgThreadCapable
    void finishContiShooting(boolean success, boolean showMessageByDialog);

    void addThermalSpot();

    void removeLastThermalSpot();

    void clearThermalSpots();

    void exportAllRecordsToCSV();

    // --------------------------------- Getter / Setter / Updates ------------------------------- //

    String getCurrentPatientCuid();

    String getCurrentPatientName();

    void setCurrentPatient(String patientCuid);

    boolean isDeviceAttached();

    boolean isContiShootingMode();

    boolean isOpacityMaskAttached();

    @NewThread
    void setOpacityMask(String imagePath);

    @NewThread @BgThreadCapable
    void updateThermalImageView(RenderedImage renderedImage);

}
