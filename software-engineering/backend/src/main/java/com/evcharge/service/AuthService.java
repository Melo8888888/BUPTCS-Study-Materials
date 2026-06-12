package com.evcharge.service;

import com.evcharge.domain.Role;
import com.evcharge.dto.AuthDto;
import com.evcharge.persistence.UserRecord;
import com.evcharge.persistence.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_LEN_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository userRepository;
    private final String secret;
    private final long ttlHours;
    private SecretKey signingKey;

    public AuthService(UserRepository userRepository,
                       @Value("${evcharge.auth.secret}") String secret,
                       @Value("${evcharge.auth.ttlHours:24}") long ttlHours) {
        this.userRepository = userRepository;
        this.secret = secret;
        this.ttlHours = ttlHours;
    }

    @PostConstruct
    void init() {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("evcharge.auth.secret must be at least 32 bytes for HS256 (current: " + bytes.length + ")");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken: " + request.username());
        }
        Role role = parseRole(request.role());
        if (role == Role.USER && (request.vehicleId() == null || request.vehicleId().isBlank())) {
            throw new IllegalArgumentException("vehicleId is required for USER role");
        }

        byte[] saltBytes = new byte[SALT_BYTES];
        RNG.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String passwordHash = hashPassword(request.password(), salt);

        UserRecord record = new UserRecord(
                UUID.randomUUID().toString(),
                request.username(),
                passwordHash,
                salt,
                request.vehicleId(),
                request.phone(),
                role,
                LocalDateTime.now()
        );
        userRepository.save(record);
        return issueToken(record);
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        UserRecord record = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        String hashed = hashPassword(request.password(), record.getSalt());
        if (!constantTimeEquals(hashed, record.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return issueToken(record);
    }

    public Optional<AuthDto.AuthPrincipal> verifyToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String vid = claims.get("vid", String.class);
            String roleStr = claims.get("role", String.class);
            if (userId == null || roleStr == null) return Optional.empty();
            return Optional.of(new AuthDto.AuthPrincipal(userId, username, vid, Role.valueOf(roleStr)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private AuthDto.AuthResponse issueToken(UserRecord record) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofHours(ttlHours));
        String token = Jwts.builder()
                .subject(record.getId())
                .claim("username", record.getUsername())
                .claim("vid", record.getVehicleId())
                .claim("role", record.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new AuthDto.AuthResponse(
                token,
                record.getId(),
                record.getUsername(),
                record.getVehicleId(),
                record.getRole(),
                LocalDateTime.ofInstant(expiry, ZoneId.systemDefault())
        );
    }

    private Role parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) return Role.USER;
        try {
            return Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + roleStr);
        }
    }

    private String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_LEN_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
