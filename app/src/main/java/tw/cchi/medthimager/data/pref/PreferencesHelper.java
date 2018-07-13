package tw.cchi.medthimager.data.pref;

import android.graphics.Point;
import android.support.annotation.Nullable;

import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.model.api.Tag;
import tw.cchi.medthimager.model.api.User;

public interface PreferencesHelper {

    // Authentication api should only be accessed by class helper.session.Session
    // ------------------------- Authentication ------------------------- //

    boolean isAuthenticated();

    void setAuthenticated(boolean authenticated);

    @Nullable
    AccessTokens getAccessTokens();

    void setAccessTokens(@Nullable AccessTokens accessTokens);

    @Nullable
    User getUser();

    void setUser(@Nullable User user);

    // ------------------------- States ------------------------- //

    @Nullable
    String getSelectedPatientCuid();

    void setSelectedPatientCuid(String selectedPatientCuid);

    @Nullable
    Date getLastSyncPatients();

    void setLastSyncPatients(Date datetime);

    @Nullable
    Date getLastSyncThImages();

    void setLastSyncThImages(Date datetime);

    int getLastNotifiedVersion();

    void setLastNotifiedVersion(int versionCode);

    int getSyncPatientConflictCount();

    void setSyncPatientConflictCount(int count);

    Set<String> getSelectedTags();

    void setSelectedTags(Set<String> tagUuids);


    // ------------------------- Settings ------------------------- //

    boolean getAutoApplyVisibleOffsetEnabled();

    void setAutoApplyVisibleOffsetEnabled(boolean enabled);

    Point getDefaultVisibleOffset();

    void setDefaultVisibleOffset(Point offset);

    boolean getClearSpotsOnDisconnectEnabled();

    void setClearSpotsOnDisconnect(boolean enable);

    // -------------------------- Caches -------------------------- //

    boolean isTagsCacheEmpty();

    HashMap<String, Tag> getCachedTags();

    void setCachedTags(Set<Tag> tags);

}
