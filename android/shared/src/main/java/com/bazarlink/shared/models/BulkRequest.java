package com.bazarlink.shared.models;

import java.math.BigDecimal;

public class BulkRequest {
    public long id;
    public long customer;
    public long product;
    public String product_name;
    public BigDecimal quantity;
    public String delivery_address;
    public String notes;
    public String status;
    public boolean can_dispatch;
}
