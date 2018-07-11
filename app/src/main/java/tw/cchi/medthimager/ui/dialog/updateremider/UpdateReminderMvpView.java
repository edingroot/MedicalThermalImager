package tw.cchi.medthimager.ui.dialog.updateremider;

import tw.cchi.medthimager.ui.base.DialogMvpView;

public interface UpdateReminderMvpView extends DialogMvpView {

    void setCurrentVersion(String versionName);

    void setNewVersion(String versionName);

    void launchGooglePlayPage(String packageName);

    void dismiss();

}
