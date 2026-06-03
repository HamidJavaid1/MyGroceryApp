package com.bazarlink.shared.models;

import java.util.List;

public class PaymentMethodsResponse {
    public List<PaymentMethodOption> methods;

    public static class PaymentMethodOption {
        public String id;
        public String label;
        public boolean sandbox;
        public boolean requires_mobile;
        public boolean requires_otp;
    }
}
