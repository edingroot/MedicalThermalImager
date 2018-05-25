package tw.cchi.medthimager.helper.session;

import android.util.Log;

import java.lang.ref.WeakReference;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.helper.pref.PreferencesHelper;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.model.api.AccessTokens;

public class Session {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private final WeakReference<SessionManager> sessionManagerRef;
    private PreferencesHelper preferencesHelper;

    private boolean active = false;
    private AccessTokens accessTokens;
    private User user;

    Session(SessionManager sessionManager, PreferencesHelper preferencesHelper) {
        this.sessionManagerRef = new WeakReference<>(sessionManager);
        this.preferencesHelper = preferencesHelper;
        restoreState();
    }

    /**
     * Note: make sure {@link MvpApplication#createAuthedAPIClient(AccessTokens)} has been called.
     */
    public void activate(AccessTokens accessTokens, User user) {
        if (sessionManagerRef.get() == null)
            return;

        Log.i(TAG, "Activating session");
        preferencesHelper.setAccessTokens(accessTokens);
        preferencesHelper.setUser(user);
        preferencesHelper.setAuthenticated(true);
        sessionManagerRef.get().handleSessionActivate();
        active = true;
    }

    public void invalidate() {
        if (sessionManagerRef.get() == null)
            return;

        preferencesHelper.setAuthenticated(false);
        preferencesHelper.setAccessTokens(null);
        preferencesHelper.setUser(null);
        sessionManagerRef.get().handleSessionInvalidate();
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
        preferencesHelper.setAccessTokens(accessTokens);
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        preferencesHelper.setUser(user);
    }

    private void restoreState() {
        this.active = preferencesHelper.isAuthenticated();
        this.accessTokens = preferencesHelper.getAccessTokens();
        this.user = preferencesHelper.getUser();
    }
}
