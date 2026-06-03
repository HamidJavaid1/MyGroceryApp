package com.bazarlink.shared.models;

import java.util.List;

public class Page<T> {
    public int count;
    public String next;
    public String previous;
    public List<T> results;
}
