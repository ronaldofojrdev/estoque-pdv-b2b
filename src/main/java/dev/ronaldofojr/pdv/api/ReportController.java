package dev.ronaldofojr.pdv.api;

import dev.ronaldofojr.pdv.api.Dtos.DailyTotalResponse;
import dev.ronaldofojr.pdv.api.Dtos.SummaryResponse;
import dev.ronaldofojr.pdv.api.Dtos.TopProductResponse;
import dev.ronaldofojr.pdv.repository.SaleRepository;
import dev.ronaldofojr.pdv.service.Exceptions.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Relatorios so de vendas concluidas (as canceladas ficam de fora). Datas em UTC, intervalo [from, to]. */
@RestController
@RequestMapping("/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private static final int MAX_RANGE_DAYS = 366;

    private final SaleRepository sales;

    public ReportController(SaleRepository sales) {
        this.sales = sales;
    }

    @GetMapping("/daily")
    @Transactional(readOnly = true)
    public List<DailyTotalResponse> daily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return sales.dailyTotals(from.atStartOfDay().toInstant(ZoneOffset.UTC), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
                .stream().map(d -> new DailyTotalResponse(d.getDay(), d.getSalesCount(), d.getTotal())).toList();
    }

    /** Total vendido, quantidade de vendas e ticket medio do periodo. */
    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public SummaryResponse summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        SaleRepository.Summary summary = sales.summary(from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        BigDecimal average = summary.getSalesCount() == 0 ? null
                : summary.getTotal().divide(BigDecimal.valueOf(summary.getSalesCount()), 2, RoundingMode.HALF_EVEN);
        return new SummaryResponse(from, to, summary.getSalesCount(), summary.getTotal(), average);
    }

    @GetMapping("/top-products")
    @Transactional(readOnly = true)
    public List<TopProductResponse> topProducts(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(defaultValue = "10") int limit) {
        validateRange(from, to);
        int capped = Math.max(1, Math.min(limit, 100));
        return sales.topProducts(from.atStartOfDay().toInstant(ZoneOffset.UTC), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), capped)
                .stream().map(t -> new TopProductResponse(t.getProductId(), t.getProductName(), t.getQuantity(), t.getRevenue())).toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("A data final nao pode ser anterior a inicial");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessRuleException("O periodo maximo e de " + MAX_RANGE_DAYS + " dias");
        }
    }
}
