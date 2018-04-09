package tw.cchi.medthimager.ui.camera.contishoot;

import tw.cchi.medthimager.ui.base.DialogMvpView;

public interface ContiShootMvpView extends DialogMvpView {

    void dismissDialog();

    ContiShootDialog.OnResultListener getListener();

}
