package tw.cchi.medthimager.data.network;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.util.CommonUtils;

import static tw.cchi.medthimager.Config.API_BASE_URL;

public class ApiServiceGenerator {
    private static final String TAG = Config.TAGPRE + ApiServiceGenerator.class.getSimpleName();

    private static OkHttpClient.Builder httpClient;
    private static Retrofit.Builder builder;

    private static AccessTokens accessTokens;

    public static <S> S createService(Class<S> serviceClass) {
        httpClient = new OkHttpClient.Builder();
        builder = new Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(CommonUtils.getGsonInstance()));

        httpClient.addInterceptor(new HttpLoggingInterceptor().setLevel(Config.API_LOGGING_LEVEL));

        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-type", "application/json")
                    .method(original.method(), original.body())
                    .build();
            return chain.proceed(request);
        });

        OkHttpClient client = httpClient.build();
        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(serviceClass);
    }

    public static <S> S createService(Class<S> serviceClass, AccessTokens accessTokens, MvpApplication application) {
        if (accessTokens == null)
            return null;

        ApiServiceGenerator.accessTokens = accessTokens;
        final AccessTokens token = accessTokens;

        // Build the API client
        httpClient = new OkHttpClient.Builder();
        builder = new Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(CommonUtils.getGsonInstance()));

        httpClient.addInterceptor(new HttpLoggingInterceptor().setLevel(Config.API_LOGGING_LEVEL));

        // Add API authentication bearer header
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-type", "application/json")
                    .header("Authorization",
                            token.getTokenType() + " " + token.getAccessToken())
                    .method(original.method(), original.body())
                    .build();
            return chain.proceed(request);
        });

        // Handle the authentication process
        httpClient.authenticator((route, response) -> {
            if (responseCount(response) >= 2) {
                // If both the original call and the call with refreshed token failed,
                // it will probably keep failing, so don't try again.
                return null;
            }

            // We need a new client, since we don't want to make another call using our client with access token
            ApiClient tokenClient = createService(ApiClient.class);
            Call<AccessTokens> call = tokenClient.getRefreshAccessToken(ApiServiceGenerator.accessTokens.getRefreshToken());
            try {
                retrofit2.Response<AccessTokens> tokenResponse = call.execute();
                if (tokenResponse.code() == 200) {
                    AccessTokens newTokens = tokenResponse.body();
                    ApiServiceGenerator.accessTokens = newTokens;

                    application.getSession().setAccessTokens(newTokens);

                    return response.request().newBuilder()
                            .header("Authorization", newTokens.getTokenType() + " " + newTokens.getAccessToken())
                            .build();
                } else {
                    application.getSession().invalidate();
                    return null;
                }
            } catch (Exception e) {
                application.getSession().invalidate();
                return null;
            }
        });

        OkHttpClient client = httpClient.build();
        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(serviceClass);
    }

    // Response counter for the authenticator
    private static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        Log.d(TAG, "authenticator responseCount=" + result);
        return result;
    }
}
