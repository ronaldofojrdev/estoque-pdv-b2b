package dev.ronaldofojr.pdv.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.pdv.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SecurityIT extends IntegrationTestBase {

    @Test
    void loginReturnsTokenAndRole() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordOrUnknownUserReturns401WithTheSameMessage() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario ou senha invalidos"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ninguem\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario ou senha invalidos"));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/sales")).andExpect(status().isUnauthorized());
        mvc.perform(get("/products").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void sellerCannotUseAdminEndpoints() throws Exception {
        String seller = sellerToken();
        UUID anyId = UUID.randomUUID();

        mvc.perform(post("/products").header("Authorization", seller).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"X\",\"name\":\"X\",\"price\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/products/" + anyId + "/movements").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"IN\",\"quantity\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/sales/" + anyId + "/cancel").header("Authorization", seller)).andExpect(status().isForbidden());
        mvc.perform(get("/reports/daily?from=2026-01-01&to=2026-01-31").header("Authorization", seller))
                .andExpect(status().isForbidden());
        mvc.perform(get("/products/low-stock").header("Authorization", seller)).andExpect(status().isOk());
    }

    @Test
    void adminCanUseAdminEndpoints() throws Exception {
        mvc.perform(get("/reports/daily?from=2026-01-01&to=2026-01-31").header("Authorization", adminToken()))
                .andExpect(status().isOk());
    }
}
