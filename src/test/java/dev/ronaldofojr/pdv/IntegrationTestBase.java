package dev.ronaldofojr.pdv;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.domain.Product;
import dev.ronaldofojr.pdv.service.CustomerService;
import dev.ronaldofojr.pdv.service.ProductService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Postgres real (Testcontainers). Antes de cada teste limpa as tabelas, mantendo os usuarios do seed. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ProductService productService;
    @Autowired
    protected CustomerService customerService;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE stock_movements, sale_items, sales, customers, products CASCADE");
    }

    protected String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    protected String adminToken() throws Exception {
        return "Bearer " + login("admin", "admin123");
    }

    protected String sellerToken() throws Exception {
        return "Bearer " + login("vendedor", "vendedor123");
    }

    protected Product newProduct(String sku, String price, int quantity, int minStock) {
        return productService.create(sku, "Produto " + sku, new BigDecimal(price), quantity, minStock, "teste");
    }

    protected Customer newCustomer() {
        return customerService.create("Empresa Teste " + UUID.randomUUID(), randomValidCnpj(), null);
    }

    /** CNPJ valido e diferente a cada chamada: 12 digitos aleatorios + os dois digitos verificadores. */
    protected static String randomValidCnpj() {
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        StringBuilder base = new StringBuilder();
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            base.append(random.nextInt(10));
        }
        base.append(checkDigit(base.toString(), weights1));
        base.append(checkDigit(base.toString(), weights2));
        return base.toString();
    }

    private static int checkDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    protected int stockOf(Product product) {
        return jdbc.queryForObject("SELECT quantity FROM products WHERE id = ?", Integer.class, product.getId());
    }
}
