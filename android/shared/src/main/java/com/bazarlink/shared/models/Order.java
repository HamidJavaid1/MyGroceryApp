package com.bazarlink.shared.models;

import java.math.BigDecimal;
import java.util.List;

public class Order {
    public long id;
    public long customer;
    public String customer_name;
    public long shop;
    public String shop_name;
    public String status;
    public String address;
    public String payment_method;
    public String payment_status;
    public BigDecimal subtotal;
    public BigDecimal delivery_fee;
    public BigDecimal total;
    public List<OrderItem> items;
}
