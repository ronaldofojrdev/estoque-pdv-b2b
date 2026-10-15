package dev.ronaldofojr.pdv.repository;

import dev.ronaldofojr.pdv.domain.Customer;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByCnpj(String cnpj);

    @Query("""
            select c from Customer c
            where c.active = true
              and (lower(c.name) like lower(concat('%', :q, '%')) or c.cnpj like concat('%', :digits, '%'))
            """)
    Page<Customer> search(@Param("q") String q, @Param("digits") String digits, Pageable pageable);
}
