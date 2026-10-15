package dev.ronaldofojr.pdv.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.ronaldofojr.pdv.IntegrationTestBase;
import dev.ronaldofojr.pdv.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProductApiIT extends IntegrationTestBase {

    private String create(String sku, String name, String price, int qty, int min) throws Exception {
        String response = mvc.perform(post("/products").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"%s\",\"name\":\"%s\",\"price\":%s,\"initialQuantity\":%d,\"minStock\":%d}"
                                .formatted(sku, name, price, qty, min)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void createsAProductNormalizesSkuAndRecordsTheInitialMovement() throws Exception {
        String id = create("  par-10 ", "Parafuso 10mm", "1.5", 50, 10);

        mvc.perform(get("/products/" + id).header("Authorization", sellerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("PAR-10"))
                .andExpect(jsonPath("$.quantity").value(50))
                .andExpect(jsonPath("$.lowStock").value(false));
        mvc.perform(get("/products/" + id + "/movements").header("Authorization", adminToken()))
                .andExpect(jsonPath("$[0].type").value("IN"))
                .andExpect(jsonPath("$[0].delta").value(50));
    }

    @Test
    void duplicatedSkuReturns409() throws Exception {
        create("DUP-1", "Primeiro", "1", 0, 0);

        mvc.perform(post("/products").header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"dup-1\",\"name\":\"Segundo\",\"price\":2}"))
                .andExpect(status().isConflict());
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        mvc.perform(post("/products").header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\",\"name\":\"X\",\"price\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/products").header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A\",\"name\":\"X\",\"price\":1,\"initialQuantity\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchFiltersByNameOrSkuAndPaginates() throws Exception {
        create("AAA-1", "Cabo de rede", "10", 5, 1);
        create("AAA-2", "Cabo HDMI", "20", 5, 1);
        create("BBB-1", "Mouse", "30", 5, 1);

        mvc.perform(get("/products?q=cabo").header("Authorization", sellerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/products?q=bbb").header("Authorization", sellerToken()))
                .andExpect(jsonPath("$.content[0].name").value("Mouse"));
        mvc.perform(get("/products?size=2&page=1").header("Authorization", sellerToken()))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void inactiveProductsAreHiddenFromThePdvButVisibleToTheAdminList() throws Exception {
        String id = create("INA-1", "Descontinuado", "5", 1, 0);
        mvc.perform(put("/products/" + id).header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Descontinuado\",\"price\":5,\"minStock\":0,\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/products?q=INA-1").header("Authorization", sellerToken()))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/products?q=INA-1&onlyActive=false").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void editingNameAndPriceDoesNotTouchTheStock() throws Exception {
        Product product = newProduct("EDT-1", "10.00", 30, 5);

        mvc.perform(put("/products/" + product.getId()).header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo nome\",\"price\":12.5,\"minStock\":7,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12.5))
                .andExpect(jsonPath("$.quantity").value(30));
        assertThat(stockOf(product)).isEqualTo(30);
    }

    @Test
    void manualStockInAndOutUpdateTheBalance() throws Exception {
        Product product = newProduct("MOV-1", "1.00", 10, 0);

        mvc.perform(post("/products/" + product.getId() + "/movements").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"IN\",\"quantity\":15,\"reason\":\"Compra\"}"))
                .andExpect(jsonPath("$.quantity").value(25));
        mvc.perform(post("/products/" + product.getId() + "/movements").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OUT\",\"quantity\":5,\"reason\":\"Quebra\"}"))
                .andExpect(jsonPath("$.quantity").value(20));
    }

    @Test
    void manualOutNeverMakesTheStockNegative() throws Exception {
        Product product = newProduct("MOV-2", "1.00", 3, 0);

        mvc.perform(post("/products/" + product.getId() + "/movements").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OUT\",\"quantity\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.available").value(3));
        assertThat(stockOf(product)).isEqualTo(3);
    }

    @Test
    void salesTypesAreNotAcceptedAsManualMovements() throws Exception {
        Product product = newProduct("MOV-3", "1.00", 3, 0);

        mvc.perform(post("/products/" + product.getId() + "/movements").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"SALE\",\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void lowStockListsProductsAtOrBelowTheMinimum() throws Exception {
        newProduct("LOW-1", "1.00", 2, 5);   // abaixo do minimo
        newProduct("LOW-2", "1.00", 5, 5);   // exatamente no minimo
        newProduct("LOW-3", "1.00", 50, 5);  // ok

        mvc.perform(get("/products/low-stock").header("Authorization", sellerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value("LOW-1"))
                .andExpect(jsonPath("$[1].sku").value("LOW-2"));
    }
}
