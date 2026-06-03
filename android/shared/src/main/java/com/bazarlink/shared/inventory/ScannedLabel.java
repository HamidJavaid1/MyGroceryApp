package com.bazarlink.shared.inventory;

import android.content.Intent;

import androidx.annotation.Nullable;

/** Fields extracted from a product label via ML Kit text recognition. */
public class ScannedLabel {
    @Nullable public String name;
    @Nullable public String unit;
    @Nullable public String price;
    @Nullable public String stock;

    public static final String EXTRA_NAME = "scanned_name";
    public static final String EXTRA_UNIT = "scanned_unit";
    public static final String EXTRA_PRICE = "scanned_price";
    public static final String EXTRA_STOCK = "scanned_stock";

    public Intent toIntent(Intent target) {
        if (name != null) {
            target.putExtra(EXTRA_NAME, name);
        }
        if (unit != null) {
            target.putExtra(EXTRA_UNIT, unit);
        }
        if (price != null) {
            target.putExtra(EXTRA_PRICE, price);
        }
        if (stock != null) {
            target.putExtra(EXTRA_STOCK, stock);
        }
        return target;
    }

    @Nullable
    public static ScannedLabel fromIntent(@Nullable Intent data) {
        if (data == null) {
            return null;
        }
        ScannedLabel label = new ScannedLabel();
        label.name = data.getStringExtra(EXTRA_NAME);
        label.unit = data.getStringExtra(EXTRA_UNIT);
        label.price = data.getStringExtra(EXTRA_PRICE);
        label.stock = data.getStringExtra(EXTRA_STOCK);
        if (label.name == null && label.unit == null && label.price == null && label.stock == null) {
            return null;
        }
        return label;
    }
}
