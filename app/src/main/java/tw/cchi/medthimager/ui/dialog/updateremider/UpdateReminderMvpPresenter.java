package tw.cchi.medthimager.ui.dialog.updateremider;

import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface UpdateReminderMvpPresenter<V extends UpdateReminderMvpView> extends MvpPresenter<V> {

    void ignore();

    void update();

}