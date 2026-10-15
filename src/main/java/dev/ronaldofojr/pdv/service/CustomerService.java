package dev.ronaldofojr.pdv.service;

import dev.ronaldofojr.pdv.domain.Cnpj;
import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.repository.CustomerRepository;
import dev.ronaldofojr.pdv.service.Exceptions.BusinessRuleException;
import dev.ronaldofojr.pdv.service.Exceptions.ConflictException;
import dev.ronaldofojr.pdv.service.Exceptions.NotFoundException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional
    public Customer create(String name, String cnpj, String email) {
        if (!Cnpj.isValid(cnpj)) {
            throw new BusinessRuleException("CNPJ invalido");
        }
        String digits = Cnpj.normalize(cnpj);
        if (customers.existsByCnpj(digits)) {
            throw new ConflictException("Ja existe um cliente com o CNPJ " + digits);
        }
        try {
            return customers.saveAndFlush(new Customer(name, digits, email));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ja existe um cliente com o CNPJ " + digits);
        }
    }

    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente", id));
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(String q, Pageable pageable) {
        String text = q == null ? "" : q.trim();
        // busca por CNPJ so quando o texto tem digitos; senao o "%%" casaria com todo mundo
        String digits = Cnpj.normalize(text);
        return customers.search(text, digits.isEmpty() ? "no-digits-" + UUID.randomUUID() : digits, pageable);
    }
}
