package com.naboo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface Products extends JpaRepository<Product,UUID>, JpaSpecificationExecutor<Product> { java.util.List<Product> findByStatusOrderByDisplayOrderAsc(String status); java.util.Optional<Product> findBySlugAndStatus(String slug,String status); boolean existsBySlug(String slug); }
interface Plans extends JpaRepository<PackagePlan,UUID>, JpaSpecificationExecutor<PackagePlan> { java.util.List<PackagePlan> findByProductIdAndActiveOrderByDisplayOrderAsc(UUID productId,boolean active); java.util.List<PackagePlan> findByFeaturedAndActiveOrderByDisplayOrderAsc(boolean featured,boolean active); }
interface Services extends JpaRepository<ServiceItem,UUID>, JpaSpecificationExecutor<ServiceItem> { java.util.List<ServiceItem> findByStatusOrderByDisplayOrderAsc(String status); java.util.Optional<ServiceItem> findBySlugAndStatus(String slug,String status); }
interface Inquiries extends JpaRepository<Inquiry,UUID>, JpaSpecificationExecutor<Inquiry> {}
interface Demos extends JpaRepository<DemoRequest,UUID>, JpaSpecificationExecutor<DemoRequest> {}
interface Settings extends JpaRepository<Setting,String> {}
interface Users extends JpaRepository<AdminUser,UUID>, JpaSpecificationExecutor<AdminUser> { java.util.Optional<AdminUser> findByEmailIgnoreCase(String email); }
interface Audits extends JpaRepository<AuditLog,UUID>, JpaSpecificationExecutor<AuditLog> {}
