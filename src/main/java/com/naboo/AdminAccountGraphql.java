package com.naboo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;

@Controller
class AdminAccountGraphql {
    private final Users users;
    private final Audits audits;
    private final PasswordEncoder encoder;
    AdminAccountGraphql(Users users, Audits audits, PasswordEncoder encoder) { this.users=users; this.audits=audits; this.encoder=encoder; }
    private void audit(String action, UUID id) { audits.save(new AuditLog(SecurityContextHolder.getContext().getAuthentication().getName(),action,id)); }
    @SchemaMapping(typeName="AuditEntry",field="createdAt") String createdAt(AuditLog log) { return log.createdAt.toString(); }
    @PreAuthorize("hasAuthority('SCOPE_admin')") @QueryMapping List<AdminUser> adminUsers() { return users.findAll(); }
    private static PageRequest page(int page,int size,String sort,Set<String> allowed,String fallback) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid page");
        String field=fallback; Sort.Direction direction=Sort.Direction.ASC;
        if(sort!=null && !sort.isBlank()) { String[] parts=sort.split(",",2); field=parts[0]; if(!allowed.contains(field)) throw new IllegalArgumentException("Invalid sort"); if(parts.length>1) { if(parts[1].equalsIgnoreCase("desc")) direction=Sort.Direction.DESC; else if(!parts[1].equalsIgnoreCase("asc")) throw new IllegalArgumentException("Invalid sort"); } }
        return PageRequest.of(page,size,Sort.by(direction,field));
    }
    private static String pattern(String search) { if(search!=null && search.length()>100) throw new IllegalArgumentException("Search too long"); return search==null || search.isBlank()?null:"%"+search.toLowerCase(Locale.ROOT).replace("%","\\%").replace("_","\\_")+"%"; }
    @PreAuthorize("hasAuthority('SCOPE_admin')") @QueryMapping PageResult<AdminUser> adminUsersPage(@Argument int page,@Argument int size,@Argument String role,@Argument Boolean active,@Argument String search,@Argument String sort) {
        if(role!=null && !List.of("ADMIN","CONTENT_MANAGER","SALES_MANAGER","SUPPORT_AGENT").contains(role)) throw new IllegalArgumentException("Invalid role"); String text=pattern(search);
        Specification<AdminUser> spec=(root,query,cb)->{var criteria=new ArrayList<jakarta.persistence.criteria.Predicate>();if(role!=null)criteria.add(cb.equal(root.get("role"),role));if(active!=null)criteria.add(cb.equal(root.get("active"),active));if(text!=null)criteria.add(cb.like(cb.lower(root.<String>get("email")),text,'\\'));return cb.and(criteria.toArray(new jakarta.persistence.criteria.Predicate[0]));};
        var result=users.findAll(spec,page(page,size,sort,Set.of("email","role","active"),"email")); return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    @PreAuthorize("hasAuthority('SCOPE_admin')") @QueryMapping List<AuditLog> adminAuditLogs(@Argument Integer limit) { return audits.findAll(PageRequest.of(0,limit==null?30:Math.clamp(limit,1,100),Sort.by(Sort.Direction.DESC,"createdAt"))).getContent(); }
    @PreAuthorize("hasAuthority('SCOPE_admin')") @QueryMapping PageResult<AuditLog> adminAuditLogsPage(@Argument int page,@Argument int size,@Argument String search,@Argument String sort) {
        String text=pattern(search); Specification<AuditLog> spec=(root,query,cb)->{var criteria=new ArrayList<jakarta.persistence.criteria.Predicate>();if(text!=null)criteria.add(cb.or(cb.like(cb.lower(root.<String>get("actor")),text,'\\'),cb.like(cb.lower(root.<String>get("action")),text,'\\')));return cb.and(criteria.toArray(new jakarta.persistence.criteria.Predicate[0]));};
        var result=audits.findAll(spec,page(page,size,sort==null?"createdAt,desc":sort,Set.of("actor","action","createdAt"),"createdAt"));
        return new PageResult<>(result.getContent(),Math.toIntExact(result.getTotalElements()),result.getTotalPages());
    }
    private static String role(String role) { if(!List.of("ADMIN","CONTENT_MANAGER","SALES_MANAGER","SUPPORT_AGENT").contains(role)) throw new IllegalArgumentException("Invalid role"); return role; }
    private static String password(String value) { if(value==null || value.length()<12 || value.length()>128) throw new IllegalArgumentException("Invalid password"); return value; }
    @PreAuthorize("hasAuthority('SCOPE_admin')") @MutationMapping AdminUser createAdminUser(@Argument String email,@Argument String password,@Argument String role) {
        if(email==null || email.length()>254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Invalid email");
        if(users.findByEmailIgnoreCase(email).isPresent()) throw new IllegalArgumentException("Email already used");
        AdminUser user=new AdminUser(); user.email=email.trim().toLowerCase(); user.passwordHash=encoder.encode(password(password)); user.role=role(role); users.save(user); audit("CREATE_USER",user.id); return user;
    }
    @PreAuthorize("hasAuthority('SCOPE_admin')") @MutationMapping AdminUser updateAdminUser(@Argument String id,@Argument String role,@Argument boolean active) {
        AdminUser user=users.findById(UUID.fromString(id)).orElseThrow();
        if(user.active && user.role.equals("ADMIN") && (!active || !role.equals("ADMIN")) && users.findAll().stream().filter(u->u.active && u.role.equals("ADMIN")).count()==1) throw new IllegalArgumentException("Cannot disable the last administrator");
        user.role=role(role); user.active=active; users.save(user); audit("UPDATE_USER",user.id); return user;
    }
    @PreAuthorize("hasAuthority('SCOPE_admin')") @MutationMapping AdminUser resetAdminPassword(@Argument String id,@Argument String password) {
        AdminUser user=users.findById(UUID.fromString(id)).orElseThrow(); user.passwordHash=encoder.encode(password(password)); users.save(user); audit("RESET_PASSWORD",user.id); return user;
    }
}
