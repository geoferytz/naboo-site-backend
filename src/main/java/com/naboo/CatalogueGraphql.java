package com.naboo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Controller
class CatalogueGraphql {
    private final Products products; private final Plans plans; private final Services services; private final Inquiries inquiries; private final Demos demos; private final Settings settings; private final Audits audits; private final Notifications notifications; private final Users users;
    CatalogueGraphql(Products products, Plans plans, Services services, Inquiries inquiries, Demos demos, Settings settings, Audits audits, Notifications notifications, Users users) {
        this.products=products; this.plans=plans; this.services=services; this.inquiries=inquiries; this.demos=demos; this.settings=settings; this.audits=audits; this.notifications=notifications; this.users=users;
    }
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static String required(String value,int max) { if (value==null || value.isBlank() || value.length()>max) throw new IllegalArgumentException("Invalid required field"); return value.trim(); }
    private static String optional(String value,int max) { if(value!=null && value.length()>max) throw new IllegalArgumentException("Field too long"); return value==null?null:value.trim(); }
    private static UUID uuid(String id) { return UUID.fromString(id); }
    private static String slug(String value) { String slug=required(value,160); if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new IllegalArgumentException("Invalid slug"); return slug; }
    private static String features(List<String> value) { if(value==null || value.size()>30) throw new IllegalArgumentException("Invalid features"); return String.join("\n",value.stream().map(s -> required(s,120)).toList()); }
    private static String imageUrl(String value) { String url=optional(value,500); if(url!=null && !url.isBlank() && !url.matches("https://[a-zA-Z0-9.-]+(?::[0-9]{1,5})?/[a-zA-Z0-9/_.,%?=&-]*|/uploads/[a-f0-9-]{36}\\.(png|jpg)")) throw new IllegalArgumentException("Invalid image URL"); return url; }
    private void audit(String action,UUID id) { audits.save(new AuditLog(SecurityContextHolder.getContext().getAuthentication().getName(),action,id)); }
    @SchemaMapping(typeName="Product",field="features") List<String> productFeatures(Product p) { return split(p.features); }
    @SchemaMapping(typeName="Product",field="packages") List<PackagePlan> productPlans(Product p) { return plans.findByProductIdAndActiveOrderByDisplayOrderAsc(p.id,true); }
    @SchemaMapping(typeName="Package",field="features") List<String> planFeatures(PackagePlan p) { return split(p.features); }
    @SchemaMapping(typeName="Package",field="price") String price(PackagePlan p) { return p.price==null?null:p.price.toPlainString(); }
    @SchemaMapping(typeName="Inquiry",field="createdAt") String inquiryDate(Inquiry i) { return i.createdAt.toString(); }
    @SchemaMapping(typeName="DemoRequest",field="createdAt") String demoDate(DemoRequest d) { return d.createdAt.toString(); }
    private static List<String> split(String value) { return value==null || value.isBlank()?List.of():Arrays.asList(value.split("\\n|,")); }
    @QueryMapping List<Product> products() { return products.findByStatusOrderByDisplayOrderAsc("PUBLISHED"); }
    @QueryMapping Product productBySlug(@Argument String slug) { return products.findBySlugAndStatus(slug,"PUBLISHED").orElse(null); }
    @QueryMapping List<Product> featuredProducts() { return products().stream().filter(p->p.featured).toList(); }
    @QueryMapping List<ServiceItem> services() { return services.findByStatusOrderByDisplayOrderAsc("PUBLISHED"); }
    @QueryMapping ServiceItem serviceBySlug(@Argument String slug) { return services.findBySlugAndStatus(slug,"PUBLISHED").orElse(null); }
    @QueryMapping List<PackagePlan> pricingPackages(@Argument String productSlug) { Product product=productBySlug(productSlug); return product==null?List.of():plans.findByProductIdAndActiveOrderByDisplayOrderAsc(product.id,true); }
    @QueryMapping List<PackagePlan> featuredPricingPackages() { Set<UUID> published=products().stream().map(p->p.id).collect(java.util.stream.Collectors.toSet()); return plans.findByFeaturedAndActiveOrderByDisplayOrderAsc(true,true).stream().filter(p->published.contains(p.productId)).toList(); }
    @QueryMapping List<Setting> companySettings() { return settings.findAll().stream().filter(s->!s.key.startsWith("private.")).toList(); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager','SCOPE_sales_manager','SCOPE_support_agent')") @QueryMapping Dashboard adminDashboard() { var latest=PageRequest.of(0,5,Sort.by(Sort.Direction.DESC,"createdAt")); return new Dashboard(products().size(),(int)plans.findAll().stream().filter(p->p.active).count(),services().size(),(int)inquiries.findAll().stream().filter(i->i.status.equals("NEW")).count(),(int)demos.findAll().stream().filter(d->d.status.equals("PENDING")).count(),inquiries.findAll(latest).getContent(),demos.findAll(latest).getContent()); }
    record Dashboard(int products,int packages,int services,int newInquiries,int pendingDemos,List<Inquiry> recentInquiries,List<DemoRequest> recentDemos) {}
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager','SCOPE_sales_manager')") @QueryMapping List<Product> adminProducts() { return products.findAll(); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager','SCOPE_sales_manager')") @QueryMapping Product adminProduct(@Argument String id) { return products.findById(uuid(id)).orElse(null); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @QueryMapping List<PackagePlan> adminPricingPackages() { return plans.findAll(); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @QueryMapping List<ServiceItem> adminServices() { return services.findAll(); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @QueryMapping ServiceItem adminService(@Argument String id) { return services.findById(uuid(id)).orElse(null); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping List<Inquiry> adminInquiries() { return inquiries.findAll(); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping Inquiry adminInquiry(@Argument String id) { return inquiries.findById(uuid(id)).orElse(null); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping List<DemoRequest> adminDemoRequests() { return demos.findAll(); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping DemoRequest adminDemoRequest(@Argument String id) { return demos.findById(uuid(id)).orElse(null); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping List<AdminUser> assignableUsers() { return users.findAll().stream().filter(u->u.active).toList(); }
    private static PageRequest page(int page,int size) { if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid page"); return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt")); }
    private static PageRequest page(int page,int size,String sort,Set<String> allowed,String fallback) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid page");
        String field=fallback; Sort.Direction direction=Sort.Direction.ASC;
        if(sort!=null && !sort.isBlank()) { String[] parts=sort.split(",",2); field=parts[0]; if(!allowed.contains(field)) throw new IllegalArgumentException("Invalid sort"); if(parts.length>1) { if(parts[1].equalsIgnoreCase("desc")) direction=Sort.Direction.DESC; else if(!parts[1].equalsIgnoreCase("asc")) throw new IllegalArgumentException("Invalid sort"); } }
        return PageRequest.of(page,size,Sort.by(direction,field));
    }
    private static String searchPattern(String search) { String text=optional(search,100); return text==null || text.isBlank()?null:"%"+text.toLowerCase(Locale.ROOT).replace("%","\\%").replace("_","\\_")+"%"; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager','SCOPE_sales_manager')") @QueryMapping PageResult<Product> adminProductsPage(@Argument int page,@Argument int size,@Argument String status,@Argument String search,@Argument String sort) {
        if(status!=null && !List.of("DRAFT","PUBLISHED","ARCHIVED").contains(status)) throw new IllegalArgumentException("Invalid status");
        String text=searchPattern(search);
        Specification<Product> spec=(root,query,cb)->{var criteria=new ArrayList<jakarta.persistence.criteria.Predicate>();if(status!=null)criteria.add(cb.equal(root.get("status"),status));if(text!=null)criteria.add(cb.or(cb.like(cb.lower(root.<String>get("name")),text,'\\'),cb.like(cb.lower(root.<String>get("slug")),text,'\\'),cb.like(cb.lower(root.<String>get("category")),text,'\\')));return cb.and(criteria.toArray(new jakarta.persistence.criteria.Predicate[0]));};
        var result=products.findAll(spec,page(page,size,sort,Set.of("name","slug","category","status","displayOrder"),"displayOrder")); return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @QueryMapping PageResult<PackagePlan> adminPricingPackagesPage(@Argument int page,@Argument int size,@Argument String productId,@Argument Boolean active,@Argument String search,@Argument String sort) {
        UUID selected=productId==null?null:uuid(productId); String text=searchPattern(search);
        Specification<PackagePlan> spec=(root,query,cb)->{var criteria=new ArrayList<jakarta.persistence.criteria.Predicate>();if(selected!=null)criteria.add(cb.equal(root.get("productId"),selected));if(active!=null)criteria.add(cb.equal(root.get("active"),active));if(text!=null)criteria.add(cb.or(cb.like(cb.lower(root.<String>get("name")),text,'\\'),cb.like(cb.lower(root.<String>get("description")),text,'\\')));return cb.and(criteria.toArray(new jakarta.persistence.criteria.Predicate[0]));};
        var result=plans.findAll(spec,page(page,size,sort,Set.of("name","price","billingFrequency","displayOrder"),"displayOrder")); return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @QueryMapping PageResult<ServiceItem> adminServicesPage(@Argument int page,@Argument int size,@Argument String status,@Argument String search,@Argument String sort) {
        if(status!=null && !List.of("DRAFT","PUBLISHED","ARCHIVED").contains(status)) throw new IllegalArgumentException("Invalid status");
        String text=searchPattern(search);
        Specification<ServiceItem> spec=(root,query,cb)->{var criteria=new ArrayList<jakarta.persistence.criteria.Predicate>();if(status!=null)criteria.add(cb.equal(root.get("status"),status));if(text!=null)criteria.add(cb.or(cb.like(cb.lower(root.<String>get("name")),text,'\\'),cb.like(cb.lower(root.<String>get("slug")),text,'\\'),cb.like(cb.lower(root.<String>get("category")),text,'\\')));return cb.and(criteria.toArray(new jakarta.persistence.criteria.Predicate[0]));};
        var result=services.findAll(spec,page(page,size,sort,Set.of("name","slug","category","status","displayOrder"),"displayOrder")); return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    private static <T> Specification<T> leadFilter(String status,String search) {
        String text=optional(search,100);
        return (root,query,cb) -> {
            var criteria=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(status!=null && !status.isBlank()) criteria.add(cb.equal(root.get("status"),status));
            if(text!=null && !text.isBlank()) {
                String pattern="%"+text.toLowerCase(Locale.ROOT).replace("%","\\%").replace("_","\\_")+"%";
                criteria.add(cb.or(cb.like(cb.lower(root.<String>get("fullName")),pattern,'\\'),cb.like(cb.lower(root.<String>get("email")),pattern,'\\'),cb.like(cb.lower(root.<String>get("referenceNumber")),pattern,'\\')));
            }
            return cb.and(criteria.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping PageResult<Inquiry> adminInquiriesPage(@Argument int page,@Argument int size,@Argument String status,@Argument String search,@Argument String sort) {
        if(status!=null && !List.of("NEW","IN_PROGRESS","RESPONDED","CLOSED","SPAM").contains(status)) throw new IllegalArgumentException("Invalid status");
        var result=inquiries.findAll(leadFilter(status,search),page(page,size,sort==null?"createdAt,desc":sort,Set.of("referenceNumber","fullName","email","status","createdAt"),"createdAt")); return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping PageResult<DemoRequest> adminDemoRequestsPage(@Argument int page,@Argument int size,@Argument String status,@Argument String search,@Argument String sort) {
        if(status!=null && !List.of("PENDING","APPROVED","RESCHEDULED","COMPLETED","CANCELLED").contains(status)) throw new IllegalArgumentException("Invalid status");
        var result=demos.findAll(leadFilter(status,search),page(page,size,sort==null?"createdAt,desc":sort,Set.of("referenceNumber","fullName","email","status","createdAt"),"createdAt")); return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @QueryMapping List<Setting> adminCompanySettings() { return settings.findAll(); }
    record ProductInput(String name,String slug,String shortDescription,String description,String category,String imageUrl,Boolean featured,Integer displayOrder,List<String> features) {}
    record PackageInput(String productId,String name,String description,String price,String currency,String billingFrequency,Boolean customPrice,Boolean active,Boolean featured,Integer displayOrder,String userLimit,String roomLimit,String productLimit,String supportLevel,List<String> features) {}
    record ServiceInput(String name,String slug,String shortDescription,String description,String imageUrl,String category,String priceType,String startingPrice,String currency,Integer displayOrder) {}
    record InquiryInput(String fullName,String email,String phone,String company,String subject,String productId,String message,boolean consent,String website) {}
    record QuotationInput(String fullName,String email,String phone,String company,String productId,String message,boolean consent,String website) {}
    record DemoInput(String fullName,String companyName,String email,String phone,String productId,String preferredDate,String preferredTime,String businessType,String additionalInformation,boolean consent,String website) {}
    private void validateContact(String name,String email,boolean consent,String website) { required(name,160); if(email==null || email.length()>254 || !EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Invalid email"); if(!consent || (website!=null && !website.isBlank())) throw new IllegalArgumentException("Invalid request"); }
    private UUID publishedProduct(String id) { UUID productId=uuid(id); if(products.findById(productId).filter(p->p.status.equals("PUBLISHED")).isEmpty()) throw new IllegalArgumentException("Invalid product"); return productId; }
    @MutationMapping Inquiry submitInquiry(@Argument InquiryInput input) {
        validateContact(input.fullName(),input.email(),input.consent(),input.website()); Inquiry i=new Inquiry(); i.referenceNumber="INQ-"+i.id.toString().substring(0,8).toUpperCase(); i.fullName=input.fullName().trim(); i.email=input.email().trim(); i.phone=optional(input.phone(),40); i.company=optional(input.company(),160); i.subject=required(input.subject(),120); i.message=required(input.message(),5000); i.productId=input.productId()==null?null:publishedProduct(input.productId()); Inquiry saved=inquiries.save(i); notifications.newRequest("inquiry",saved.referenceNumber,saved.email); return saved;
    }
    @MutationMapping DemoRequest requestDemo(@Argument DemoInput input) {
        validateContact(input.fullName(),input.email(),input.consent(),input.website()); DemoRequest d=new DemoRequest(); d.referenceNumber="DEMO-"+d.id.toString().substring(0,8).toUpperCase(); d.fullName=input.fullName().trim(); d.companyName=required(input.companyName(),160); d.email=input.email().trim(); d.phone=required(input.phone(),40); d.productId=publishedProduct(input.productId()); d.preferredDate=optional(input.preferredDate(),20); d.preferredTime=optional(input.preferredTime(),20); d.businessType=optional(input.businessType(),120); d.additionalInformation=optional(input.additionalInformation(),5000); DemoRequest saved=demos.save(d); notifications.newRequest("demo",saved.referenceNumber,saved.email); return saved;
    }
    @MutationMapping Inquiry requestQuotation(@Argument QuotationInput input) {
        validateContact(input.fullName(),input.email(),input.consent(),input.website()); Inquiry i=new Inquiry(); i.referenceNumber="QTE-"+i.id.toString().substring(0,8).toUpperCase(); i.fullName=input.fullName().trim(); i.email=input.email().trim(); i.phone=optional(input.phone(),40); i.company=optional(input.company(),160); i.subject="Quotation Request"; i.message=required(input.message(),5000); i.productId=input.productId()==null?null:publishedProduct(input.productId()); Inquiry saved=inquiries.save(i); notifications.newRequest("quotation",saved.referenceNumber,saved.email); return saved;
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @MutationMapping Product saveProduct(@Argument String id,@Argument ProductInput input) {
        Product p=id==null?new Product():products.findById(uuid(id)).orElseThrow(); p.name=required(input.name(),160); p.slug=slug(input.slug()); p.shortDescription=required(input.shortDescription(),500); p.description=required(input.description(),10000); p.category=required(input.category(),100); p.imageUrl=imageUrl(input.imageUrl()); p.featured=Boolean.TRUE.equals(input.featured()); p.displayOrder=input.displayOrder()==null?0:input.displayOrder(); p.features=features(input.features()); products.save(p); audit("SAVE_PRODUCT",p.id); return p;
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @MutationMapping Product setProductStatus(@Argument String id,@Argument String status) { if(!List.of("PUBLISHED","DRAFT","ARCHIVED").contains(status)) throw new IllegalArgumentException("Invalid status"); Product p=products.findById(uuid(id)).orElseThrow(); p.status=status; products.save(p); audit("PRODUCT_"+status,p.id); return p; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @MutationMapping PackagePlan savePackage(@Argument String id,@Argument PackageInput input) {
        PackagePlan p=id==null?new PackagePlan():plans.findById(uuid(id)).orElseThrow(); p.productId=uuid(input.productId()); if(!products.existsById(p.productId)) throw new IllegalArgumentException("Invalid product"); p.name=required(input.name(),120); p.description=required(input.description(),500); p.customPrice=Boolean.TRUE.equals(input.customPrice()); p.price=p.customPrice?null:new BigDecimal(required(input.price(),30)); if(p.price!=null && p.price.signum()<0) throw new IllegalArgumentException("Invalid price"); p.currency=required(input.currency(),8); if(!List.of("MONTHLY","QUARTERLY","ANNUALLY","ONE_TIME").contains(input.billingFrequency())) throw new IllegalArgumentException("Invalid frequency"); p.billingFrequency=input.billingFrequency(); p.active=input.active()==null || input.active(); p.featured=Boolean.TRUE.equals(input.featured()); p.displayOrder=input.displayOrder()==null?0:input.displayOrder(); p.userLimit=optional(input.userLimit(),80); p.roomLimit=optional(input.roomLimit(),80); p.productLimit=optional(input.productLimit(),80); p.supportLevel=optional(input.supportLevel(),80); p.features=features(input.features()); plans.save(p); audit("SAVE_PACKAGE",p.id); return p;
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @MutationMapping PackagePlan setPackageActive(@Argument String id,@Argument boolean active) { PackagePlan p=plans.findById(uuid(id)).orElseThrow(); p.active=active; plans.save(p); audit("PACKAGE_ACTIVE",p.id); return p; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @MutationMapping ServiceItem saveService(@Argument String id,@Argument ServiceInput input) { ServiceItem s=id==null?new ServiceItem():services.findById(uuid(id)).orElseThrow(); s.name=required(input.name(),160); s.slug=slug(input.slug()); s.shortDescription=required(input.shortDescription(),500); s.description=required(input.description(),10000); s.imageUrl=imageUrl(input.imageUrl()); s.category=optional(input.category(),100); if(s.category==null)s.category="General"; s.priceType=input.priceType()==null?"QUOTE":input.priceType(); if(!List.of("FIXED","FROM","QUOTE","PACKAGE").contains(s.priceType)) throw new IllegalArgumentException("Invalid price type"); s.currency=input.currency()==null?"TSh":required(input.currency(),8); s.startingPrice=input.startingPrice()==null || input.startingPrice().isBlank()?null:new BigDecimal(input.startingPrice()); if(s.startingPrice!=null && s.startingPrice.signum()<0) throw new IllegalArgumentException("Invalid price"); if(List.of("FIXED","FROM").contains(s.priceType) && s.startingPrice==null) throw new IllegalArgumentException("Starting price is required"); s.displayOrder=input.displayOrder()==null?0:input.displayOrder(); services.save(s); audit("SAVE_SERVICE",s.id); return s; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @MutationMapping ServiceItem setServiceStatus(@Argument String id,@Argument String status) { if(!List.of("PUBLISHED","DRAFT","ARCHIVED").contains(status)) throw new IllegalArgumentException("Invalid status"); ServiceItem s=services.findById(uuid(id)).orElseThrow(); s.status=status; services.save(s); audit("SERVICE_"+status,s.id); return s; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @MutationMapping Inquiry updateInquiry(@Argument String id,@Argument String status,@Argument String notes) { if(!List.of("NEW","IN_PROGRESS","RESPONDED","CLOSED","SPAM").contains(status)) throw new IllegalArgumentException("Invalid status"); Inquiry i=inquiries.findById(uuid(id)).orElseThrow(); i.status=status; i.notes=optional(notes,5000); inquiries.save(i); audit("UPDATE_INQUIRY",i.id); return i; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @MutationMapping DemoRequest updateDemo(@Argument String id,@Argument String status,@Argument String notes) { if(!List.of("PENDING","APPROVED","RESCHEDULED","COMPLETED","CANCELLED").contains(status)) throw new IllegalArgumentException("Invalid status"); DemoRequest d=demos.findById(uuid(id)).orElseThrow(); d.status=status; d.notes=optional(notes,5000); demos.save(d); notifications.demoStatus(d.email,d.referenceNumber,d.status); audit("UPDATE_DEMO",d.id); return d; }
    private UUID assignee(String userId) { if(userId==null || userId.isBlank()) return null; AdminUser user=users.findById(uuid(userId)).orElseThrow(); if(!user.active) throw new IllegalArgumentException("Cannot assign to an inactive user"); return user.id; }
    private String appendNote(String existing,String note) { String actor=SecurityContextHolder.getContext().getAuthentication().getName(); String entry="["+Instant.now()+" "+actor+"] "+required(note,2000); return existing==null || existing.isBlank()?entry:existing+"\n"+entry; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @MutationMapping Inquiry assignInquiry(@Argument String id,@Argument String userId) { Inquiry i=inquiries.findById(uuid(id)).orElseThrow(); i.assignedUserId=assignee(userId); inquiries.save(i); audit("ASSIGN_INQUIRY",i.id); return i; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @MutationMapping DemoRequest assignDemoRequest(@Argument String id,@Argument String userId) { DemoRequest d=demos.findById(uuid(id)).orElseThrow(); d.assignedUserId=assignee(userId); demos.save(d); audit("ASSIGN_DEMO",d.id); return d; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @MutationMapping Inquiry addInquiryNote(@Argument String id,@Argument String note) { Inquiry i=inquiries.findById(uuid(id)).orElseThrow(); i.notes=appendNote(i.notes,note); inquiries.save(i); audit("NOTE_INQUIRY",i.id); return i; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @MutationMapping DemoRequest addDemoRequestNote(@Argument String id,@Argument String note) { DemoRequest d=demos.findById(uuid(id)).orElseThrow(); d.notes=appendNote(d.notes,note); demos.save(d); audit("NOTE_DEMO",d.id); return d; }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')") @MutationMapping Setting updateSetting(@Argument String key,@Argument String value) {
        if(!List.of("companyName","slogan","description","mission","vision","values","email","phone","address","hours","heroTitle","heroDescription","footerDescription","seoDescription","whatsapp","facebook","instagram","linkedin","logoUrl","private.contactRecipient").contains(key)) throw new IllegalArgumentException("Invalid key");
        String normalized;
        if(List.of("logoUrl","facebook","instagram","linkedin").contains(key)) { normalized=optional(value,500); if(normalized!=null && !normalized.isBlank()) normalized=imageUrl(normalized); normalized=normalized==null?"":normalized; }
        else if(key.equals("whatsapp")) { normalized=optional(value,20); if(normalized!=null && !normalized.isBlank() && !normalized.matches("[0-9+()\\- ]{5,20}")) throw new IllegalArgumentException("Invalid WhatsApp number"); normalized=normalized==null?"":normalized; }
        else if(key.equals("private.contactRecipient")) { normalized=required(value,254); if(!EMAIL.matcher(normalized).matches()) throw new IllegalArgumentException("Invalid email"); }
        else normalized=required(value,3000);
        Setting s=settings.save(new Setting(key,normalized)); audit("UPDATE_SETTING",null); return s;
    }
}
