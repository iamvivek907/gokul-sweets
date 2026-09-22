package com.gokulsweets.restaurant.inventory.repository;

import com.gokulsweets.restaurant.inventory.entity.InventoryStockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryStockTransactionRepository
        extends JpaRepository<InventoryStockTransaction, Long> {
}

