package com.example.demo;

public class MenuSales {
    private Long menuId;
    private Long totalQuantity; // 承接 SUM() 的 Long

    // 建構子
    public MenuSales(Long menuId, Long totalQuantity) {
        this.menuId = menuId;
        this.totalQuantity = totalQuantity;
    }

    // Getters 讓 Controller 可以呼叫
    public Long getMenuId() {
        return menuId;
    }

    public Long getTotalQuantity() {
        return totalQuantity;
    }
}