package tw.cchi.medthimager.helper.session;

import android.util.Log;

import javax.inject.Inject;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.data.DataManager;
import tw.cchi.medthimager.data.network.ApiClient;
import tw.cchi.medthimager.data.network.ApiServiceGenerator;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.model.api.AccessTokens;

public class Session {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private final SessionManager sessionManager;
    private final DataManager dataManager;

    private boolean active = false;
    private AccessTokens accessTokens;
    private User user;
    private ApiClient apiClient;

    Session(SessionManager sessionManager, DataManager dataManager) {
        this.sessionManager = sessionManager;
        this.dataManager = dataManager;
        restoreState();
    }

    public void activate(AccessTokens accessTokens, User user) {
        Log.i(TAG, "Activating session");

        apiClient = sessionManager.createAuthedAPIClient(accessTokens);

        if (apiClient == null) {
            invalidate();
        } else {
            dataManager.pref.setAccessTokens(accessTokens);
            dataManager.pref.setUser(user);
            dataManager.pref.setAuthenticated(true);
            sessionManager.handleSessionActivate();
            active = true;
        }
    }

    public void invalidate() {
        dataManager.pref.setAuthenticated(false);
        dataManager.pref.setAccessTokens(null);
        dataManager.pref.setUser(null);
        sessionManager.handleSessionInvalidate();
        apiClient = null;
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public AccessTokens getAccessTokens() {
        return accessTokens;
    }

    public void setAccessTokens(AccessTokens accessTokens) {
        this.accessTokens = accessTokens;
        dataManager.pref.setAccessTokens(accessTokens);
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        dataManager.pref.setUser(user);
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    private void restoreState() {
        this.accessTokens = dataManager.pref.getAccessTokens();
        this.apiClient = sessionManager.createAuthedAPIClient(accessTokens);

        if (apiClient == null) {
            invalidate();
        } else {
            this.active = dataManager.pref.isAuthenticated();
            this.user = dataManager.pref.getUser();
        }
    }
}
