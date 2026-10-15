package dev.ronaldofojr.pdv.repository;

import dev.ronaldofojr.pdv.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySku(String sku);

    @Query("""
            select p from Product p
            where (:onlyActive = false or p.active = true)
              and (lower(p.name) like lower(concat('%', :q, '%')) or lower(p.sku) like lower(concat('%', :q, '%')))
            """)
    Page<Product> search(@Param("q") String q, @Param("onlyActive") boolean onlyActive, Pageable pageable);

    @Query("select p from Product p where p.active = true and p.quantity <= p.minStock order by p.quantity asc, p.name asc")
    List<Product> findLowStock();

    List<Product> findAllByIdIn(Collection<UUID> ids);

    /**
     * Baixa o estoque em UMA instrucao, so se houver saldo. Duas vendas simultaneas do mesmo
     * item nunca passam as duas: o banco serializa o UPDATE na linha e a segunda ve o saldo novo.
     * Devolve 0 quando nao ha saldo suficiente (ou o produto esta inativo).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE products SET quantity = quantity - :qty WHERE id = :id AND active = TRUE AND quantity >= :qty",
            nativeQuery = true)
    int decrementStock(@Param("id") UUID id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE products SET quantity = quantity + :qty WHERE id = :id", nativeQuery = true)
    int incrementStock(@Param("id") UUID id, @Param("qty") int qty);

    @Query(value = "SELECT quantity FROM products WHERE id = :id", nativeQuery = true)
    Integer currentQuantity(@Param("id") UUID id);
}
