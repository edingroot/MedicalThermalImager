package tw.cchi.medthimager.ui.settings;

import tw.cchi.medthimager.ui.base.MvpPresenter;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;

public interface SettingsMvpPresenter<V extends SettingsMvpView> extends MvpPresenter<V> {

    void login();

    void logout();

    void setClearSpotsOnDisconnect(boolean enable);

    void setAutoSetVisibleOffset(boolean enable);

    void syncPatients();

    void syncThImages();

    @BgThreadCapable
    void onSyncPatientsDone();

    @BgThreadCapable
    void onSyncThImagesDone();

}
