package com.naboo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AuthController {
    private final AuthenticationManager manager;
    private final JwtEncoder encoder;
    AuthController(AuthenticationManager manager, JwtEncoder encoder) { this.manager = manager; this.encoder = encoder; }
    record Credentials(String email, String password) {}
    record Token(String accessToken, String role) {}
    @PostMapping("/auth/login") Token login(@RequestBody Credentials input) {
        try {
            var authentication = manager.authenticate(new UsernamePasswordAuthenticationToken(input.email(), input.password()));
            var now = Instant.now();
            String scope=authentication.getAuthorities().stream().findFirst().orElseThrow().getAuthority().replaceFirst("^ROLE_", "").toLowerCase(java.util.Locale.ROOT);
            var claims = JwtClaimsSet.builder().issuer("naboo").subject(authentication.getName()).issuedAt(now)
                .expiresAt(now.plus(30, ChronoUnit.MINUTES)).claim("scope", scope).build();
            return new Token(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue(), scope);
        } catch (AuthenticationException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); }
    }
}
