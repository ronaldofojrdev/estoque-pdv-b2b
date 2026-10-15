package dev.ronaldofojr.pdv.service;

import java.util.UUID;

/** Excecoes de negocio. Cada uma vira um status HTTP no ApiExceptionHandler. */
public final class Exceptions {

    private Exceptions() {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String what, Object id) {
            super(what + " " + id + " nao encontrado(a)");
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) {
            super(message);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Usuario ou senha invalidos");
        }
    }

    public static class InsufficientStockException extends RuntimeException {
        private final UUID productId;
        private final int available;

        public InsufficientStockException(UUID productId, String sku, int available, int requested) {
            super("Estoque insuficiente para " + sku + ": disponivel " + available + ", pedido " + requested);
            this.productId = productId;
            this.available = available;
        }

        public UUID getProductId() {
            return productId;
        }

        public int getAvailable() {
            return available;
        }
    }
}
