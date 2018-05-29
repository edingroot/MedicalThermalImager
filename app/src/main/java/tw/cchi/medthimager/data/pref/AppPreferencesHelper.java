package tw.cchi.medthimager.data.pref;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.support.annotation.Nullable;

import com.google.gson.Gson;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.di.ApplicationContext;
import tw.cchi.medthimager.di.PreferenceInfo;
import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.model.User;

@Singleton
public class AppPreferencesHelper implements PreferencesHelper {
    // Authentication
    private static final String KEY_AUTHENTICATED = "KEY_AUTHENTICATED";
    private static final String KEY_ACCESS_TOKENS = "KEY_ACCESS_TOKENS";
    private static final String KEY_USER = "KEY_USER";

    // States
    private static final String KEY_LAST_SYNCED_PATIENTS = "KEY_LAST_SYNCED_PATIENTS";
    private static final String KEY_SELECTED_PATIENT_CUID = "KEY_SELECTED_PATIENT_CUID";
    private static final String KEY_SYNC_PATIENT_CONFLICTS = "KEY_SYNC_PATIENT_CONFLICTS";

    // Settings
    private static final String KEY_DEFAULT_VISIBLE_OFFSET_ENABLED = "KEY_DEFAULT_VISIBLE_OFFSET_ENABLED";
    private static final String KEY_DEFAULT_VISIBLE_OFFSET_X = "KEY_DEFAULT_VISIBLE_OFFSET_X";
    private static final String KEY_DEFAULT_VISIBLE_OFFSET_Y = "KEY_DEFAULT_VISIBLE_OFFSET_Y";
    private static final String KEY_CLEAR_SPOTS_ON_DISCONNECT = "KEY_CLEAR_SPOTS_ON_DISCONNECT";

    private final SharedPreferences mPrefs;
    private final Gson gson = new Gson();

    @Inject
    public AppPreferencesHelper(@ApplicationContext Context context,
                                @PreferenceInfo String prefFileName) {
        mPrefs = context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE);
    }

    @Override
    public boolean isAuthenticated() {
        return mPrefs.getBoolean(KEY_AUTHENTICATED, false);
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        mPrefs.edit().putBoolean(KEY_AUTHENTICATED, authenticated).apply();
    }

    @Nullable
    @Override
    public AccessTokens getAccessTokens() {
        String json = mPrefs.getString(KEY_ACCESS_TOKENS, null);
        return json == null ? null : gson.fromJson(json, AccessTokens.class);
    }

    @Override
    public void setAccessTokens(@Nullable AccessTokens accessTokens) {
        mPrefs.edit().putString(KEY_ACCESS_TOKENS,
                accessTokens == null ? null : gson.toJson(accessTokens)).apply();
    }

    @Nullable
    @Override
    public User getUser() {
        String json = mPrefs.getString(KEY_USER, null);
        return json == null ? null : gson.fromJson(json, User.class);
    }

    @Override
    public void setUser(@Nullable User user) {
        mPrefs.edit().putString(KEY_USER, user == null ? null : gson.toJson(user)).apply();
    }

    @Nullable
    @Override
    public String getSelectedPatientCuid() {
        return mPrefs.getString(KEY_SELECTED_PATIENT_CUID, null);
    }

    @Override
    public void setSelectedPatientCuid(String selectedPatientCuid) {
        mPrefs.edit().putString(KEY_SELECTED_PATIENT_CUID, selectedPatientCuid).apply();
    }

    @Override
    public Date getLastSyncPatients() {
        long timestamp = mPrefs.getLong(KEY_LAST_SYNCED_PATIENTS, 0);
        return timestamp == 0 ? new Date() : new Date(timestamp);
    }

    @Override
    public void setLastSyncPatients(Date datetime) {
        mPrefs.edit().putLong(KEY_LAST_SYNCED_PATIENTS, datetime.getTime()).apply();
    }

    @Override
    public int getSyncPatientConflictCount() {
        return mPrefs.getInt(KEY_SYNC_PATIENT_CONFLICTS, 0);
    }

    @Override
    public void setSyncPatientConflictCount(int count) {
        mPrefs.edit().putInt(KEY_SYNC_PATIENT_CONFLICTS, count).apply();
    }

    @Override
    public boolean getAutoApplyVisibleOffsetEnabled() {
        return mPrefs.getBoolean(KEY_DEFAULT_VISIBLE_OFFSET_ENABLED, false);
    }

    @Override
    public void setAutoApplyVisibleOffsetEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_DEFAULT_VISIBLE_OFFSET_ENABLED, enabled).apply();
    }


    @Override
    public Point getDefaultVisibleOffset() {
        return new Point(
                mPrefs.getInt(KEY_DEFAULT_VISIBLE_OFFSET_X, Config.INIT_DEFAULT_VISIBLE_OFFSET_X),
                mPrefs.getInt(KEY_DEFAULT_VISIBLE_OFFSET_Y, Config.INIT_DEFAULT_VISIBLE_OFFSET_Y));
    }

    @Override
    public void setDefaultVisibleOffset(Point offset) {
        mPrefs.edit()
                .putInt(KEY_DEFAULT_VISIBLE_OFFSET_X, offset.x)
                .putInt(KEY_DEFAULT_VISIBLE_OFFSET_Y, offset.y)
                .apply();
    }

    @Override
    public boolean getClearSpotsOnDisconnectEnabled() {
        return mPrefs.getBoolean(KEY_CLEAR_SPOTS_ON_DISCONNECT, false);
    }

    @Override
    public void setClearSpotsOnDisconnect(boolean enable) {
        mPrefs.edit().putBoolean(KEY_CLEAR_SPOTS_ON_DISCONNECT, enable).apply();
    }

}
