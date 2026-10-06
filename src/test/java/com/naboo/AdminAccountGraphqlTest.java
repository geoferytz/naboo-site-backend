package com.naboo;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAccountGraphqlTest {
    Users users=mock(Users.class);
    Audits audits=mock(Audits.class);
    AdminAccountGraphql graphql=new AdminAccountGraphql(users,audits,mock(PasswordEncoder.class));

    @Test void userListIsPagedAndRejectsUnsafeSort() {
        AdminUser user=new AdminUser();
        when(users.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        var result=graphql.adminUsersPage(0,20,"ADMIN",true,"admin","email,asc");
        assertEquals(List.of(user),result.items());
        assertThrows(IllegalArgumentException.class,()->graphql.adminUsersPage(0,20,null,null,null,"passwordHash,asc"));
    }

    @Test void auditListIsPagedAndSearchable() {
        AuditLog entry=new AuditLog("admin@example.test","SAVE_PRODUCT",null);
        when(audits.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));
        var result=graphql.adminAuditLogsPage(0,20,"save",null);
        assertEquals(List.of(entry),result.items());
        assertEquals(1,result.totalPages());
    }
}
