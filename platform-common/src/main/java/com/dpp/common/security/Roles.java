package com.dpp.common.security;

public final class Roles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";

    public static final String PREFIX = "ROLE_";
    public static final String CUSTOMER_AUTHORITY = PREFIX + CUSTOMER;
    public static final String ADMIN_AUTHORITY = PREFIX + ADMIN;

    private Roles() {
    }
}
