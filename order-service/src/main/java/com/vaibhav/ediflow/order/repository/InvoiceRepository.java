package com.vaibhav.ediflow.order.repository;
import com.vaibhav.ediflow.order.domain.Invoice; import org.springframework.data.jpa.repository.*; import java.util.UUID;
public interface InvoiceRepository extends JpaRepository<Invoice,UUID>,JpaSpecificationExecutor<Invoice>{}
