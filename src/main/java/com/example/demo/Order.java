package com.example.demo;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id // 主鍵
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne // 多對一關聯使用者
    @JoinColumn(name = "user_id")
    private User user;

    private int total;           // 訂單最終總金額
    private String paymentStatus; // 付款狀態 (true/false)
    private String paymentMethod; // 新增：支付方式 (cash, credit_card)
    private int usedPoints;      // 新增：使用的抵扣點數
    private int earnedPoints;    // 新增：該訂單獲得的點數
    private String orderTime;
    private String note;
    // 一對多關聯訂單詳情，設定級聯刪除 (Order 刪除時 OrderDetail 也會一起刪除)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> orderDetails = new ArrayList<>();
}