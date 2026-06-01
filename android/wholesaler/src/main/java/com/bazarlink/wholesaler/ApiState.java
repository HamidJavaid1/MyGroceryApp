package com.bazarlink.wholesaler;

public class ApiState {
    public boolean isLoading;
    public String errorMessage;

    public ApiState() {
        this.isLoading = false;
        this.errorMessage = null;
    }
}

