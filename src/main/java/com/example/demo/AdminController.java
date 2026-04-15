package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired // 自動注入 Repository
    private MenuRepository menuRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderDetailRepository orderDetailRepository;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private UserRepository userRepository;

    // 獲取菜單管理頁面
    @GetMapping("/menu")
    public String manageMenu(Model model, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser"); // 檢查管理者是否已登入
        if (admin == null) {
            return "redirect:/admin/adminlogin"; 
        }
        List<Menu> allMenus = menuRepository.findAll(); // 取得所有菜單內容
        model.addAttribute("menus", allMenus);
        model.addAttribute("newMenu", new Menu()); 
        return "admin_menu";
    }

    // 新增菜單項目
    @PostMapping("/menu/add")
    public String addMenu(@ModelAttribute Menu menu, @RequestParam(value = "imageFile", required = false) MultipartFile imageFile, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser");
        if (admin == null) {
            return "redirect:/admin/adminlogin";
        }

        if (imageFile != null && !imageFile.isEmpty()) { // 處理圖片上傳
            try {
                String fileName = UUID.randomUUID().toString() + "_" + imageFile.getOriginalFilename(); // 生成唯一檔名
                String uploadDir = "uploads/";
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING); // 儲存檔案
                menu.setPhoto("/uploads/" + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        menuRepository.save(menu); // 儲存到資料庫
        return "redirect:/admin/menu";
    }

    // 刪除菜單項目
    @PostMapping("/menu/delete")
    public String deleteMenu(@RequestParam Long id, Model model, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            return "redirect:/admin/adminlogin"; 
        }
        // 檢查該菜單是否已有訂單使用，若有則不可刪除（維持資料一致性）
        boolean isUsed = orderDetailRepository.findAll().stream()
                .anyMatch(detail -> detail.getMenu().getId().equals(id));

        if (isUsed) {
            List<Menu> allMenus = menuRepository.findAll();
            model.addAttribute("menus", allMenus);
            model.addAttribute("error", "このメニューは注文に使用されているため、削除できません。");
            model.addAttribute("newMenu", new Menu());
            return "admin_menu";
        }

        menuRepository.deleteById(id);
        return "redirect:/admin/menu";
    }

    // 管理者登入處理
    @PostMapping("/adminlogin_2")
    public String adminLogin(@RequestParam("admin_email") String adminEmail, @RequestParam("admin_password") String adminPassword, HttpSession session,Model model) {
        System.out.println("正在管理者登入");
        Admin admin = adminRepository.findByEmail(adminEmail);
        if (admin != null && admin.getPassword().equals(adminPassword)) { // 驗證密碼
            session.setAttribute("adminUser", admin);
            return "redirect:/admin/adminprofile";
        } else {
            model.addAttribute("error", "無効な管理者メールアドレスまたはパスワードです。");
            return "admin_login";
        }
    }

    // 檢視所有歷史訂單與銷售統計
    @GetMapping("/history")
    public String adminHistory(HttpSession session,Model model){
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            return "redirect:/admin/adminlogin"; 
        }
        System.out.println("正在檢視所有歷史紀錄");
        List<Order> orders = orderRepository.findAll(); // 獲取所有訂單
        model.addAttribute("orders", orders);

        // 計算各項餐點的銷售總量
        List<Menu> allMenus = menuRepository.findAll();
        List<MenuSales> salesData = orderDetailRepository.findAllMenuSales(); // 從 Repository 獲取銷售統計
        Map<Long, Long> salesMap = new HashMap<>();
        for (MenuSales data : salesData) {
            Long qty = data.getTotalQuantity() != null ? data.getTotalQuantity() : 0L;
            salesMap.put(data.getMenuId(), qty);
        }
        model.addAttribute("menus", allMenus);
        model.addAttribute("salesMap", salesMap);

        // 分類已付款與未付款訂單
        List<Order> paymentstatusOrders = orderRepository.findByPaymentStatus("true");
        model.addAttribute("paymentstatusOrders", paymentstatusOrders);

        List<Order> unpaymentstatusOrders = orderRepository.findByPaymentStatus("false");
        model.addAttribute("unpaymentstatusOrders", unpaymentstatusOrders);

        return "admin_history";
    }

    // 管理者取消/刪除訂單
    @PostMapping("/admin_cancel-order")
    public String adminCancelOrder(@RequestParam("orderId") Long orderId, Model model, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            return "redirect:/admin/adminlogin";  
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            orderRepository.delete(order);
        }
        System.out.println("正在取消訂單，訂單ID: " + orderId);
        return adminHistory(session,model);
    }

    // 檢視歷史紀錄（純查詢）
    @GetMapping("/admin_history")
    public String viewAdminHistory(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        return "admin_history";
    }

    // 顯示管理登入頁面
    @GetMapping("/adminlogin")
    public String adminLoginPage() {
        return "admin_login";
    }

    // 管理者登出
    @GetMapping("/adminlogout")
    public String adminLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    // 顯示管理者的個人檔案與數據儀表板
    @GetMapping("/adminprofile")
    public String showAdminProfile(HttpSession session, Model model) {
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            return "admin_login";
        }

        // 1. 獲取統計數據
        long menuCount = menuRepository.count();
        long orderCount = orderRepository.count();
        int totalRevenue = orderRepository.findByPaymentStatus("true").stream()
                .mapToInt(Order::getTotal)
                .sum();

        model.addAttribute("adminUser", admin); 
        model.addAttribute("menuCount", menuCount);
        model.addAttribute("orderCount", orderCount);
        model.addAttribute("totalRevenue", totalRevenue);
        return "admin_profile";
    }

    // 管理者向けのユーザー一覧表示機能
    @GetMapping("/users")
    public String listUsers(HttpSession session, Model model) {
        Admin admin = (Admin) session.getAttribute("adminUser");
        if (admin == null) {
            return "redirect:/admin/adminlogin";
        }
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        return "admin_users";
    }

    // ユーザー削除機能
    @PostMapping("/users/delete")
    @Transactional
    public String deleteUser(@RequestParam("userId") Long userId, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser");
        if (admin == null) {
            return "redirect:/admin/adminlogin";
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            // 整合性を保つため、ユーザーに関連する注文を先に削除
            List<Order> userOrders = orderRepository.findByUser(user);
            orderRepository.deleteAll(userOrders);
            userRepository.delete(user);
        }
        return "redirect:/admin/users";
    }
}