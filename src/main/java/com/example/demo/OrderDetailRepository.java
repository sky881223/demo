package com.example.demo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    @Query("SELECT new com.example.demo.MenuSales(od.menu.id, SUM(od.quantity)) " +
       "FROM OrderDetail od GROUP BY od.menu.id")
    List<MenuSales> findAllMenuSales();
}