package com.bazarlink.shared.models;

import java.math.BigDecimal;

public class Quotation { public long id; public long bulk_request; public String bulk_request_product_name; public long wholesaler; public String wholesaler_name; public BigDecimal price_per_unit; public BigDecimal delivery_fee; public String valid_until; public String message; public boolean is_accepted; public BigDecimal total; }
