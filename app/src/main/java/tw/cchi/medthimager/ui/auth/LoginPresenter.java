package tw.cchi.medthimager.ui.auth;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.network.ApiClient;
import tw.cchi.medthimager.data.network.ApiServiceGenerator;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.model.api.AccessTokens;
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

        if (!getMvpView().isNetworkConnected()) {
            getMvpView().showSnackBar(R.string.no_network_access);
            return;
        }

        getMvpView().setLoggingIn(true);

        AtomicReference<AccessTokens> accessTokensRef = new AtomicReference<>();
        AtomicReference<User> userRef = new AtomicReference<>();

        // Note: Only the 2xx responses will go to onNext
        guestApiClient.getNewAccessToken(email, password)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((Response<AccessTokens> response) -> {
                    // Log.i(TAG, "[onLogin] " + call.request().method() + " " + call.request().url());
                    // Log.i(TAG, "[onLogin] AccessTokens: got status code " + response.code());

                    if (response.code() == 200) {
                        AccessTokens accessTokens = response.body();
                        accessTokensRef.set(accessTokens);

                        ApiClient authedApiClient = application.sessionManager.createAuthedAPIClient(accessTokens);
                        if (authedApiClient != null) {
                            // return authedApiClient.getProfile();
                            userRef.set(new User(1, "tmp@example.com", "temp-user"));
                            application.getSession().activate(accessTokensRef.get(), userRef.get());

                            if (isViewAttached()) {
                                getMvpView().startCameraActivityAndFinish();
                            }
                        } else {
                            throw new Error();
                        }
                    } else {
                        throw new Error();
                    }
                });
//        });.flatMap((Response<User> response) -> {
//            if (response.code() == 200) {
//                userRef.set(response.body());
//                return Observable.just(true);
//            } else {
//                throw new Error();
//            }
//        }).subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(
//                o -> {},
//                e -> {
//                    e.printStackTrace();
//
//                    if (isViewAttached()) {
//                        getMvpView().setLoggingIn(false);
//                        if (e instanceof IOException)
//                            getMvpView().showSnackBar(R.string.login_failed_comm_err);
//                        else
//                            getMvpView().showSnackBar(R.string.login_failed);
//                    }
//                },
//                () -> {
//                    application.getSession().activate(accessTokensRef.get(), userRef.get());
//
//                    if (isViewAttached()) {
//                        getMvpView().startCameraActivityAndFinish();
//                    }
//                }
//            );
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
