package dev.ronaldofojr.pdv.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    // updatable = false: o saldo so muda por UPDATE atomico no repositorio (decrementStock/incrementStock).
    // Se o JPA regravasse esta coluna num save() de edicao de nome/preco, poderia apagar uma venda que
    // acabou de baixar o estoque (lost update).
    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String sku, String name, BigDecimal price, int initialQuantity, int minStock) {
        requireValid(price, initialQuantity, minStock);
        this.id = UUID.randomUUID();
        this.sku = sku.trim().toUpperCase();
        this.name = name;
        this.price = price.setScale(2, RoundingMode.HALF_EVEN);
        this.quantity = initialQuantity;
        this.minStock = minStock;
        this.active = true;
        this.createdAt = Instant.now();
    }

    // O DTO ja valida isso na API, mas o dominio nao pode depender de quem o chama.
    private static void requireValid(BigDecimal price, int quantity, int minStock) {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("O preco nao pode ser negativo");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("O estoque nao pode ser negativo");
        }
        if (minStock < 0) {
            throw new IllegalArgumentException("O estoque minimo nao pode ser negativo");
        }
    }

    public void update(String name, BigDecimal price, int minStock, boolean active) {
        requireValid(price, 0, minStock);
        this.name = name;
        this.price = price.setScale(2, RoundingMode.HALF_EVEN);
        this.minStock = minStock;
        this.active = active;
    }

    public boolean isLowStock() {
        return quantity <= minStock;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getMinStock() {
        return minStock;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
