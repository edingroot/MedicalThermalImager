package tw.cchi.medthimager.helper.session;

import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;

import javax.inject.Inject;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.data.network.ApiClient;
import tw.cchi.medthimager.data.network.ApiServiceGenerator;
import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.ui.auth.LoginActivity;

public class SessionManager {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    protected MvpApplication application;
    private final ArrayList<AuthEventListener> authEventListeners = new ArrayList<>();
    private Session session;

    @Inject
    public SessionManager(MvpApplication application) {
        this.application = application;
        this.session = new Session(this, application.dataManager);
    }

    public Session getSession() {
        return session;
    }

    public void addAuthEventListener(AuthEventListener listener) {
        synchronized (authEventListeners) {
            this.authEventListeners.add(listener);
        }
    }

    public void removeAuthEventListener(AuthEventListener listener) {
        synchronized (authEventListeners) {
            this.authEventListeners.remove(listener);
        }
    }

    public ApiClient createAuthedAPIClient(AccessTokens accessTokens) {
        return ApiServiceGenerator.createService(ApiClient.class, accessTokens, application);
    }


    synchronized void handleSessionActivate() {
        Log.i(TAG, "Activating session");
        notifyLogin();
    }

    synchronized void handleSessionInvalidate() {
        Log.i(TAG, "Invalidating session");
        notifyLogout();

        // Ask to login
        Intent intent = new Intent(application, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        application.startActivity(intent);
    }

    private void notifyLogin() {
        synchronized (authEventListeners) {
            for (int i = authEventListeners.size() - 1; i >= 0; i--) {
                authEventListeners.get(i).onLogin();
            }
        }
    }

    private void notifyLogout() {
        synchronized (authEventListeners) {
            for (int i = authEventListeners.size() - 1; i >= 0; i--) {
                authEventListeners.get(i).onLogout();
            }
        }
    }

    public interface AuthEventListener {
        void onLogin();

        void onLogout();
    }
}
