package tw.cchi.medthimager.ui.camera.contishot;

import tw.cchi.medthimager.ui.base.DialogMvpView;

public interface ContiShotMvpView extends DialogMvpView {

    void dismissDialog();

    ContiShotDialog.OnResultListener getListener();

}
