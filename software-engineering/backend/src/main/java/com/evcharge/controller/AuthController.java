package com.evcharge.controller;

import com.evcharge.dto.ApiResponse;
import com.evcharge.dto.AuthDto;
import com.evcharge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthDto.AuthResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDto.AuthResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout() {
        return ApiResponse.ok(Map.of("loggedOut", true));
    }

    @GetMapping("/whoami")
    public ApiResponse<AuthDto.WhoamiResponse> whoami(HttpServletRequest request) {
        Object attr = request.getAttribute("authPrincipal");
        if (!(attr instanceof AuthDto.AuthPrincipal principal)) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(new AuthDto.WhoamiResponse(
                principal.userId(),
                principal.username(),
                principal.vehicleId(),
                principal.role()
        ));
    }
}
