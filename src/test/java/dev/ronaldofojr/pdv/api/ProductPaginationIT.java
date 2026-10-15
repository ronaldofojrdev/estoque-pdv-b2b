package dev.ronaldofojr.pdv.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.pdv.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de borda da listagem paginada de produtos. */
class ProductPaginationIT extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void createProducts() throws Exception {
        token = sellerToken();
        for (int i = 1; i <= 5; i++) {
            newProduct("PAG-" + i, "1.00", 10, 0);
        }
    }

    @Test
    void pageBeyondTheLastReturnsEmptyContentButKeepsTheTotals() throws Exception {
        mvc.perform(get("/products?size=2&page=10").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void lastPageHasOnlyTheRemainingItems() throws Exception {
        mvc.perform(get("/products?size=2&page=2").header("Authorization", token))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void sizeAboveTheLimitIsCappedAt100() throws Exception {
        mvc.perform(get("/products?size=100000").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void negativePageAndZeroSizeFallBackToTheDefaults() throws Exception {
        mvc.perform(get("/products?page=-3&size=0").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void nonNumericPageFallsBackToTheFirstPage() throws Exception {
        // comportamento do Spring: valor que nao e numero e ignorado e vale o padrao
        mvc.perform(get("/products?page=abc").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void sortingByAFieldThatDoesNotExistReturns400NotAServerError() throws Exception {
        mvc.perform(get("/products?sort=campoInexistente,asc").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortingByPriceDescendingWorks() throws Exception {
        newProduct("PAG-CARO", "999.00", 1, 0);

        mvc.perform(get("/products?sort=price,desc&size=1").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("PAG-CARO"));
    }

    @Test
    void searchWithoutMatchesReturnsAnEmptyPageNotAnError() throws Exception {
        mvc.perform(get("/products?q=nao-existe-esse-produto").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }
}
