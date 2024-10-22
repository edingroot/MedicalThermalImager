package tw.cchi.medthimager.ui.camera;

import com.flir.flironesdk.RenderedImage;

import java.util.List;

import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.model.api.Tag;
import tw.cchi.medthimager.ui.base.MvpPresenter;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;
import tw.cchi.medthimager.util.annotation.NewThread;

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

    @BgThreadCapable
    void updateCurrentPatientStatusText();

    void setCurrentPatient(String patientCuid);

    List<Tag> getSelectedTags();

    void setSelectedTags(List<Tag> tags);

    boolean isDeviceAttached();

    boolean isContiShootingMode();

    boolean isOpacityMaskAttached();

    @NewThread
    void setOpacityMask(String imagePath);

    @NewThread @BgThreadCapable
    void updateThermalImageView(RenderedImage renderedImage);

}
