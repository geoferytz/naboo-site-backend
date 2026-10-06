package com.naboo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogueGraphqlTest {
    Products products=mock(Products.class); Plans plans=mock(Plans.class); Services services=mock(Services.class);
    Inquiries inquiries=mock(Inquiries.class); Demos demos=mock(Demos.class); Settings settings=mock(Settings.class); Audits audits=mock(Audits.class); Users users=mock(Users.class); Notifications notifications=mock(Notifications.class);
    CatalogueGraphql graphql;
    @BeforeEach void setUp(){graphql=new CatalogueGraphql(products,plans,services,inquiries,demos,settings,audits,notifications,users);}
    @Test void publicCatalogueOnlyUsesPublishedProducts(){Product p=new Product();p.status="PUBLISHED";when(products.findByStatusOrderByDisplayOrderAsc("PUBLISHED")).thenReturn(List.of(p));assertEquals(List.of(p),graphql.products());verify(products,never()).findAll();}
    @Test void unpublishedProductIsNotReturned(){when(products.findBySlugAndStatus("draft","PUBLISHED")).thenReturn(Optional.empty());assertNull(graphql.productBySlug("draft"));}
    @Test void serviceBySlugOnlyUsesPublishedServices(){ServiceItem service=new ServiceItem();service.status="PUBLISHED";when(services.findBySlugAndStatus("installation","PUBLISHED")).thenReturn(Optional.of(service));assertEquals(service,graphql.serviceBySlug("installation"));}
    @Test void featuredPackagesExcludeUnpublishedProducts(){Product product=new Product();product.status="PUBLISHED";PackagePlan plan=new PackagePlan();plan.productId=product.id;when(products.findByStatusOrderByDisplayOrderAsc("PUBLISHED")).thenReturn(List.of(product));when(plans.findByFeaturedAndActiveOrderByDisplayOrderAsc(true,true)).thenReturn(List.of(plan));assertEquals(List.of(plan),graphql.featuredPricingPackages());}
    @Test void quotationGetsReferenceAndInitialStatus(){var input=new CatalogueGraphql.QuotationInput("Visitor","visitor@example.com",null,"Company",null,"Please quote",true,"");when(inquiries.save(any())).thenAnswer(inv->inv.getArgument(0));Inquiry result=graphql.requestQuotation(input);assertTrue(result.referenceNumber.matches("QTE-[A-F0-9]{8}"));assertEquals("NEW",result.status);assertEquals("Quotation Request",result.subject);verify(inquiries).save(result);}
    @Test void inquiryRequiresConsentAndValidEmail(){var input=new CatalogueGraphql.InquiryInput("Visitor","invalid",null,null,"Product Inquiry",null,"Hello",true,"");assertThrows(IllegalArgumentException.class,()->graphql.submitInquiry(input));verifyNoInteractions(inquiries);}
    @Test void inquiryGetsReferenceAndInitialStatus(){var input=new CatalogueGraphql.InquiryInput("Visitor","visitor@example.com",null,null,"Product Inquiry",null,"Please call me",true,"");when(inquiries.save(any())).thenAnswer(inv->inv.getArgument(0));Inquiry result=graphql.submitInquiry(input);assertTrue(result.referenceNumber.matches("INQ-[A-F0-9]{8}"));assertEquals("NEW",result.status);verify(inquiries).save(result);verify(notifications).newRequest("inquiry",result.referenceNumber,"visitor@example.com");}
    @Test void productListIsPagedAndRejectsUnsafeSort(){
        Product product=new Product();
        when(products.findAll(any(org.springframework.data.jpa.domain.Specification.class),any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(product)));
        var result=graphql.adminProductsPage(0,20,"PUBLISHED","sales","name,asc");
        assertEquals(List.of(product),result.items());assertEquals(1,result.totalItems());
        assertThrows(IllegalArgumentException.class,()->graphql.adminProductsPage(0,20,null,null,"passwordHash,asc"));
    }
    @Test void serviceListIsPagedAndStatusFiltered(){
        ServiceItem service=new ServiceItem();
        when(services.findAll(any(org.springframework.data.jpa.domain.Specification.class),any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(service)));
        var result=graphql.adminServicesPage(0,20,"PUBLISHED","support","name,asc");
        assertEquals(List.of(service),result.items());assertEquals(1,result.totalPages());
        assertThrows(IllegalArgumentException.class,()->graphql.adminServicesPage(0,20,"UNKNOWN",null,null));
    }
    @Test void inquiryListIsPagedAndStatusFiltered(){
        Inquiry request=new Inquiry();request.status="NEW";
        when(inquiries.findAll(any(org.springframework.data.jpa.domain.Specification.class),any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(request)));
        var result=graphql.adminInquiriesPage(0,20,"NEW","Visitor","createdAt,desc");
        assertEquals(1,result.totalItems());assertEquals(List.of(request),result.items());
        assertThrows(IllegalArgumentException.class,()->graphql.adminInquiriesPage(-1,20,null,null,null));
    }
    @Test void demoRejectsUnknownProduct(){UUID id=UUID.randomUUID();var input=new CatalogueGraphql.DemoInput("Visitor","Company","visitor@example.com","123",id.toString(),null,null,null,null,true,"");assertThrows(IllegalArgumentException.class,()->graphql.requestDemo(input));verifyNoInteractions(demos);}
    @Test void assignableUsersOnlyReturnsActiveAccounts(){AdminUser active=new AdminUser();AdminUser inactive=new AdminUser();inactive.active=false;when(users.findAll()).thenReturn(List.of(active,inactive));assertEquals(List.of(active),graphql.assignableUsers());}
    @Test void inquiryNoteIsTimestampedAndAppended(){org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@example.test",null));try{Inquiry inquiry=new Inquiry();inquiry.notes="Existing note";when(inquiries.findById(inquiry.id)).thenReturn(Optional.of(inquiry));when(inquiries.save(any())).thenAnswer(inv->inv.getArgument(0));Inquiry result=graphql.addInquiryNote(inquiry.id.toString(),"Followed up");assertTrue(result.notes.contains("Existing note"));assertTrue(result.notes.contains("admin@example.test"));assertTrue(result.notes.contains("Followed up"));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}
    @Test void demoIsPendingNotAutomaticallyApproved(){Product p=new Product();p.status="PUBLISHED";when(products.findById(p.id)).thenReturn(Optional.of(p));when(demos.save(any())).thenAnswer(inv->inv.getArgument(0));var input=new CatalogueGraphql.DemoInput("Visitor","Company","visitor@example.com","123",p.id.toString(),null,null,null,null,true,"");DemoRequest result=graphql.requestDemo(input);assertEquals("PENDING",result.status);assertTrue(result.referenceNumber.startsWith("DEMO-"));}
}
