package dev.ronaldofojr.pdv.api;

import dev.ronaldofojr.pdv.api.Dtos.CreateCustomerRequest;
import dev.ronaldofojr.pdv.api.Dtos.CustomerResponse;
import dev.ronaldofojr.pdv.service.CustomerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CustomerResponse> search(@RequestParam(required = false) String q,
                                         @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(q, pageable).map(CustomerResponse::from);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.from(service.get(id));
    }

    // o vendedor pode cadastrar o cliente na hora do atendimento
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse body = CustomerResponse.from(service.create(request.name(), request.cnpj(), request.email()));
        return ResponseEntity.created(URI.create("/customers/" + body.id())).body(body);
    }
}
