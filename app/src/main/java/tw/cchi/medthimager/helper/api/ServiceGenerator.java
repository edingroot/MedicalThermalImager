package tw.cchi.medthimager.helper.api;

import android.content.Context;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.helper.pref.AppPreferencesHelper;
import tw.cchi.medthimager.helper.pref.PreferencesHelper;
import tw.cchi.medthimager.model.AccessTokens;

import static tw.cchi.medthimager.Config.API_BASE_URL;

public class ServiceGenerator {
    private static OkHttpClient.Builder httpClient;
    private static Retrofit.Builder builder;

    private static AccessTokens accesTokens;

    public static <S> S createService(Class<S> serviceClass) {
        httpClient = new OkHttpClient.Builder();
        builder = new Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create());

        OkHttpClient client = httpClient.build();
        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(serviceClass);
    }

    public static <S> S createService(Class<S> serviceClass, AccessTokens accessTokens, Context context) {
        httpClient = new OkHttpClient.Builder();
        builder = new Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create());

        if (accessTokens != null) {
            accesTokens = accessTokens;
            final AccessTokens token = accessTokens;
            httpClient.addInterceptor(chain -> {
                Request original = chain.request();

                Request.Builder requestBuilder = original.newBuilder()
                        .header("Accept", "application/json")
                        .header("Content-type", "application/json")
                        .header("Authorization",
                                token.getTokenType() + " " + token.getAccessToken())
                        .method(original.method(), original.body());

                Request request = requestBuilder.build();
                return chain.proceed(request);
            });

            httpClient.authenticator((route, response) -> {
                if (responseCount(response) >= 2) {
                    // If both the original call and the call with refreshed token failed,
                    // it will probably keep failing, so don't try again.
                    return null;
                }

                // We need a new client, since we don't want to make another call using our client with access token
                APIClient tokenClient = createService(APIClient.class);
                Call<AccessTokens> call = tokenClient.getRefreshAccessToken(accesTokens.getRefreshToken());
                try {
                    retrofit2.Response<AccessTokens> tokenResponse = call.execute();
                    if (tokenResponse.code() == 200) {
                        AccessTokens newTokens = tokenResponse.body();
                        accesTokens = newTokens;

                        PreferencesHelper preferencesHelper = new AppPreferencesHelper(context, Constants.PREF_NAME);
                        preferencesHelper.setAuthenticated(true);
                        preferencesHelper.setAccessTokens(newTokens);

                        return response.request().newBuilder()
                                .header("Authorization", newTokens.getTokenType() + " " + newTokens.getAccessToken())
                                .build();
                    } else {
                        return null;
                    }
                } catch (Exception e) {
                    return null;
                }
            });
        }

        OkHttpClient client = httpClient.build();
        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(serviceClass);
    }

    private static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}