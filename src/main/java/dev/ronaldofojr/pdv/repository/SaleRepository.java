package dev.ronaldofojr.pdv.repository;

import dev.ronaldofojr.pdv.domain.Sale;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByClientSaleId(UUID clientSaleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findForUpdate(@Param("id") UUID id);

    Page<Sale> findAllByOrderByCreatedAtDesc(Pageable pageable);

    interface DailyTotal {
        LocalDate getDay();

        long getSalesCount();

        BigDecimal getTotal();
    }

    interface Summary {
        long getSalesCount();

        BigDecimal getTotal();
    }

    interface TopProduct {
        UUID getProductId();

        String getProductName();

        long getQuantity();

        BigDecimal getRevenue();
    }

    @Query(value = """
            SELECT CAST(created_at AT TIME ZONE 'UTC' AS date) AS day, count(*) AS salesCount, sum(total) AS total
            FROM sales
            WHERE status = 'COMPLETED' AND created_at >= :from AND created_at < :to
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<DailyTotal> dailyTotals(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            SELECT i.product_id AS productId, i.product_name AS productName,
                   sum(i.quantity) AS quantity, sum(i.line_total) AS revenue
            FROM sale_items i JOIN sales s ON s.id = i.sale_id
            WHERE s.status = 'COMPLETED' AND s.created_at >= :from AND s.created_at < :to
            GROUP BY i.product_id, i.product_name
            ORDER BY revenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopProduct> topProducts(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    @Query(value = """
            SELECT count(*) AS salesCount, coalesce(sum(total), 0) AS total
            FROM sales
            WHERE status = 'COMPLETED' AND created_at >= :from AND created_at < :to
            """, nativeQuery = true)
    Summary summary(@Param("from") Instant from, @Param("to") Instant to);
}
