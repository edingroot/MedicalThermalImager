package tw.cchi.medthimager.data.pref;

import android.graphics.Point;
import android.support.annotation.Nullable;

import java.util.Date;

import tw.cchi.medthimager.model.api.AccessTokens;
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

    Date getLastSyncPatients();

    void setLastSyncPatients(Date datetime);

    Date getLastSyncThImages();

    void setLastSyncThImages(Date datetime);

    int getSyncPatientConflictCount();

    void setSyncPatientConflictCount(int count);


    // ------------------------- Settings ------------------------- //

    boolean getAutoApplyVisibleOffsetEnabled();

    void setAutoApplyVisibleOffsetEnabled(boolean enabled);

    Point getDefaultVisibleOffset();

    void setDefaultVisibleOffset(Point offset);

    boolean getClearSpotsOnDisconnectEnabled();

    void setClearSpotsOnDisconnect(boolean enable);

}
