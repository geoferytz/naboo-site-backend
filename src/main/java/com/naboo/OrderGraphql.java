package com.naboo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Controller
class OrderGraphql {
    static final List<String> PAYMENT_METHODS=List.of("BANK","AIRTEL_MONEY","TIGO_PESA","M_PESA","HALOPESA");
    static final List<String> STATUSES=List.of("AWAITING_PAYMENT","PAYMENT_SUBMITTED","PAID","CANCELLED","REFUNDED");
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final Orders orders; private final OrderItems items; private final Plans plans; private final Products products; private final Audits audits; private final Notifications notifications;
    OrderGraphql(Orders orders,OrderItems items,Plans plans,Products products,Audits audits,Notifications notifications) { this.orders=orders; this.items=items; this.plans=plans; this.products=products; this.audits=audits; this.notifications=notifications; }

    record OrderItemInput(String packageId,int quantity) {}
    record OrderInput(String fullName,String email,String phone,String company,String paymentMethod,List<OrderItemInput> items,boolean consent,String website) {}

    private static String required(String value,int max) { if(value==null || value.isBlank() || value.length()>max) throw new IllegalArgumentException("Invalid required field"); return value.trim(); }
    private static String optional(String value,int max) { if(value!=null && value.length()>max) throw new IllegalArgumentException("Field too long"); return value==null || value.isBlank()?null:value.trim(); }
    private void audit(String action,UUID id) { audits.save(new AuditLog(SecurityContextHolder.getContext().getAuthentication().getName(),action,id)); }

    @SchemaMapping(typeName="Order",field="items") List<OrderItem> orderItems(Order order) { return items.findByOrderId(order.id); }
    @SchemaMapping(typeName="Order",field="totalAmount") String totalAmount(Order order) { return order.totalAmount.toPlainString(); }
    @SchemaMapping(typeName="Order",field="createdAt") String createdAt(Order order) { return order.createdAt.toString(); }
    @SchemaMapping(typeName="Order",field="paidAt") String paidAt(Order order) { return order.paidAt==null?null:order.paidAt.toString(); }
    @SchemaMapping(typeName="OrderItem",field="unitPrice") String unitPrice(OrderItem item) { return item.unitPrice.toPlainString(); }

    @Transactional @MutationMapping Order placeOrder(@Argument OrderInput input) {
        if(!input.consent() || (input.website()!=null && !input.website().isBlank())) throw new IllegalArgumentException("Invalid request");
        if(input.email()==null || input.email().length()>254 || !EMAIL.matcher(input.email()).matches()) throw new IllegalArgumentException("Invalid email");
        if(!PAYMENT_METHODS.contains(input.paymentMethod())) throw new IllegalArgumentException("Invalid payment method");
        if(input.items()==null || input.items().isEmpty() || input.items().size()>20) throw new IllegalArgumentException("Cart is empty");
        Order order=new Order(); order.referenceNumber="ORD-"+order.id.toString().substring(0,8).toUpperCase();
        order.fullName=required(input.fullName(),160); order.email=input.email().trim(); order.phone=required(input.phone(),40); order.company=optional(input.company(),160); order.paymentMethod=input.paymentMethod();
        BigDecimal total=BigDecimal.ZERO; String currency=null; List<OrderItem> lines=new ArrayList<>();
        for(OrderItemInput line : input.items()) {
            if(line.quantity()<1 || line.quantity()>100) throw new IllegalArgumentException("Invalid quantity");
            PackagePlan plan=plans.findById(UUID.fromString(line.packageId())).filter(p->p.active && !p.customPrice && p.price!=null).orElseThrow(()->new IllegalArgumentException("Package is not available for online purchase"));
            Product product=products.findById(plan.productId).filter(p->p.status.equals("PUBLISHED")).orElseThrow(()->new IllegalArgumentException("Product is not available"));
            if(currency==null) currency=plan.currency; else if(!currency.equals(plan.currency)) throw new IllegalArgumentException("All packages must use the same currency");
            OrderItem item=new OrderItem(); item.orderId=order.id; item.packageId=plan.id; item.productName=product.name; item.packageName=plan.name; item.billingFrequency=plan.billingFrequency; item.unitPrice=plan.price; item.quantity=line.quantity();
            total=total.add(plan.price.multiply(BigDecimal.valueOf(line.quantity()))); lines.add(item);
        }
        order.currency=currency; order.totalAmount=total; Order saved=orders.save(order); items.saveAll(lines);
        notifications.newRequest("order",saved.referenceNumber,saved.email); return saved;
    }
    @QueryMapping Order orderByReference(@Argument String referenceNumber,@Argument String email) { return orders.findByReferenceNumberAndEmailIgnoreCase(required(referenceNumber,32),required(email,254)).orElse(null); }
    @MutationMapping Order confirmOrderPayment(@Argument String referenceNumber,@Argument String email,@Argument String paymentReference,@Argument String payerPhone) {
        Order order=orders.findByReferenceNumberAndEmailIgnoreCase(required(referenceNumber,32),required(email,254)).orElseThrow(()->new IllegalArgumentException("Order not found"));
        if(!List.of("AWAITING_PAYMENT","PAYMENT_SUBMITTED").contains(order.status)) throw new IllegalArgumentException("This order can no longer be updated");
        String reference=required(paymentReference,80); if(!reference.matches("[A-Za-z0-9 ._/-]{4,80}")) throw new IllegalArgumentException("Invalid payment reference");
        order.paymentReference=reference; order.payerPhone=optional(payerPhone,40); order.status="PAYMENT_SUBMITTED"; return orders.save(order);
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping Order adminOrder(@Argument String id) { return orders.findById(UUID.fromString(id)).orElse(null); }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @QueryMapping PageResult<Order> adminOrdersPage(@Argument int page,@Argument int size,@Argument String status,@Argument String search,@Argument String sort) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid page");
        if(status!=null && !STATUSES.contains(status)) throw new IllegalArgumentException("Invalid status");
        String field="createdAt"; Sort.Direction direction=Sort.Direction.DESC;
        if(sort!=null && !sort.isBlank()) { String[] parts=sort.split(",",2); if(!Set.of("referenceNumber","fullName","email","status","totalAmount","createdAt").contains(parts[0])) throw new IllegalArgumentException("Invalid sort"); field=parts[0]; direction=parts.length>1 && parts[1].equalsIgnoreCase("asc")?Sort.Direction.ASC:Sort.Direction.DESC; }
        String text=optional(search,100); String pattern=text==null?null:"%"+text.toLowerCase(Locale.ROOT).replace("%","\\%").replace("_","\\_")+"%";
        Specification<Order> spec=(root,query,cb)->{var criteria=new ArrayList<jakarta.persistence.criteria.Predicate>();if(status!=null)criteria.add(cb.equal(root.get("status"),status));if(pattern!=null)criteria.add(cb.or(cb.like(cb.lower(root.<String>get("fullName")),pattern,'\\'),cb.like(cb.lower(root.<String>get("email")),pattern,'\\'),cb.like(cb.lower(root.<String>get("referenceNumber")),pattern,'\\'),cb.like(cb.lower(root.<String>get("paymentReference")),pattern,'\\')));return cb.and(criteria.toArray(new jakarta.persistence.criteria.Predicate[0]));};
        var result=orders.findAll(spec,PageRequest.of(page,size,Sort.by(direction,field))); return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_sales_manager')") @MutationMapping Order updateOrderStatus(@Argument String id,@Argument String status,@Argument String notes) {
        if(!STATUSES.contains(status)) throw new IllegalArgumentException("Invalid status");
        Order order=orders.findById(UUID.fromString(id)).orElseThrow(); order.status=status; order.notes=optional(notes,5000);
        if(status.equals("PAID") && order.paidAt==null) order.paidAt=Instant.now();
        orders.save(order); notifications.orderStatus(order.email,order.referenceNumber,status); audit("ORDER_"+status,order.id); return order;
    }
}
