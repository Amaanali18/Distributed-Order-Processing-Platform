package com.dpp.common.event;

public final class Topics {

    public static final String ORDERS = "orders.v1";
    public static final String INVENTORY = "inventory.v1";
    public static final String PAYMENTS = "payments.v1";
    public static final String NOTIFICATIONS = "notifications.v1";

    public static String retry(String topic) {
        return topic + ".retry";
    }

    public static String deadLetter(String topic) {
        return topic + ".DLQ";
    }

    private Topics() {
    }
}
