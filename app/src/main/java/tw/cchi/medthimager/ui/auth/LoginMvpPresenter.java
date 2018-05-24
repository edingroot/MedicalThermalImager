package tw.cchi.medthimager.ui.auth;

import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface LoginMvpPresenter<V extends LoginMvpView> extends MvpPresenter<V> {

    void login(String email, String password);

}
