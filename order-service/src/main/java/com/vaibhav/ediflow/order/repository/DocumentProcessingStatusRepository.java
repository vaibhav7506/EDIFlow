package com.vaibhav.ediflow.order.repository;

import com.vaibhav.ediflow.order.domain.DocumentProcessingStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentProcessingStatusRepository extends JpaRepository<DocumentProcessingStatus, UUID> { }
