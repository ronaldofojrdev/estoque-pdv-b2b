package dev.ronaldofojr.pdv.api;

import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.domain.MovementType;
import dev.ronaldofojr.pdv.domain.PaymentMethod;
import dev.ronaldofojr.pdv.domain.Product;
import dev.ronaldofojr.pdv.domain.Sale;
import dev.ronaldofojr.pdv.domain.SaleItem;
import dev.ronaldofojr.pdv.domain.SaleStatus;
import dev.ronaldofojr.pdv.domain.StockMovement;
import dev.ronaldofojr.pdv.service.AuthService.Token;
import dev.ronaldofojr.pdv.service.SaleService.SaleDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Requests e responses da API num lugar so. */
public final class Dtos {

    private Dtos() {
    }

    // ---- auth
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String username, String role, Instant expiresAt) {
        public static LoginResponse from(Token t) {
            return new LoginResponse(t.value(), t.username(), t.role(), t.expiresAt());
        }
    }

    // ---- produtos
    public record CreateProductRequest(
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
            @Min(0) @Max(10_000_000) Integer initialQuantity,
            @Min(0) @Max(10_000_000) Integer minStock) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
            @Min(0) @Max(10_000_000) int minStock,
            boolean active) {
    }

    public record StockMovementRequest(
            @NotNull MovementType type,
            @Min(1) @Max(10_000_000) int quantity,
            @Size(max = 200) String reason) {
    }

    public record ProductResponse(UUID id, String sku, String name, BigDecimal price, int quantity, int minStock,
                                  boolean active, boolean lowStock) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getQuantity(),
                    p.getMinStock(), p.isActive(), p.isLowStock());
        }
    }

    public record MovementResponse(UUID id, MovementType type, int delta, String reason, UUID saleId,
                                   String createdBy, Instant createdAt) {
        public static MovementResponse from(StockMovement m) {
            return new MovementResponse(m.getId(), m.getType(), m.getDelta(), m.getReason(), m.getSaleId(),
                    m.getCreatedBy(), m.getCreatedAt());
        }
    }

    // ---- clientes
    public record CreateCustomerRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank String cnpj,
            @Email @Size(max = 160) String email) {
    }

    public record CustomerResponse(UUID id, String name, String cnpj, String email) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getCnpj(), c.getEmail());
        }
    }

    // ---- vendas
    public record SaleItemRequest(
            @NotNull UUID productId,
            @Min(1) @Max(100_000) int quantity) {
    }

    public record CreateSaleRequest(
            @NotNull UUID customerId,
            @NotNull PaymentMethod paymentMethod,
            @NotEmpty @Size(max = 200) List<@Valid SaleItemRequest> items) {
    }

    public record SaleItemResponse(UUID productId, String sku, String productName, BigDecimal unitPrice,
                                   int quantity, BigDecimal lineTotal) {
        static SaleItemResponse from(SaleItem i) {
            return new SaleItemResponse(i.getProductId(), i.getSku(), i.getProductName(), i.getUnitPrice(),
                    i.getQuantity(), i.getLineTotal());
        }
    }

    public record SaleSummary(UUID id, UUID customerId, String customerName, SaleStatus status,
                              PaymentMethod paymentMethod, BigDecimal total, String createdBy, Instant createdAt) {
        public static SaleSummary from(Sale s, String customerName) {
            return new SaleSummary(s.getId(), s.getCustomerId(), customerName, s.getStatus(), s.getPaymentMethod(),
                    s.getTotal(), s.getCreatedBy(), s.getCreatedAt());
        }
    }

    public record SaleResponse(UUID id, UUID clientSaleId, UUID customerId, SaleStatus status,
                               PaymentMethod paymentMethod, BigDecimal total, String createdBy, Instant createdAt,
                               Instant canceledAt, List<SaleItemResponse> items) {
        public static SaleResponse from(SaleDetail d) {
            Sale s = d.sale();
            return new SaleResponse(s.getId(), s.getClientSaleId(), s.getCustomerId(), s.getStatus(),
                    s.getPaymentMethod(), s.getTotal(), s.getCreatedBy(), s.getCreatedAt(), s.getCanceledAt(),
                    d.items().stream().map(SaleItemResponse::from).toList());
        }
    }

    // ---- relatorios
    public record DailyTotalResponse(java.time.LocalDate day, long salesCount, BigDecimal total) {
    }

    /** averageTicket e nulo quando nao houve venda no periodo (nao existe media de zero vendas). */
    public record SummaryResponse(java.time.LocalDate from, java.time.LocalDate to, long salesCount,
                                  BigDecimal total, BigDecimal averageTicket) {
    }

    public record TopProductResponse(UUID productId, String productName, long quantity, BigDecimal revenue) {
    }
}
