package com.vaibhav.ediflow.order.repository;

import com.vaibhav.ediflow.order.domain.EdiInterchange;
import com.vaibhav.ediflow.order.domain.InterchangeStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdiInterchangeRepository extends JpaRepository<EdiInterchange, UUID> {
    Page<EdiInterchange> findByStatus(InterchangeStatus status, Pageable pageable);
}
