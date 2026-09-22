package com.gokulsweets.restaurant.kot.repository;

import com.gokulsweets.restaurant.kot.entity.KotItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KotItemRepository
        extends JpaRepository<KotItem, Long> {
}