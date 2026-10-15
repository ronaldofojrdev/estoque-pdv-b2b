package dev.ronaldofojr.pdv.api;

import dev.ronaldofojr.pdv.api.Dtos.LoginRequest;
import dev.ronaldofojr.pdv.api.Dtos.LoginResponse;
import dev.ronaldofojr.pdv.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(auth.login(request.username(), request.password()));
    }
}
