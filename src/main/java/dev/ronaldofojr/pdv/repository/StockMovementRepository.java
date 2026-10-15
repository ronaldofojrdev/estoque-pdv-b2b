package dev.ronaldofojr.pdv.repository;

import dev.ronaldofojr.pdv.domain.StockMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findTop100ByProductIdOrderByCreatedAtDesc(UUID productId);
}
