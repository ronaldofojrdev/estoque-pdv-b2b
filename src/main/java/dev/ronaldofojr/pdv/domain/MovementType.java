package dev.ronaldofojr.pdv.domain;

public enum MovementType {
    /** Entrada manual (compra de fornecedor, devolucao). */
    IN,
    /** Saida manual (perda, quebra, uso interno). */
    OUT,
    SALE,
    SALE_CANCEL
}
