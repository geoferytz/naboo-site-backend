package com.naboo;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
class InitialData implements CommandLineRunner {
    private final Products products; private final Plans plans; private final Services services; private final Users users; private final PasswordEncoder encoder;
    @Value("${app.admin-email}") String email;
    @Value("${app.admin-password}") String password;
    InitialData(Products products, Plans plans, Services services, Users users, PasswordEncoder encoder) {
        this.products=products; this.plans=plans; this.services=services; this.users=users; this.encoder=encoder;
    }
    @Override public void run(String... args) {
        if (!email.isBlank() && !password.isBlank() && users.findByEmailIgnoreCase(email).isEmpty()) {
            if (password.length() < 12) throw new IllegalStateException("ADMIN_PASSWORD must be at least 12 characters");
            AdminUser admin = new AdminUser(); admin.email=email; admin.passwordHash=encoder.encode(password); users.save(admin);
        }
        seedProduct("NABOO Sales & Management System", "sales-management", "Business Management", "Manage sales, inventory and business operations from one place.",
            "Manage Sales. Control Inventory. Grow Your Business.", "Sales management,Inventory management,Customer records,Reports", new String[]{"Starter","Business","Enterprise"},new String[]{"30000","60000",null});
        seedProduct("NABOO Hotel Management System", "hotel-management", "Hospitality", "Bring reservations, guests and hotel operations together.",
            "A practical workspace for hospitality teams.", "Reservations,Guest management,Billing,Housekeeping", new String[]{"Basic","Professional","Enterprise"},new String[]{"50000","100000",null});
        seedService("Custom Software Development","custom-software","Software built around your business requirements.","Software Development","QUOTE",null);
        seedService("Website Development","website-development","Responsive websites for businesses and organizations.","Web Development","QUOTE",null);
        seedService("Software Installation","software-installation","Application installation and configuration support.","Implementation","FROM","50000");
        seedService("Graphics Design","graphics-design","Creative visual design for brands, products and digital experiences.","Design","QUOTE",null);
        seedService("IT Consultation","it-consultation","Guidance for your next digital initiative.","Consultation","QUOTE",null);
        seedService("Maintenance and Technical Support","technical-support","Ongoing help to keep your software operational.","Support","PACKAGE",null);
    }
    private void seedProduct(String name,String slug,String category,String shortText,String description,String features,String[] names,String[] prices) {
        if (products.existsBySlug(slug)) return;
        Product p = new Product(); p.name=name; p.slug=slug; p.category=category; p.shortDescription=shortText; p.description=description; p.features=features; p.featured=true; p.status="PUBLISHED"; products.save(p);
        for (int i=0;i<names.length;i++) { PackagePlan plan=new PackagePlan(); plan.productId=p.id; plan.name=names[i]; plan.description=names[i]+" package for "+category.toLowerCase()+" teams"; plan.customPrice=prices[i]==null; plan.price=prices[i]==null?null:new BigDecimal(prices[i]); plan.displayOrder=i; plan.featured=i==1; plan.features=features; if(category.equals("Hospitality")) { plan.roomLimit=switch(names[i]){case "Basic"->"10";case "Professional"->"50";default->"Unlimited";}; plan.supportLevel=switch(names[i]){case "Basic"->"Basic";case "Professional"->"Priority";default->"Dedicated";}; } else { plan.userLimit=switch(names[i]){case "Starter"->"1";case "Business"->"5";default->"Custom";}; plan.productLimit=names[i].equals("Starter")?"500":"Unlimited"; plan.supportLevel=switch(names[i]){case "Starter"->"Basic";case "Business"->"Priority";default->"Dedicated";}; } plans.save(plan); }
    }
    private void seedService(String name,String slug,String text,String category,String priceType,String price) { if (services.findAll().stream().anyMatch(s -> s.slug.equals(slug))) return; ServiceItem s=new ServiceItem(); s.name=name; s.slug=slug; s.shortDescription=text; s.description=text; s.category=category; s.priceType=priceType; s.startingPrice=price==null?null:new BigDecimal(price); s.status="PUBLISHED"; services.save(s); }
}
