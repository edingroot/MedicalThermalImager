package tw.cchi.medthimager.ui.camera.contishoot;

import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface ContiShootMvpPresenter<V extends ContiShootMvpView> extends MvpPresenter<V> {

    boolean parseResult(String interval, String captureCount);

}
