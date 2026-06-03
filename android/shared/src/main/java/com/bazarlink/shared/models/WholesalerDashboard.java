package com.bazarlink.shared.models;

import java.math.BigDecimal;
import java.util.List;

public class WholesalerDashboard {
    public BigDecimal total_sales;
    public BigDecimal total_profit;
    public int active_shipments;
    public int pending_orders;
    public List<LowStockAlert> low_stock_alerts;

    public static class LowStockAlert {
        public String name;
        public BigDecimal stock_left;
    }
}