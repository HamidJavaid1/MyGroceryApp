package com.bazarlink.shared.api;

import com.bazarlink.shared.models.*;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface BazarLinkApi {
    @POST("api/v1/auth/login/")
    Call<AuthResponse> login(@Body Map<String, String> body);

    @POST("api/v1/auth/register/")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("api/v1/auth/refresh/")
    Call<Map<String, String>> refreshToken(@Body Map<String, String> body);

    @POST("api/v1/users/fcm-token/")
    Call<Void> updateFcmToken(@Body Map<String, String> body);

    @GET("api/v1/users/me/")
    Call<User> me();

    @Multipart
    @PATCH("api/v1/users/me/")
    Call<User> updateMeAvatar(@Part MultipartBody.Part avatar);

    @GET("api/v1/categories/")
    Call<Page<Category>> categories();

    @GET("api/v1/products/")
    Call<Page<Product>> products(@QueryMap Map<String, String> filters);

    @POST("api/v1/products/")
    Call<Product> createProduct(@Body Product body);

    @PATCH("api/v1/products/{id}/")
    Call<Product> updateProduct(@Path("id") long productId, @Body Map<String, String> body);

    @GET("api/v1/shops/")
    Call<Page<Shop>> listShops(@QueryMap Map<String, String> filters);

    @GET("api/v1/shops/my-shop/")
    Call<Shop> myShop();

    @GET("api/v1/shops/nearby/")
    Call<List<Shop>> nearbyShops(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("radius_km") double radiusKm,
            @Query("kind") String kind
    );

    @GET("api/v1/analytics/wholesaler_dashboard/")
    Call<WholesalerDashboard> wholesalerDashboard();

    @GET("api/v1/analytics/shopkeeper_dashboard/")
    Call<WholesalerDashboard> shopkeeperDashboard();

    @GET("api/v1/payments/methods/")
    Call<PaymentMethodsResponse> paymentMethods();

    @POST("api/v1/payments/initiate/")
    Call<Payment> initiatePayment(@Body PaymentInitRequest body);

    @POST("api/v1/payments/{id}/confirm/")
    Call<Payment> confirmPayment(@Path("id") String paymentId, @Body PaymentConfirmRequest body);

    @POST("api/v1/orders/")
    Call<Order> createOrder(@Body Order body);

    @GET("api/v1/orders/")
    Call<Page<Order>> orders(@QueryMap Map<String, String> filters);

    @POST("api/v1/orders/{id}/transition/")
    Call<Order> transitionOrder(@Path("id") long orderId, @Body Map<String, String> body);

    @POST("api/v1/bulk-requests/")
    Call<BulkRequest> createBulkRequest(@Body BulkRequest body);

    @POST("api/v1/bulk-requests/{id}/dispatch/")
    Call<BulkRequest> dispatchBulkRequest(@Path("id") long bulkRequestId);

    @GET("api/v1/bulk-requests/")
    Call<Page<BulkRequest>> bulkRequests();

    @POST("api/v1/quotations/")
    Call<Quotation> createQuotation(@Body Quotation body);

    @GET("api/v1/quotations/")
    Call<Page<Quotation>> quotations();

    @POST("api/v1/quotations/{id}/accept/")
    Call<Quotation> acceptQuotation(@Path("id") long quotationId);

    @GET("api/v1/notifications/")
    Call<Page<NotificationItem>> notifications();

    @Multipart
    @POST("api/v1/products/")
    Call<Product> uploadProduct(@Part MultipartBody.Part image);
}
