package com.vaibhav.ediflow.order.repository;
import com.vaibhav.ediflow.order.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface DeadLetterRepository extends JpaRepository<DeadLetterRecord,UUID>{long countByStatus(DeadLetterStatus status);}
