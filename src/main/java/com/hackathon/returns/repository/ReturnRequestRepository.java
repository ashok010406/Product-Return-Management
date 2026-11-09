package com.hackathon.returns.repository;

import com.hackathon.returns.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    boolean existsByPurchase_IdAndStatusNot(Long purchaseId, String status);
    boolean existsByPurchase_Id(Long purchaseId);
}
