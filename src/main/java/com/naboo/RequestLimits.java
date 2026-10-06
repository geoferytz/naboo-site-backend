package com.naboo;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class GraphqlLimits {
    @Bean GraphQlSourceBuilderCustomizer limits() { return builder -> builder.instrumentation(List.of(new MaxQueryDepthInstrumentation(8),new MaxQueryComplexityInstrumentation(120))); }
}
@Component
class RequestLimits extends OncePerRequestFilter {
    private record Window(long minute, AtomicInteger count) {}
    private final ConcurrentHashMap<String,Window> counters = new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request.getMethod().equals("POST") && (request.getRequestURI().equals("/graphql") || request.getRequestURI().equals("/auth/login"))) {
            long minute=Instant.now().getEpochSecond()/60;
            if(counters.size()>10000) counters.entrySet().removeIf(entry -> entry.getValue().minute()<minute);
            String key=request.getRemoteAddr()+request.getRequestURI();
            Window window=counters.compute(key,(k,old)->old==null||old.minute()!=minute?new Window(minute,new AtomicInteger(0)):old);
            if(window.count().getAndIncrement()>(request.getRequestURI().equals("/auth/login")?10:120)) { response.sendError(429,"Too many requests"); return; }
        }
        chain.doFilter(request,response);
    }
}
