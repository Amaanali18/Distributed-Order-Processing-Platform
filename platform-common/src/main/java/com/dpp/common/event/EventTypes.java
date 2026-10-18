package com.dpp.common.event;

public final class EventTypes {

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_CANCELLED = "OrderCancelled";

    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_REJECTED = "InventoryRejected";
    public static final String INVENTORY_COMMITTED = "InventoryCommitted";
    public static final String INVENTORY_RELEASED = "InventoryReleased";

    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String REFUND_COMPLETED = "RefundCompleted";

    private EventTypes() {
    }
}
