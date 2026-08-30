package com.vaibhav.ediflow.order.repository;
import com.vaibhav.ediflow.order.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.domain.*; import java.util.*;
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder,UUID>,JpaSpecificationExecutor<PurchaseOrder>{Optional<PurchaseOrder> findFirstByPoNumberAndStatusOrderByCreatedAtDesc(String poNumber,OrderStatus status);}
