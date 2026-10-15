package dev.ronaldofojr.pdv.repository;

import dev.ronaldofojr.pdv.domain.SaleItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findBySaleIdOrderByProductName(UUID saleId);
}
