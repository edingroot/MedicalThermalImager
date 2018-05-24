package tw.cchi.medthimager.ui.settings;

import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
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
        // Unreachable state currently
    }

    @Override
    public void logout() {
        if (application.authedApiClient == null) {
            getMvpView().showSnackBar(R.string.error_occurred);
            return;
        }

        application.authedApiClient.logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i(TAG, "logout: got status code " + response.code()); // 201 for success

                // Logout anyway
                clearCredentialsAndFinish();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                call.cancel();
                t.printStackTrace();

                // Logout anyway
                clearCredentialsAndFinish();
            }
        });
    }

    @Override
    public void setClearSpotsOnDisconnect(boolean enable) {
        preferencesHelper.setClearSpotsOnDisconnect(enable);
    }

    @Override
    public void setAutoSetVisibleOffset(boolean enable) {
        preferencesHelper.setAutoApplyVisibleOffsetEnabled(enable);
    }

    private void clearCredentialsAndFinish() {
        preferencesHelper.setAuthenticated(false);
        preferencesHelper.setAccessTokens(null);
        // TODO: clear user info
        activity.finish();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
