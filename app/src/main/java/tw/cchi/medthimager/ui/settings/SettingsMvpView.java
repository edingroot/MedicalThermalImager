package tw.cchi.medthimager.ui.settings;

import tw.cchi.medthimager.ui.base.MvpView;

public interface SettingsMvpView extends MvpView {

    void setSwClearSpotsOnDisconn(boolean checked);

    void setSwAutoApplyVisibleOffset(boolean checked);

}
