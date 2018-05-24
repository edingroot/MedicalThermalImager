package tw.cchi.medthimager.ui.auth;

import android.util.Log;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.helper.api.ApiClient;
import tw.cchi.medthimager.helper.api.ApiServiceGenerator;
import tw.cchi.medthimager.model.AccessTokens;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class LoginPresenter<V extends LoginMvpView> extends BasePresenter<V> implements LoginMvpPresenter<V> {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private ApiClient guestApiClient;

    @Inject
    public LoginPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
        guestApiClient = ApiServiceGenerator.createService(ApiClient.class);

        getMvpView().setCredentials(Config.PREFILLED_LOGIN_EMAIL, Config.PREFILLED_LOGIN_PASSWORD);
    }

    @Override
    public void login(String email, String password) {
        if (email.isEmpty() || password.isEmpty())
            return;

        guestApiClient.getNewAccessToken(email, password).enqueue(new Callback<AccessTokens>() {
            @Override
            public void onResponse(Call<AccessTokens> call, Response<AccessTokens> response) {
                Log.i(TAG, "login: " + call.request().method() + " " + call.request().url());

                if (response.code() == 200) {
                    AccessTokens accessTokens = response.body();
                    preferencesHelper.setAccessTokens(accessTokens);
                    // TODO: fetch user info
                    preferencesHelper.setAuthenticated(true);
                    if (application.createAuthedAPIClient()) {
                        if (isViewAttached())
                            getMvpView().launchCameraActivityAndFinish();
                        return;
                    }
                }

                // If fails
                if (isViewAttached())
                    getMvpView().showSnackBar(R.string.login_failed);
            }

            @Override
            public void onFailure(Call<AccessTokens> call, Throwable t) {
                call.cancel();
                t.printStackTrace();

                if (isViewAttached())
                    getMvpView().showSnackBar(R.string.login_failed_comm_err);
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
