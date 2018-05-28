package tw.cchi.medthimager.ui.settings;

import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.helper.api.ApiHelper;
import tw.cchi.medthimager.helper.session.Session;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.util.NetworkUtils;

public class SettingsPresenter<V extends SettingsMvpView> extends BasePresenter<V> implements SettingsMvpPresenter<V> {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject AppCompatActivity activity;
    @Inject ApiHelper apiHelper;

    private Session currentSession;

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
        currentSession = application.getSession();

        getMvpView().setAuthState(currentSession.isActive(), currentSession.getUser());
        getMvpView().setSwClearSpotsOnDisconn(preferencesHelper.getClearSpotsOnDisconnectEnabled());
        getMvpView().setSwAutoApplyVisibleOffset(preferencesHelper.getAutoApplyVisibleOffsetEnabled());

        // Refresh data asynchronously
        apiHelper.refreshUserProfile().observeOn(AndroidSchedulers.mainThread()).subscribe(success -> {
            if (isViewAttached()) {
                if (success) {
                    getMvpView().setAuthState(currentSession.isActive(), currentSession.getUser());
                } else {
                    getMvpView().showSnackBar(R.string.error_refresh_profile);
                }
            }
        });
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
                Log.i(TAG, "onLogout: got status code " + response.code());

                // Logout anyway
                // activity will be notified to finish by invalidateSession()
                currentSession.invalidate();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                call.cancel();
                t.printStackTrace();

                // Logout anyway
                currentSession.invalidate();
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

    @Override
    public void syncPatients() {
        application.getSyncService(syncService -> {
            if (!NetworkUtils.isNetworkConnected(application)) {
                if (isViewAttached())
                    getMvpView().showSnackBar(R.string.no_network_access);
            } else if (!application.getSession().isActive()) {
                if (isViewAttached())
                    getMvpView().showSnackBar(R.string.unauthenticated);
            } else {
                syncService.syncPatients();
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
