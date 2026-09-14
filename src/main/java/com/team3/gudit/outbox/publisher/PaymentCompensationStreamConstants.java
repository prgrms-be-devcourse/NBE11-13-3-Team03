package com.team3.gudit.outbox.publisher;

public final class PaymentCompensationStreamConstants {

    private PaymentCompensationStreamConstants() {
    }

    public static final String PAYMENT_COMPENSATION_STREAM =
            "stream:payment-compensation";

    public static final String PAYMENT_COMPENSATION_GROUP =
            "payment-compensation-group";

    public static final String PAYMENT_COMPENSATION_CONSUMER =
            "payment-compensation-consumer-1";
}