package tw.cchi.medthimager.helper.api;

import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;

import javax.inject.Inject;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.helper.pref.PreferencesHelper;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.ui.auth.LoginActivity;

public class SessionManager {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private MvpApplication application;
    private PreferencesHelper preferencesHelper;
    private final ArrayList<AuthEventListener> authEventListeners = new ArrayList<>();

    @Inject
    public SessionManager(MvpApplication application, PreferencesHelper preferencesHelper) {
        this.application = application;
        this.preferencesHelper = preferencesHelper;
    }

    /**
     * Note: make sure {@link MvpApplication#createAuthedAPIClient(AccessTokens)} has been called.
     */
    public synchronized void activateSession(AccessTokens accessTokens, User user) {
        Log.i(TAG, "Activating session");
        preferencesHelper.setAccessTokens(accessTokens);
        preferencesHelper.setUser(user);
        preferencesHelper.setAuthenticated(true);
        notifyLogin();
    }

    public synchronized void invalidateSession() {
        Log.i(TAG, "Invalidating session");
        preferencesHelper.setAuthenticated(false);
        preferencesHelper.setAccessTokens(null);
        preferencesHelper.setUser(null);
        application.authedApiClient = null;
        notifyLogout();

        // Ask to login
        Intent intent = new Intent(application, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        application.startActivity(intent);
    }

    public void updateTokens(AccessTokens accessTokens) {
        preferencesHelper.setAccessTokens(accessTokens);
    }

    public boolean isSessionActive() {
        return preferencesHelper.isAuthenticated();
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
