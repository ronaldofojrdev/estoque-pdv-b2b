package dev.ronaldofojr.pdv.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Historico de tudo que mexeu no estoque. delta e negativo nas saidas. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false)
    private int delta;

    private String reason;

    @Column(name = "sale_id")
    private UUID saleId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(UUID productId, MovementType type, int delta, String reason, UUID saleId, String createdBy) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.type = type;
        this.delta = delta;
        this.reason = reason;
        this.saleId = saleId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public MovementType getType() {
        return type;
    }

    public int getDelta() {
        return delta;
    }

    public String getReason() {
        return reason;
    }

    public UUID getSaleId() {
        return saleId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
