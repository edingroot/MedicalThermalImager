package tw.cchi.medthimager.ui.dialog.updateremider;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.util.AppUtils;

public class UpdateReminderPresenter<V extends UpdateReminderMvpView> extends BasePresenter<V> implements UpdateReminderMvpPresenter<V> {

    private int newVersionCode;
    private String newVersionName;

    @Inject
    public UpdateReminderPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);

        newVersionCode = dataManager.rconfig.getLatestVersionCode();
        newVersionName = dataManager.rconfig.getLatestVersionName();

        getMvpView().setCurrentVersion(AppUtils.getVersionName(activity));
        getMvpView().setNewVersion(newVersionName);
    }

    @Override
    public void ignore() {
        dataManager.pref.setLastNotifiedVersion(newVersionCode);
        getMvpView().dismiss();
    }

    @Override
    public void update() {
        getMvpView().launchGooglePlayPage(AppUtils.getPackageName(activity));
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
