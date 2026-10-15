package dev.ronaldofojr.pdv.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
public class Sale {

    @Id
    private UUID id;

    @Column(name = "client_sale_id", nullable = false, unique = true, updatable = false)
    private UUID clientSaleId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    protected Sale() {
    }

    public Sale(UUID clientSaleId, UUID customerId, PaymentMethod paymentMethod, BigDecimal total, String createdBy) {
        this.id = UUID.randomUUID();
        this.clientSaleId = clientSaleId;
        this.customerId = customerId;
        this.paymentMethod = paymentMethod;
        this.total = total;
        this.createdBy = createdBy;
        this.status = SaleStatus.COMPLETED;
        this.createdAt = Instant.now();
    }

    /** @return true se a venda foi cancelada agora; false se ja estava cancelada */
    public boolean cancel() {
        if (status == SaleStatus.CANCELED) {
            return false;
        }
        status = SaleStatus.CANCELED;
        canceledAt = Instant.now();
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientSaleId() {
        return clientSaleId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }
}
