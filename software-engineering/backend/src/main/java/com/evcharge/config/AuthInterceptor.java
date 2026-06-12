package com.evcharge.config;

import com.evcharge.domain.Role;
import com.evcharge.dto.ApiResponse;
import com.evcharge.dto.AuthDto;
import com.evcharge.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Pattern WHITELIST_PATTERN = Pattern.compile(
            "^/(api/health|api/auth/(register|login|logout)|api/acceptance/.*|ws(/.*)?)$"
    );
    private static final Pattern VEHICLE_PATH_PATTERN = Pattern.compile("^/api/vehicles/([^/]+)(/.*)?$");
    private static final Pattern ADMIN_PATH_PATTERN = Pattern.compile("^/api/admin(/.*)?$");

    private final AuthService authService;
    private final boolean enabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(AuthService authService,
                           @Value("${evcharge.auth.enabled:true}") boolean enabled) {
        this.authService = authService;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!enabled) return true;

        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) return true;
        if (WHITELIST_PATTERN.matcher(uri).matches()) return true;

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED: missing bearer token");
            return false;
        }
        String token = header.substring("Bearer ".length()).trim();
        Optional<AuthDto.AuthPrincipal> maybe = authService.verifyToken(token);
        if (maybe.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED: invalid token");
            return false;
        }
        AuthDto.AuthPrincipal principal = maybe.get();
        request.setAttribute("authPrincipal", principal);

        if (ADMIN_PATH_PATTERN.matcher(uri).matches()) {
            if (principal.role() != Role.ADMIN) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN: admin role required");
                return false;
            }
            return true;
        }

        Matcher vehicleMatch = VEHICLE_PATH_PATTERN.matcher(uri);
        if (vehicleMatch.matches() && principal.role() != Role.ADMIN) {
            String pathVid = vehicleMatch.group(1);
            if (principal.vehicleId() == null || !principal.vehicleId().equalsIgnoreCase(pathVid)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN: vehicleId mismatch (token=" + principal.vehicleId() + ", path=" + pathVid + ")");
                return false;
            }
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(message));
    }
}
