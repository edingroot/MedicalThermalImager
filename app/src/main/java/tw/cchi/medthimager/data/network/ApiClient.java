package tw.cchi.medthimager.data.network;

import java.util.List;

import io.reactivex.Observable;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.model.api.PatientResponse;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.util.annotation.RequireAuth;

public interface ApiClient {

    @FormUrlEncoded
    @POST("auth")
    Observable<Response<AccessTokens>> getNewAccessToken(
            @Field("email") String email,
            @Field("password") String password);

    @RequireAuth
    @FormUrlEncoded
    @POST("refreshToken")
    Call<AccessTokens> getRefreshAccessToken(
            @Field("refresh_token") String refreshToken);

    @RequireAuth
    @POST("logout")
    Call<Void> logout();

    @RequireAuth
    @GET("profile")
    Observable<Response<User>> getProfile();

    @RequireAuth
    @GET("patients")
    Observable<Response<List<SSPatient>>> getAllPatients();

    @RequireAuth
    @POST("patients")
    Observable<Response<PatientResponse>> createPatient(@Body SSPatient patient);

}

/*
    @FormUrlEncoded
    @POST("/oauth/token")
    Call<AccessTokens> getNewAccessTokenOauth(
            @Field("code") String code,
            @Field("client_id") String clientId,
            @Field("client_secret") String clientSecret,
            @Field("redirect_uri") String redirectUri,
            @Field("grant_type") String grantType);

    @FormUrlEncoded
    @POST("/oauth/token")
    Call<AccessTokens> getRefreshAccessTokenOauth(
            @Field("refresh_token") String refreshToken,
            @Field("client_id") String clientId,
            @Field("client_secret") String clientSecret,
            @Field("redirect_uri") String redirectUri,
            @Field("grant_type") String grantType);
    */
