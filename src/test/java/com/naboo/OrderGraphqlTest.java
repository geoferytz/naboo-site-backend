package com.naboo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderGraphqlTest {
    Orders orders=mock(Orders.class); OrderItems items=mock(OrderItems.class); Plans plans=mock(Plans.class); Products products=mock(Products.class);
    Audits audits=mock(Audits.class); Notifications notifications=mock(Notifications.class);
    OrderGraphql graphql=new OrderGraphql(orders,items,plans,products,audits,notifications);
    Product product=new Product(); PackagePlan plan=new PackagePlan();

    @BeforeEach void setUp() {
        product.name="NABOO Sales"; product.status="PUBLISHED";
        plan.productId=product.id; plan.name="Starter"; plan.price=new BigDecimal("30000"); plan.currency="TSh"; plan.billingFrequency="MONTHLY"; plan.active=true;
        when(plans.findById(plan.id)).thenReturn(Optional.of(plan)); when(products.findById(product.id)).thenReturn(Optional.of(product));
        when(orders.save(any())).thenAnswer(inv->inv.getArgument(0));
    }

    @Test void placesOrderWithSnapshotTotal() {
        var input=new OrderGraphql.OrderInput("Buyer","buyer@example.com","0712345678",null,"M_PESA",List.of(new OrderGraphql.OrderItemInput(plan.id.toString(),2)),true,"");
        Order order=graphql.placeOrder(input);
        assertEquals(new BigDecimal("60000"),order.totalAmount); assertEquals("AWAITING_PAYMENT",order.status); assertTrue(order.referenceNumber.startsWith("ORD-"));
        verify(items).saveAll(argThat((Iterable<OrderItem> lines)->lines.iterator().next().unitPrice.equals(new BigDecimal("30000"))));
        verify(notifications).newRequest("order",order.referenceNumber,"buyer@example.com");
    }

    @Test void rejectsCustomPricePackagesAndUnknownMethods() {
        plan.customPrice=true;
        var input=new OrderGraphql.OrderInput("Buyer","buyer@example.com","0712345678",null,"M_PESA",List.of(new OrderGraphql.OrderItemInput(plan.id.toString(),1)),true,"");
        assertThrows(IllegalArgumentException.class,()->graphql.placeOrder(input));
        plan.customPrice=false;
        assertThrows(IllegalArgumentException.class,()->graphql.placeOrder(new OrderGraphql.OrderInput("Buyer","buyer@example.com","0712345678",null,"CASH",List.of(new OrderGraphql.OrderItemInput(plan.id.toString(),1)),true,"")));
        assertThrows(IllegalArgumentException.class,()->graphql.placeOrder(new OrderGraphql.OrderInput("Buyer","buyer@example.com","0712345678",null,"M_PESA",List.of(),true,"")));
    }

    @Test void customerConfirmationMovesOrderToSubmitted() {
        Order order=new Order(); order.referenceNumber="ORD-TEST1234"; order.email="buyer@example.com"; order.status="AWAITING_PAYMENT";
        when(orders.findByReferenceNumberAndEmailIgnoreCase("ORD-TEST1234","buyer@example.com")).thenReturn(Optional.of(order));
        Order updated=graphql.confirmOrderPayment("ORD-TEST1234","buyer@example.com","MP240101.1234.A1","0712345678");
        assertEquals("PAYMENT_SUBMITTED",updated.status); assertEquals("MP240101.1234.A1",updated.paymentReference);
        order.status="PAID";
        assertThrows(IllegalArgumentException.class,()->graphql.confirmOrderPayment("ORD-TEST1234","buyer@example.com","X",null));
    }

    @Test void adminMarkingPaidRecordsTimestampAndAudit() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@example.test",null));
        try {
            Order order=new Order(); order.email="buyer@example.com"; order.referenceNumber="ORD-1"; when(orders.findById(order.id)).thenReturn(Optional.of(order));
            Order result=graphql.updateOrderStatus(order.id.toString(),"PAID","Verified against bank statement");
            assertEquals("PAID",result.status); assertNotNull(result.paidAt); verify(audits).save(any()); verify(notifications).orderStatus("buyer@example.com","ORD-1","PAID");
            assertThrows(IllegalArgumentException.class,()->graphql.updateOrderStatus(order.id.toString(),"SHIPPED",null));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
