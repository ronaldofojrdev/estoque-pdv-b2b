package dev.ronaldofojr.pdv.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.pdv.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CustomerApiIT extends IntegrationTestBase {

    private static final String CNPJ = "11.222.333/0001-81";

    @Test
    void createsCustomerStoringOnlyTheDigits() throws Exception {
        mvc.perform(post("/customers").header("Authorization", sellerToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mercado Central Ltda\",\"cnpj\":\"%s\",\"email\":\"compras@mercado.com\"}".formatted(CNPJ)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cnpj").value("11222333000181"));
    }

    @Test
    void invalidCnpjReturns422() throws Exception {
        mvc.perform(post("/customers").header("Authorization", sellerToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa\",\"cnpj\":\"11.222.333/0001-80\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("CNPJ invalido"));
    }

    @Test
    void sameCnpjWithDifferentFormattingReturns409() throws Exception {
        String token = sellerToken();
        mvc.perform(post("/customers").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"cnpj\":\"%s\"}".formatted(CNPJ))).andExpect(status().isCreated());

        mvc.perform(post("/customers").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"B\",\"cnpj\":\"11222333000181\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void searchesByNameOrCnpjDigits() throws Exception {
        String token = sellerToken();
        mvc.perform(post("/customers").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Padaria Pao Quente\",\"cnpj\":\"%s\"}".formatted(CNPJ)));

        mvc.perform(get("/customers?q=padaria").header("Authorization", token))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/customers?q=11.222.333").header("Authorization", token))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/customers?q=zzz").header("Authorization", token))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/customers").header("Authorization", token))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void unknownCustomerReturns404() throws Exception {
        mvc.perform(get("/customers/" + UUID.randomUUID()).header("Authorization", sellerToken()))
                .andExpect(status().isNotFound());
    }
}
