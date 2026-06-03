package com.bazarlink.shared.models;

import java.math.BigDecimal;
import java.util.Map;

public class Payment {
    public String id;
    public long order;
    public String provider;
    public String provider_label;
    public BigDecimal amount;
    public String currency;
    public String status;
    public String status_label;
    public String mobile_account;
    public String gateway_reference;
    public String gateway_message;
    public Map<String, Object> metadata;
}
