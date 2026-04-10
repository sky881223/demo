package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderDetailRepository orderDetailRepository;
    @Autowired
    private AdminRepository adminRepository;

    @GetMapping("/menu")
    public String manageMenu(Model model, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            return "redirect:/admin/adminlogin"; 
        }
        List<Menu> allMenus = menuRepository.findAll();
        model.addAttribute("menus", allMenus);
        model.addAttribute("newMenu", new Menu()); 
        return "admin_menu";
    }

    @PostMapping("/menu/add")
    public String addMenu(@ModelAttribute Menu menu, @RequestParam(value = "imageFile", required = false) MultipartFile imageFile, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser");
        if (admin == null) {
            return "redirect:/admin/adminlogin";
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String fileName = UUID.randomUUID().toString() + "_" + imageFile.getOriginalFilename();
                String uploadDir = "uploads/";
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                menu.setPhoto("/uploads/" + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        menuRepository.save(menu); 
        return "redirect:/admin/menu";
    }

    @PostMapping("/menu/delete")
    public String deleteMenu(@RequestParam Long id, Model model, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            return "redirect:/admin/adminlogin"; 
        }
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
    @PostMapping("/adminlogin_2")
    public String adminLogin(@RequestParam("admin_email") String adminEmail, @RequestParam("admin_password") String adminPassword, HttpSession session,Model model) {
        System.out.println("正在管理者登入");
        Admin admin = adminRepository.findByEmail(adminEmail);
        if (admin != null && admin.getPassword().equals(adminPassword)) {
            session.setAttribute("adminUser", admin);
            return "admin_profile";
        } else {
            model.addAttribute("error", "無効な管理者メールアドレスまたはパスワードです。");
            return "admin_login";
        }
    }
    @GetMapping("/history")
    public String adminHistory(HttpSession session,Model model){
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            return "redirect:/admin/adminlogin"; 
        }
        System.out.println("正在檢視所有歷史紀錄");
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        List<Menu> allMenus = menuRepository.findAll();
        List<MenuSales> salesData = orderDetailRepository.findAllMenuSales();
        Map<Long, Long> salesMap = new HashMap<>();
        for (MenuSales data : salesData) {
            Long qty = data.getTotalQuantity() != null ? data.getTotalQuantity() : 0L;
            salesMap.put(data.getMenuId(), qty);
        }
        model.addAttribute("menus", allMenus);
        model.addAttribute("salesMap", salesMap);

        List<Order> paymentstatusOrders = orderRepository.findByPaymentStatus("true");
        model.addAttribute("paymentstatusOrders", paymentstatusOrders);

        List<Order> unpaymentstatusOrders = orderRepository.findByPaymentStatus("false");
        model.addAttribute("unpaymentstatusOrders", unpaymentstatusOrders);

        return "admin_history";
    }

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


    @GetMapping("/admin_history")
    public String viewAdminHistory(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        return "admin_history";
    }
    @GetMapping("/adminlogin")
    public String adminLoginPage() {
        return "admin_login";
    }
    @GetMapping("/adminlogout")
    public String adminLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
    @GetMapping("/adminprofile")
    public String showAdminProfile(HttpSession session, Model model) {
        System.out.print("管理員!!!!!");
        Admin admin = (Admin) session.getAttribute("adminUser"); 
        if (admin == null) {
            System.out.print("admin == null");
            return "admin_login";
        }
        System.out.print("admin != null");
        model.addAttribute("adminUser", admin); 
        return "admin_profile";
    }
}