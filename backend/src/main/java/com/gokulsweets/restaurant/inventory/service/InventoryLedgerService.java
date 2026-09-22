package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.entity.InventoryStockTransaction;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.repository.InventoryStockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryLedgerService {

    private final InventoryStockTransactionRepository
            transactionRepository;

    public void record(
            InventoryDailyAllocation allocation,
            InventoryReservation reservation,
            InventoryTransactionType type,
            BigDecimal quantityDelta,
            String orderNumber,
            String referenceKey,
            String reason,
            String performedBy
    ) {
        InventoryStockTransaction transaction =
                new InventoryStockTransaction();

        transaction.setBranchProduct(
                allocation.getBranchProduct()
        );
        transaction.setAllocation(allocation);
        transaction.setReservation(reservation);
        transaction.setTransactionType(type);
        transaction.setQuantityDelta(quantityDelta);
        transaction.setOrderNumber(orderNumber);
        transaction.setReferenceKey(referenceKey);
        transaction.setReason(reason);
        transaction.setPerformedBy(performedBy);

        transactionRepository.save(transaction);
    }
}

