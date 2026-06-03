package com.bazarlink.shared.models;

import java.math.BigDecimal;
import java.util.List;

public class Product { public long id; public Long shop; public String shop_name; public long category; public String category_name; public String name; public String description; public String unit; public BigDecimal price; public BigDecimal stock_quantity; public BigDecimal low_stock_threshold; public boolean is_bulk_available; public BigDecimal min_bulk_quantity; public float rating; public List<ProductImage> images; }
