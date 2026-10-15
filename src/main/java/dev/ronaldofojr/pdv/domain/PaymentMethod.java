package dev.ronaldofojr.pdv.domain;

public enum PaymentMethod {
    CASH,
    PIX,
    CARD,
    /** Faturado: cliente B2B paga depois. */
    INVOICE
}
