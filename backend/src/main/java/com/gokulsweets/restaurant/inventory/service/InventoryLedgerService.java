package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.entity.InventoryStockTransaction;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.repository.InventoryStockTransactionRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Coordinates inventory ledger operations. */
@Service
@RequiredArgsConstructor
public class InventoryLedgerService {

    private final InventoryStockTransactionRepository transactionRepository;

    /**
     * Records inventory ledger data.
     *
     * <p>Delegates to {@code transactionRepository.save(...)}.
     *
     * @param allocation the allocation supplied to this method
     * @param reservation the reservation supplied to this method
     * @param type the type supplied to this method
     * @param quantityDelta the quantity delta supplied to this method
     * @param orderNumber the order number supplied to this method
     * @param referenceKey the reference key supplied to this method
     * @param reason the reason supplied to this method
     * @param performedBy the performed by supplied to this method
     */
    public void record(
            InventoryDailyAllocation allocation,
            InventoryReservation reservation,
            InventoryTransactionType type,
            BigDecimal quantityDelta,
            String orderNumber,
            String referenceKey,
            String reason,
            String performedBy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryLedgerService.class,
                        "record(InventoryDailyAllocation,InventoryReservation,InventoryTransactionType,BigDecimal,String,String,String,String)");
        try {
            InventoryStockTransaction transaction = new InventoryStockTransaction();
            transaction.setBranchProduct(allocation.getBranchProduct());
            transaction.setAllocation(allocation);
            transaction.setReservation(reservation);
            transaction.setTransactionType(type);
            transaction.setQuantityDelta(quantityDelta);
            transaction.setOrderNumber(orderNumber);
            transaction.setReferenceKey(referenceKey);
            transaction.setReason(reason);
            transaction.setPerformedBy(performedBy);
            transactionRepository.save(transaction);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryLedgerService.class,
                    "record(InventoryDailyAllocation,InventoryReservation,InventoryTransactionType,BigDecimal,String,String,String,String)");
        }
    }
}
