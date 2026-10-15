package dev.ronaldofojr.pdv.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.ronaldofojr.pdv.IntegrationTestBase;
import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.domain.Product;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SaleApiIT extends IntegrationTestBase {

    private String saleBody(Customer customer, Product product, int quantity) {
        return "{\"customerId\":\"%s\",\"paymentMethod\":\"PIX\",\"items\":[{\"productId\":\"%s\",\"quantity\":%d}]}"
                .formatted(customer.getId(), product.getId(), quantity);
    }

    private ResultActions sell(String token, UUID key, String body) throws Exception {
        return mvc.perform(post("/sales").header("Authorization", token).header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void createsASaleReturning201AndTheItemSnapshot() throws Exception {
        Product product = newProduct("API-1", "7.50", 20, 0);
        Customer customer = newCustomer();

        sell(sellerToken(), UUID.randomUUID(), saleBody(customer, product, 4))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.total").value(30.0))
                .andExpect(jsonPath("$.createdBy").value("vendedor"))
                .andExpect(jsonPath("$.items[0].sku").value("API-1"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(7.5));
        assertThat(stockOf(product)).isEqualTo(16);
    }

    @Test
    void replayingTheSameIdempotencyKeyReturns200AndTheSameSale() throws Exception {
        Product product = newProduct("API-2", "10.00", 10, 0);
        Customer customer = newCustomer();
        UUID key = UUID.randomUUID();
        String token = sellerToken();

        String first = sell(token, key, saleBody(customer, product, 3)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = sell(token, key, saleBody(customer, product, 3)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(stockOf(product)).isEqualTo(7);
    }

    @Test
    void insufficientStockReturns409WithTheAvailableQuantity() throws Exception {
        Product product = newProduct("API-3", "10.00", 2, 0);

        sell(sellerToken(), UUID.randomUUID(), saleBody(newCustomer(), product, 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.available").value(2))
                .andExpect(jsonPath("$.productId").value(product.getId().toString()));
        assertThat(stockOf(product)).isEqualTo(2);
    }

    @Test
    void missingIdempotencyKeyOrInvalidBodyReturn400() throws Exception {
        Product product = newProduct("API-4", "10.00", 5, 0);
        Customer customer = newCustomer();
        String token = sellerToken();

        mvc.perform(post("/sales").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(customer, product, 1)))
                .andExpect(status().isBadRequest());
        sell(token, UUID.randomUUID(), "{\"customerId\":\"%s\",\"paymentMethod\":\"PIX\",\"items\":[]}".formatted(customer.getId()))
                .andExpect(status().isBadRequest());
        sell(token, UUID.randomUUID(), saleBody(customer, product, 0)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownCustomerOrProductReturns404() throws Exception {
        Product product = newProduct("API-5", "10.00", 5, 0);
        Customer customer = newCustomer();
        String token = sellerToken();

        sell(token, UUID.randomUUID(), "{\"customerId\":\"%s\",\"paymentMethod\":\"CASH\",\"items\":[{\"productId\":\"%s\",\"quantity\":1}]}"
                .formatted(UUID.randomUUID(), product.getId())).andExpect(status().isNotFound());
        sell(token, UUID.randomUUID(), "{\"customerId\":\"%s\",\"paymentMethod\":\"CASH\",\"items\":[{\"productId\":\"%s\",\"quantity\":1}]}"
                .formatted(customer.getId(), UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminCancelsAndCancelReturnsStock() throws Exception {
        Product product = newProduct("API-6", "10.00", 10, 0);
        String response = sell(sellerToken(), UUID.randomUUID(), saleBody(newCustomer(), product, 6))
                .andReturn().getResponse().getContentAsString();
        String saleId = JsonPath.read(response, "$.id");

        mvc.perform(post("/sales/" + saleId + "/cancel").header("Authorization", sellerToken()))
                .andExpect(status().isForbidden());
        assertThat(stockOf(product)).isEqualTo(4);

        mvc.perform(post("/sales/" + saleId + "/cancel").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @Test
    void listsSalesNewestFirstWithCustomerName() throws Exception {
        Product product = newProduct("API-7", "1.00", 100, 0);
        Customer customer = newCustomer();
        String token = sellerToken();
        sell(token, UUID.randomUUID(), saleBody(customer, product, 1));
        sell(token, UUID.randomUUID(), saleBody(customer, product, 2));

        mvc.perform(get("/sales").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].total").value(2.0))
                .andExpect(jsonPath("$.content[0].customerName").value(customer.getName()));
    }

    @Test
    void reportsIgnoreCanceledSalesAndRankProductsByRevenue() throws Exception {
        Product cheap = newProduct("REP-1", "1.00", 1000, 0);
        Product pricey = newProduct("REP-2", "50.00", 1000, 0);
        Customer customer = newCustomer();
        String admin = adminToken();
        sell(admin, UUID.randomUUID(), saleBody(customer, cheap, 10));   // 10,00
        sell(admin, UUID.randomUUID(), saleBody(customer, pricey, 2));   // 100,00
        String canceled = sell(admin, UUID.randomUUID(), saleBody(customer, pricey, 5))
                .andReturn().getResponse().getContentAsString();            // 250,00 (cancelada abaixo)
        mvc.perform(post("/sales/" + JsonPath.read(canceled, "$.id") + "/cancel").header("Authorization", admin));

        String today = LocalDate.now(java.time.ZoneOffset.UTC).toString();
        mvc.perform(get("/reports/daily?from=" + today + "&to=" + today).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].salesCount").value(2))
                .andExpect(jsonPath("$[0].total").value(110.0));
        mvc.perform(get("/reports/top-products?from=" + today + "&to=" + today + "&limit=5").header("Authorization", admin))
                .andExpect(jsonPath("$[0].productName").value("Produto REP-2"))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[1].productName").value("Produto REP-1"));
    }

    @Test
    void reportRejectsInvertedOrHugeRanges() throws Exception {
        String admin = adminToken();

        mvc.perform(get("/reports/daily?from=2026-02-10&to=2026-02-01").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/reports/daily?from=2020-01-01&to=2026-01-01").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
    }
}
