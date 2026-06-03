package com.bazarlink.app;

import com.bazarlink.shared.models.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory cart scoped to a single retail shop. */
public class CartManager {
    public static class Line {
        public final long productId;
        public final String name;
        public final String unit;
        public final BigDecimal unitPrice;
        public BigDecimal quantity;

        Line(Product product, BigDecimal quantity) {
            this.productId = product.id;
            this.name = product.name;
            this.unit = product.unit == null ? "unit" : product.unit;
            this.unitPrice = product.price == null ? BigDecimal.ZERO : product.price;
            this.quantity = quantity;
        }

        public BigDecimal lineTotal() {
            return unitPrice.multiply(quantity);
        }
    }

    private long shopId;
    private String shopName = "";
    private final Map<Long, Line> lines = new LinkedHashMap<>();

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public long getShopId() {
        return shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public int itemCount() {
        return lines.size();
    }

    public List<Line> getLines() {
        return new ArrayList<>(lines.values());
    }

    public BigDecimal subtotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines.values()) {
            total = total.add(line.lineTotal());
        }
        return total;
    }

    public void clear() {
        lines.clear();
        shopId = 0;
        shopName = "";
    }

    public void bindShop(long shopId, String shopName) {
        if (this.shopId != 0 && this.shopId != shopId) {
            lines.clear();
        }
        this.shopId = shopId;
        this.shopName = shopName == null ? "" : shopName;
    }

    public void addProduct(Product product, BigDecimal quantity) {
        if (product == null) {
            return;
        }
        bindShop(product.shop, product.shop_name);
        Line existing = lines.get(product.id);
        if (existing == null) {
            lines.put(product.id, new Line(product, quantity));
        } else {
            existing.quantity = existing.quantity.add(quantity);
        }
    }

    public void setQuantity(long productId, BigDecimal quantity) {
        Line line = lines.get(productId);
        if (line == null) {
            return;
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            lines.remove(productId);
        } else {
            line.quantity = quantity;
        }
    }
}
