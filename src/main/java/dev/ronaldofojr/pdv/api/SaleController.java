package dev.ronaldofojr.pdv.api;

import dev.ronaldofojr.pdv.api.Dtos.CreateSaleRequest;
import dev.ronaldofojr.pdv.api.Dtos.SaleResponse;
import dev.ronaldofojr.pdv.api.Dtos.SaleSummary;
import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.repository.CustomerRepository;
import dev.ronaldofojr.pdv.service.SaleService;
import dev.ronaldofojr.pdv.service.SaleService.CreationResult;
import dev.ronaldofojr.pdv.service.SaleService.ItemRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sales")
public class SaleController {

    private final SaleService service;
    private final CustomerRepository customers;

    public SaleController(SaleService service, CustomerRepository customers) {
        this.service = service;
        this.customers = customers;
    }

    /**
     * Header Idempotency-Key: um UUID gerado pelo cliente para cada venda. 201 quando a venda e criada,
     * 200 quando a mesma chave ja tinha gerado uma venda (reenvio apos falha de rede).
     */
    @PostMapping
    public ResponseEntity<SaleResponse> create(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                               @Valid @RequestBody CreateSaleRequest request, Principal user) {
        CreationResult result = service.create(idempotencyKey, request.customerId(), request.paymentMethod(),
                request.items().stream().map(i -> new ItemRequest(i.productId(), i.quantity())).toList(),
                user.getName());
        SaleResponse body = SaleResponse.from(result.detail());
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/sales/" + body.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public Page<SaleSummary> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<dev.ronaldofojr.pdv.domain.Sale> page = service.list(pageable);
        Map<UUID, String> names = customers.findAllById(page.map(s -> s.getCustomerId()).toList()).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
        return page.map(s -> SaleSummary.from(s, names.get(s.getCustomerId())));
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable UUID id) {
        return SaleResponse.from(service.get(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public SaleResponse cancel(@PathVariable UUID id, Principal user) {
        return SaleResponse.from(service.cancel(id, user.getName()));
    }
}
