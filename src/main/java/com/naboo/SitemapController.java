package com.naboo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SitemapController {
    private final Products products;
    private final String domain;
    SitemapController(Products products, @Value("${APP_DOMAIN:}") String domain) { this.products=products; this.domain=domain; }
    @GetMapping(value="/robots.txt",produces="text/plain") String robots() {
        return "User-agent: *\nDisallow: /admin/\n"+(validDomain()?"Sitemap: https://"+domain+"/sitemap.xml\n":"");
    }
    @GetMapping(value="/sitemap.xml",produces=MediaType.APPLICATION_XML_VALUE) ResponseEntity<String> sitemap() {
        if(!validDomain()) return ResponseEntity.notFound().build();
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for(String path:new String[]{"/","/about","/products","/pricing","/services","/contact","/request-demo","/privacy-policy","/terms"}) append(xml,path);
        for(Product product:products.findByStatusOrderByDisplayOrderAsc("PUBLISHED")) append(xml,"/products/"+product.slug);
        return ResponseEntity.ok(xml.append("</urlset>").toString());
    }
    private void append(StringBuilder xml,String path) { xml.append("<url><loc>https://").append(domain).append(path).append("</loc></url>"); }
    private boolean validDomain() { return domain.matches("[a-zA-Z0-9][a-zA-Z0-9.-]*[a-zA-Z0-9]") && domain.length()<=253; }
}
