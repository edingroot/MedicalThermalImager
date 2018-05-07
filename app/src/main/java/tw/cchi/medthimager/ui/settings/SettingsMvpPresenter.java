package tw.cchi.medthimager.ui.settings;

import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface SettingsMvpPresenter<V extends SettingsMvpView> extends MvpPresenter<V> {

    void setClearSpotsOnDisconnect(boolean enable);

}
