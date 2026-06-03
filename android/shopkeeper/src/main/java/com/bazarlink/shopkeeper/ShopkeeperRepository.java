package com.bazarlink.shopkeeper;

import android.content.Context;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.api.BazarLinkApi;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.WholesalerDashboard;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShopkeeperRepository {

    public interface Callback1<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final BazarLinkApi api;

    public ShopkeeperRepository(Context ctx, String baseUrl) {
        this.api = new ApiClient(ctx, baseUrl).api();
    }

    public void loadDashboard(Callback1<WholesalerDashboard> cb) {
        api.shopkeeperDashboard().enqueue(new Callback<WholesalerDashboard>() {
            @Override
            public void onResponse(Call<WholesalerDashboard> call, Response<WholesalerDashboard> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    cb.onError("Failed to load dashboard");
                }
            }

            @Override
            public void onFailure(Call<WholesalerDashboard> call, Throwable t) {
                cb.onError(t.getMessage() == null ? "Network error" : t.getMessage());
            }
        });
    }

    public void loadOrders(Map<String, String> filters, Callback1<Page<Order>> cb) {
        if (filters == null) filters = new HashMap<>();
        api.orders(filters).enqueue(new Callback<Page<Order>>() {
            @Override
            public void onResponse(Call<Page<Order>> call, Response<Page<Order>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    cb.onError("Failed to load orders");
                }
            }

            @Override
            public void onFailure(Call<Page<Order>> call, Throwable t) {
                cb.onError(t.getMessage() == null ? "Network error" : t.getMessage());
            }
        });
    }

    public void loadProducts(Map<String, String> filters, Callback1<Page<Product>> cb) {
        if (filters == null) filters = new HashMap<>();
        api.products(filters).enqueue(new Callback<Page<Product>>() {
            @Override
            public void onResponse(Call<Page<Product>> call, Response<Page<Product>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    cb.onError("Failed to load products");
                }
            }

            @Override
            public void onFailure(Call<Page<Product>> call, Throwable t) {
                cb.onError(t.getMessage() == null ? "Network error" : t.getMessage());
            }
        });
    }

    public void transitionOrder(long orderId, String status, Callback1<Order> cb) {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        api.transitionOrder(orderId, body).enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    cb.onError("Failed to transition order");
                }
            }

            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                cb.onError(t.getMessage() == null ? "Network error" : t.getMessage());
            }
        });
    }
}
