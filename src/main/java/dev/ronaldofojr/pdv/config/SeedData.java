package dev.ronaldofojr.pdv.config;

import dev.ronaldofojr.pdv.domain.AppUser;
import dev.ronaldofojr.pdv.domain.Role;
import dev.ronaldofojr.pdv.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria um admin e um vendedor na primeira execucao. Troque as senhas por variavel de ambiente. */
@Component
public class SeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties properties;

    public SeedData(AppUserRepository users, PasswordEncoder encoder, AppProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        users.save(new AppUser("admin", encoder.encode(properties.seed().adminPassword()), Role.ADMIN));
        users.save(new AppUser("vendedor", encoder.encode(properties.seed().sellerPassword()), Role.SELLER));
        log.warn("Usuarios iniciais criados (admin e vendedor). Troque as senhas padrao em producao.");
    }
}
