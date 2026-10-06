package com.naboo;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class AuthControllerTest {
    @Test void issuesDecodableShortLivedToken() {
        var key=new SecretKeySpec("test-only-secret-at-least-thirty-two-characters".getBytes(StandardCharsets.UTF_8),"HmacSHA256");
        AuthenticationManager manager=mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("admin@example.test",null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        var controller=new AuthController(manager,new NimbusJwtEncoder(new ImmutableSecret<>(key)));
        var token=controller.login(new AuthController.Credentials("admin@example.test","password"));
        var jwt=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token.accessToken());
        assertEquals("admin@example.test",jwt.getSubject());
        assertEquals("admin",jwt.getClaimAsString("scope"));
        assertTrue(jwt.getExpiresAt().isBefore(jwt.getIssuedAt().plusSeconds(1801)));
    }
}
