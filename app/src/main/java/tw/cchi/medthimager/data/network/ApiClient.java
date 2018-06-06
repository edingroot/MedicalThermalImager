package tw.cchi.medthimager.data.network;

import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import tw.cchi.medthimager.model.api.User;
import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.model.api.PatientCreateRequest;
import tw.cchi.medthimager.model.api.PatientResponse;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.model.api.ThImage;
import tw.cchi.medthimager.model.api.ThImageResponse;
import tw.cchi.medthimager.util.annotation.AuthRequired;

/**
 * Retrofit interface usage ref: https://www.jianshu.com/p/acfefb0a204f
 */
public interface ApiClient {

    @FormUrlEncoded
    @POST("auth")
    Observable<Response<AccessTokens>> getNewAccessToken(
            @Field("email") String email,
            @Field("password") String password);

    @AuthRequired
    @FormUrlEncoded
    @POST("refreshToken")
    Call<AccessTokens> getRefreshAccessToken(
            @Field("refresh_token") String refreshToken);

    @AuthRequired
    @POST("logout")
    Call<Void> logout();

    @AuthRequired
    @GET("profile")
    Observable<Response<User>> getProfile();


    @AuthRequired
    @GET("patients")
    Observable<Response<List<SSPatient>>> getAllPatients();

    @AuthRequired
    @POST("patients")
    Observable<Response<PatientResponse>> createPatient(@Body PatientCreateRequest patientCreateRequest);


    @AuthRequired
    @Multipart
    @POST("thimages")
    Observable<Response<ThImageResponse>> uploadThImage(
            @Part("metadata") ThImage thImage,
            @PartMap() Map<String, RequestBody> files);

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
