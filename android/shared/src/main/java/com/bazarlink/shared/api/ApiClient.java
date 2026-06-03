package com.bazarlink.shared.api;

import android.content.Context;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private final BazarLinkApi api;

    public ApiClient(Context context, String baseUrl) {
        TokenStore tokenStore = new TokenStore(context);
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    okhttp3.Request request = chain.request();
                    // Skip adding Authorization header for auth endpoints (login/register)
                    String path = request.url().encodedPath();
                    if (path.startsWith("/api/v1/auth/")) {
                        return chain.proceed(request);
                    }
                    okhttp3.Request.Builder builder = request.newBuilder()
                            .header("Accept", "application/json");
                    String token = tokenStore.accessToken();
                    if (!token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();

        api = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BazarLinkApi.class);
    }

    public BazarLinkApi api() {
        return api;
    }
}
