package com.bazarlink.customer;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 28, 28, 28);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        BottomNavigationView nav = new BottomNavigationView(this);
        nav.getMenu().add(0, 1, 0, "Home").setIcon(android.R.drawable.ic_menu_view);
        nav.getMenu().add(0, 2, 1, "Products").setIcon(android.R.drawable.ic_menu_search);
        nav.getMenu().add(0, 3, 2, "Map").setIcon(android.R.drawable.ic_dialog_map);
        nav.getMenu().add(0, 4, 3, "Cart").setIcon(android.R.drawable.ic_menu_add);
        nav.getMenu().add(0, 5, 4, "Profile").setIcon(android.R.drawable.ic_menu_myplaces);
        nav.setOnItemSelectedListener(item -> {
            render(item.getTitle().toString());
            return true;
        });
        root.addView(nav);
        setContentView(root);
        render("Home");
    }

    private void render(String screen) {
        content.removeAllViews();
        title(screen);
        if ("Home".equals(screen)) {
            card("Fresh banners", "Nearby deals, featured products, and category shortcuts load from /api/v1/products/.");
            card("Bulk buy", "Create wholesale-sized requests and compare quotations.");
        } else if ("Products".equals(screen)) {
            card("Filters", "Category, price, rating, search, reviews, and product detail use Retrofit-backed paginated APIs.");
            card("Skeleton loading", "Lists show structured loading rows while network and Room cache resolve.");
        } else if ("Map".equals(screen)) {
            card("Nearby shops", "Google Maps consumes the PostGIS /shops/nearby endpoint.");
        } else if ("Cart".equals(screen)) {
            card("Checkout", "Address, payment method, order items, and live order timeline connect to /orders/.");
        } else {
            card("Account", "Firebase email, Google Sign-In, Phone OTP, notification center, and order history.");
        }
    }

    private void title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(28);
        view.setGravity(Gravity.START);
        content.addView(view);
    }

    private void card(String title, String body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(18);
        TextView p = new TextView(this);
        p.setText(body);
        p.setTextSize(14);
        box.addView(h);
        box.addView(p);
        card.addView(box);
        content.addView(card);
    }
}
