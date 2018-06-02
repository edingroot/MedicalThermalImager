package tw.cchi.medthimager.ui.base;

public interface DialogMvpView extends MvpView {

    void dismiss(String tag);

    BaseActivity getBaseActivity();

}
