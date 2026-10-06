package com.naboo;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration @EnableMethodSecurity
class SecurityConfig {
    @Value("${app.jwt-secret}") String secret;
    private SecretKeySpec key() {
        if (secret.length() < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(Users users) { return email -> users.findByEmailIgnoreCase(email)
        .map(u -> User.withUsername(u.email).password(u.passwordHash).roles(u.role).disabled(!u.active).build())
        .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Unknown account")); }
    @Bean AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(); provider.setUserDetailsService(details); provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean JwtEncoder jwtEncoder() { return new NimbusJwtEncoder(new ImmutableSecret<>(key())); }
    @Bean JwtDecoder jwtDecoder() { return NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build(); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET,"/actuator/health","/robots.txt","/sitemap.xml","/uploads/**").permitAll().requestMatchers(HttpMethod.POST,"/auth/login","/graphql").permitAll()
                .requestMatchers(HttpMethod.POST,"/admin/uploads").authenticated().anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())).build();
    }
}
