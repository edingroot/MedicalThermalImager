package tw.cchi.medthimager.ui.camera.contishot;

import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface ContiShotMvpPresenter<V extends ContiShotMvpView> extends MvpPresenter<V> {

    boolean parseResult(String interval, String times);

}
