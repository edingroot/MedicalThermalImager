package tw.cchi.medthimager.ui.auth;

import tw.cchi.medthimager.ui.base.MvpView;

public interface LoginMvpView extends MvpView {

    void setCredentials(String email, String password);

    void launchCameraActivityAndFinish();

}
