package com.vaibhav.ediflow.repository;
import com.vaibhav.ediflow.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PartnerRepository extends JpaRepository<Partner, UUID> { Optional<Partner> findByCode(String code); }
