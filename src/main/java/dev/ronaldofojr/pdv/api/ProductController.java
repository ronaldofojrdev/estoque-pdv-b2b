package dev.ronaldofojr.pdv.api;

import dev.ronaldofojr.pdv.api.Dtos.CreateProductRequest;
import dev.ronaldofojr.pdv.api.Dtos.MovementResponse;
import dev.ronaldofojr.pdv.api.Dtos.ProductResponse;
import dev.ronaldofojr.pdv.api.Dtos.StockMovementRequest;
import dev.ronaldofojr.pdv.api.Dtos.UpdateProductRequest;
import dev.ronaldofojr.pdv.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** Busca por nome ou SKU. O PDV usa onlyActive=true (padrao); a tela de admin pode pedir tudo. */
    @GetMapping
    public Page<ProductResponse> search(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "true") boolean onlyActive,
                                        @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        validateSort(pageable);
        return service.search(q, onlyActive, pageable).map(ProductResponse::from);
    }

    // Sem isso, ?sort=campoInexistente vira erro 500 (o Hibernate nao acha o atributo).
    private static final Set<String> SORTABLE = Set.of("name", "sku", "price", "quantity", "createdAt");

    private static void validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ordenacao invalida: " + order.getProperty() + ". Use: " + String.join(", ", SORTABLE));
            }
        });
    }

    @GetMapping("/low-stock")
    public List<ProductResponse> lowStock() {
        return service.lowStock().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return ProductResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request, Principal user) {
        ProductResponse body = ProductResponse.from(service.create(request.sku(), request.name(), request.price(),
                request.initialQuantity() == null ? 0 : request.initialQuantity(),
                request.minStock() == null ? 0 : request.minStock(), user.getName()));
        return ResponseEntity.created(URI.create("/products/" + body.id())).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(service.update(id, request.name(), request.price(), request.minStock(), request.active()));
    }

    @PostMapping("/{id}/movements")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse adjustStock(@PathVariable UUID id, @Valid @RequestBody StockMovementRequest request,
                                       Principal user) {
        return ProductResponse.from(
                service.adjust(id, request.type(), request.quantity(), request.reason(), user.getName()));
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MovementResponse> movements(@PathVariable UUID id) {
        return service.movementsOf(id).stream().map(MovementResponse::from).toList();
    }
}
