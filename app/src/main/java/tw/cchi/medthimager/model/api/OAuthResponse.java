package tw.cchi.medthimager.model.api;

import com.google.gson.annotations.SerializedName;

public class OAuthResponse {

    @SerializedName("token_type") public String tokenType;
    @SerializedName("expires_in") public long expiresIn;
    @SerializedName("access_token") public String accessToken;
    @SerializedName("refresh_token") public String refreshToken;

    public OAuthResponse() {
    }

}
