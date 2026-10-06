package com.naboo;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SitemapControllerTest {
    @Test void includesPublishedProductsOnly() {
        Products products=mock(Products.class);Product published=new Product();published.slug="sales-management";
        when(products.findByStatusOrderByDisplayOrderAsc("PUBLISHED")).thenReturn(List.of(published));
        SitemapController sitemap=new SitemapController(products,"naboo.example");
        assertTrue(sitemap.sitemap().getBody().contains("https://naboo.example/products/sales-management"));
        assertTrue(sitemap.robots().contains("Disallow: /admin/"));
        verify(products).findByStatusOrderByDisplayOrderAsc("PUBLISHED");
    }
    @Test void hasNoSitemapWithoutConfiguredDomain() {
        SitemapController sitemap=new SitemapController(mock(Products.class),"");
        assertEquals(404,sitemap.sitemap().getStatusCode().value());
    }
}
