package dev.ronaldofojr.pdv.domain;

public enum Role {
    /** Cadastra produtos, ajusta estoque, cancela vendas e ve relatorios. */
    ADMIN,
    /** Opera o PDV: consulta produtos e clientes e registra vendas. */
    SELLER
}
