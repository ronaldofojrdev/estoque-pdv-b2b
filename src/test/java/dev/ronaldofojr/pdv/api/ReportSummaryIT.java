package dev.ronaldofojr.pdv.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.ronaldofojr.pdv.IntegrationTestBase;
import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.domain.Product;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReportSummaryIT extends IntegrationTestBase {

    private final String today = LocalDate.now(ZoneOffset.UTC).toString();

    private String sell(String token, Customer customer, Product product, int quantity) throws Exception {
        return mvc.perform(post("/sales").header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"%s\",\"paymentMethod\":\"PIX\",\"items\":[{\"productId\":\"%s\",\"quantity\":%d}]}"
                                .formatted(customer.getId(), product.getId(), quantity)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void summaryHasTotalCountAndAverageTicket() throws Exception {
        Product product = newProduct("SUM-1", "10.00", 100, 0);
        Customer customer = newCustomer();
        String admin = adminToken();
        sell(admin, customer, product, 1);  // 10,00
        sell(admin, customer, product, 2);  // 20,00
        sell(admin, customer, product, 4);  // 40,00

        mvc.perform(get("/reports/summary?from=" + today + "&to=" + today).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCount").value(3))
                .andExpect(jsonPath("$.total").value(70.0))
                .andExpect(jsonPath("$.averageTicket").value(23.33));
    }

    @Test
    void summaryOfAnEmptyPeriodIsZeroWithoutAverageTicket() throws Exception {
        mvc.perform(get("/reports/summary?from=2020-01-01&to=2020-01-31").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCount").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.averageTicket").isEmpty());
    }

    @Test
    void canceledSalesAreLeftOutOfTheSummary() throws Exception {
        Product product = newProduct("SUM-2", "50.00", 100, 0);
        Customer customer = newCustomer();
        String admin = adminToken();
        sell(admin, customer, product, 1);
        String canceled = sell(admin, customer, product, 3);
        mvc.perform(post("/sales/" + JsonPath.read(canceled, "$.id") + "/cancel").header("Authorization", admin))
                .andExpect(status().isOk());

        mvc.perform(get("/reports/summary?from=" + today + "&to=" + today).header("Authorization", admin))
                .andExpect(jsonPath("$.salesCount").value(1))
                .andExpect(jsonPath("$.total").value(50.0))
                .andExpect(jsonPath("$.averageTicket").value(50.0));
    }

    @Test
    void otherReportsAlsoReturnEmptyListsForAnEmptyPeriod() throws Exception {
        String admin = adminToken();

        mvc.perform(get("/reports/daily?from=2020-01-01&to=2020-01-31").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/reports/top-products?from=2020-01-01&to=2020-01-31").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void summaryRequiresAdminAndAValidRange() throws Exception {
        mvc.perform(get("/reports/summary?from=" + today + "&to=" + today).header("Authorization", sellerToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reports/summary?from=2026-02-10&to=2026-02-01").header("Authorization", adminToken()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/reports/summary?from=nao-e-data&to=2026-02-01").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());
    }
}
