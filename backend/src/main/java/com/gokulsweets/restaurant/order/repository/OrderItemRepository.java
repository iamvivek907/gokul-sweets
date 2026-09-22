package com.gokulsweets.restaurant.order.repository;

import com.gokulsweets.restaurant.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
