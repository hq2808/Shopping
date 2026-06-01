package com.mini.shopee.repository;

import com.mini.shopee.entity.Order;
import com.mini.shopee.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.user = :user " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByUserWithItemsAndProducts(@Param("user") User user);
}

