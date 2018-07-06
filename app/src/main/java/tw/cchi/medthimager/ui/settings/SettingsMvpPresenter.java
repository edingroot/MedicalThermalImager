package tw.cchi.medthimager.ui.settings;

import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface SettingsMvpPresenter<V extends SettingsMvpView> extends MvpPresenter<V> {

    void login();

    void logout();

    void setClearSpotsOnDisconnect(boolean enable);

    void setAutoSetVisibleOffset(boolean enable);

    void syncPatients();

    void syncThImages();

    void onSyncPatientsDone();

    void onSyncThImagesDone();

}
