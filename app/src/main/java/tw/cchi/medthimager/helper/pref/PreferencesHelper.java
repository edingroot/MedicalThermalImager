package tw.cchi.medthimager.helper.pref;

import android.graphics.Point;
import android.support.annotation.Nullable;

import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.model.User;

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


    // ------------------------- Settings ------------------------- //

    boolean getAutoApplyVisibleOffsetEnabled();

    void setAutoApplyVisibleOffsetEnabled(boolean enabled);

    Point getDefaultVisibleOffset();

    void setDefaultVisibleOffset(Point offset);

    boolean getClearSpotsOnDisconnectEnabled();

    void setClearSpotsOnDisconnect(boolean enable);

}
