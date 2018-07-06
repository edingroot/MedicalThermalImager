package tw.cchi.medthimager.ui.settings;

import android.support.annotation.Nullable;

import tw.cchi.medthimager.model.api.User;
import tw.cchi.medthimager.ui.base.MvpView;

public interface SettingsMvpView extends MvpView {

    void setAuthState(boolean authenticated, @Nullable User user);

    void setSwClearSpotsOnDisconn(boolean checked);

    void setSwAutoApplyVisibleOffset(boolean checked);

    void setSyncPatientsStatus(boolean syncing, String lastSynced);

    void setSyncThImagesStatus(boolean syncing, String lastSynced);

}
