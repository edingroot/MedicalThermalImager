package tw.cchi.medthimager.ui.settings;

import android.support.v7.app.AppCompatActivity;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class SettingsPresenter<V extends SettingsMvpView> extends BasePresenter<V> implements SettingsMvpPresenter<V> {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject AppCompatActivity activity;

    @Inject
    public SettingsPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
        loadSettings();
    }

    private void loadSettings() {
        // Auth status
        getMvpView().setAuthState(preferencesHelper.isAuthenticated());

        getMvpView().setSwClearSpotsOnDisconn(preferencesHelper.getClearSpotsOnDisconnectEnabled());
        getMvpView().setSwAutoApplyVisibleOffset(preferencesHelper.getAutoApplyVisibleOffsetEnabled());
    }

    @Override
    public void login() {

    }

    @Override
    public void logout() {

    }

    @Override
    public void setClearSpotsOnDisconnect(boolean enable) {
        preferencesHelper.setClearSpotsOnDisconnect(enable);
    }

    @Override
    public void setAutoSetVisibleOffset(boolean enable) {
        preferencesHelper.setAutoApplyVisibleOffsetEnabled(enable);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
