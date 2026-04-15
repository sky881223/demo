package com.example.demo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    // 使用 JPQL 進行統計查詢，回傳每個菜單 ID 對應的銷售總數量
    @Query("SELECT new com.example.demo.MenuSales(od.menu.id, SUM(od.quantity)) " +
       "FROM OrderDetail od GROUP BY od.menu.id")
    List<MenuSales> findAllMenuSales();
}