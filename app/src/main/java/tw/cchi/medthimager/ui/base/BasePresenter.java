// Ref: https://github.com/MindorksOpenSource/android-mvp-architecture
package tw.cchi.medthimager.ui.base;

import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.data.DataManager;
import tw.cchi.medthimager.ui.dialog.updateremider.UpdateReminderDialog;
import tw.cchi.medthimager.ui.dialog.updateremider.UpdateReminderPresenter;
import tw.cchi.medthimager.util.AppUtils;

/**
 * Base class that implements the Presenter interface and provides a base implementation for
 * onAttach() and onDetach(). It also handles keeping a reference to the mvpView that
 * can be accessed from the children classes by calling getMvpView().
 */
public class BasePresenter<V extends MvpView> implements MvpPresenter<V> {
    private V mMvpView;

    protected final CompositeDisposable disposables;
    protected Handler mainLooperHandler;

    @Inject protected MvpApplication application;
    @Inject protected AppCompatActivity activity;
    @Inject protected DataManager dataManager;

    @Inject
    public BasePresenter(CompositeDisposable compositeDisposable) {
        this.disposables = compositeDisposable;
        this.mainLooperHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onAttach(V mvpView) {
        this.mMvpView = mvpView;

        if (getClass() != UpdateReminderPresenter.class)
            checkNewVersion();
    }

    @Override
    public void onDetach() {
        disposables.dispose();
        mMvpView = null;
    }

    @Override
    public boolean isViewAttached() {
        return mMvpView != null;
    }

    public V getMvpView() {
        return mMvpView;
    }

    public void checkViewAttached() {
        if (!isViewAttached()) throw new MvpViewNotAttachedException();
    }

    public CompositeDisposable getCompositeDisposable() {
        return disposables;
    }

    private void checkNewVersion() {
        int currentVersion = AppUtils.getVersionCode(application);
        int lastNotifiedVersion = dataManager.pref.getLastNotifiedVersion();

        if (lastNotifiedVersion < currentVersion) {
            lastNotifiedVersion = currentVersion;
            dataManager.pref.setLastNotifiedVersion(lastNotifiedVersion);
        }

        if (lastNotifiedVersion < dataManager.rconfig.getLatestVersionCode()) {
            UpdateReminderDialog dialog = UpdateReminderDialog.newInstance();
            dialog.show(activity.getSupportFragmentManager());
        }
    }

    public static class MvpViewNotAttachedException extends RuntimeException {
        public MvpViewNotAttachedException() {
            super("Please call Presenter.onAttach(MvpView) before" +
                    " requesting data to the Presenter");
        }
    }
}
