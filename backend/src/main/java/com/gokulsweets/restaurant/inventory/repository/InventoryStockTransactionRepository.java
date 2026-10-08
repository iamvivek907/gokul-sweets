package com.gokulsweets.restaurant.inventory.repository;

import com.gokulsweets.restaurant.inventory.entity.InventoryStockTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence operations for inventory stock transaction records. */
public interface InventoryStockTransactionRepository
        extends JpaRepository<InventoryStockTransaction, Long> {}
