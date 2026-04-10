package com.example.demo;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

@Controller
public class UserController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private MenuRepository menuRepository;
    
    
    @PostMapping("/saveUser")
    public String handleForm(@RequestParam("email") String email,@RequestParam("name") String name,@RequestParam("password") String password, Model model, HttpSession session) {
        System.out.println("--- 進入/saveUser ---");
        System.out.println("--- 萬能抓取測試 ---");
        System.out.println("抓到的 Name: " + name);
        System.out.println("抓到的 Email: " + email);
        System.out.println("抓到的 Password: " + password);

        if (password.length()>=8 && password.matches(".*[A-Z].*") && password.matches(".*[a-z].*") && password.matches(".*[0-9].*")) {
            String code = sendVerificationCode(email);
            session.setAttribute("email", email);
            session.setAttribute("code", code);
            session.setAttribute("name", name);
            session.setAttribute("password", password);
            return "pincode";
        }
        else {
            model.addAttribute("error", "パスワードは8文字以上にしてください。");
            System.out.println("パスワードは8文字以上にしてください。");
            return "signup"; 
        }
    }
    @Autowired
    private JavaMailSender mailSender;
    public String sendVerificationCode(String toEmail) {

        String code = String.valueOf((int)((Math.random() * 900000) + 100000));

   
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("107407002@stud.sju.edu.tw");
        message.setTo(toEmail);
        message.setSubject("コード認証");
        message.setText("コード：" + code + "，５分鐘內有効。");
    
        try {
        mailSender.send(message); 
        } catch (Exception e) {
            System.out.println("發信失敗，錯誤原因：" + e.getMessage());
            e.printStackTrace(); 
        }
        return code; 
    }
    
    @PostMapping("/verifyCode")
    public String verifyCode(@RequestParam("verifyCode") String verifyCode, HttpSession session , Model model) {
        String correctCode = (String) session.getAttribute("code");
        if (verifyCode.equals(correctCode)) {
            System.out.println("驗證碼正確");
            if (session.getAttribute("name") != null) {
                User user = new User();
                user.setEmail((String) session.getAttribute("email"));
                user.setUsername((String) session.getAttribute("name"));
                user.setPassword((String) session.getAttribute("password"));
                session.removeAttribute("code");
                userRepository.save(user);
            }
            else if(session.getAttribute("newpassword") != null) {
                User user = userRepository.findByEmail((String) session.getAttribute("email"));
                user.setPassword((String) session.getAttribute("password"));
                session.removeAttribute("code");
                userRepository.save(user);
            }
            return "login";
        } else {
            model.addAttribute("error", "コード間違いました。もう一度入力してください。");
            return "pincode";
        }
    }
    @PostMapping("/login_2")
    public String login( @RequestParam("email") String email, @RequestParam("password") String password, Model model, HttpSession session) {
        System.out.println("正在登入");
        session.setAttribute("email", email);
        session.setAttribute("password", password);
        User user = userRepository.findByEmail(email);
        if (user != null && user.getPassword().equals(password)) {
            session.setAttribute("user", user);
            System.out.println("密碼正確");
            return "redirect:/profile";
        } else {
            model.addAttribute("error", "メールアドレスまたはパスワードが間違っています。");
            System.out.println("メールアドレスまたはパスワードが間違っています。");
            return "login";
        }
    }

    @PostMapping("/forgottenpassword_2")
     public String forgottenpassword( @RequestParam("email") String email,@RequestParam("newpassword") String newPassword, Model model, HttpSession session) {
        System.out.println("正在重置密碼");
        String code = sendVerificationCode(email);  
        session.setAttribute("email", email);
        session.setAttribute("code", code);
        session.setAttribute("password", newPassword);
        if (newPassword.length()>=8 && newPassword.matches(".*[A-Z].*") && newPassword.matches(".*[a-z].*") && newPassword.matches(".*[0-9].*")) {
            session.setAttribute("newpassword", newPassword);
            return "pincode";
            }
        else {
            model.addAttribute("error", "パスワードは8文字以上にしてください。");
            return "forgottenpassword"; 
        }
    }
        
    @PostMapping("/resetName_2")
     public String resetName(@RequestParam("newname") String newName, Model model, HttpSession session) {
        String email = (String) session.getAttribute("email");
        System.out.println("從 Session 拿到的 Email: " + email);
        String password = (String) session.getAttribute("password");
        System.out.println("從 Session 拿到的 Password: " + password);
        User user = userRepository.findByEmailAndPassword(email, password);
        if (user != null) {
            user.setUsername(newName);
            userRepository.save(user);
            model.addAttribute("message", "名前が正常にリセットされました。");
            model.addAttribute("currentUser", user);
            return "profile";
        } else {
            model.addAttribute("error", "ユーザーが見つかりませんでした。");
        }
        return "resetName";
    }

    @PostMapping("/add-to-cart")
    public String addToCart(
        @RequestParam(value = "menuIds", required = false) List<Long> menuIds, 
        @RequestParam("quantities") List<Integer> allQuantities, 
        HttpSession session , Model model) {
        User user = (User) session.getAttribute("user"); 
        System.out.println("進入 /add-to-cart");
        if (user == null) {
            return "redirect:/login"; 
        }else{
            LocalTime now = LocalTime.now(ZoneId.of("Asia/Tokyo"));
            LocalTime openTime = LocalTime.of(6, 0);
            LocalTime closeTime = LocalTime.of(23, 45);
            boolean isOpen = true;
            if (now.isBefore(openTime) || now.isAfter(closeTime)) {
                isOpen = false;
                model.addAttribute("isOpen", isOpen);
                return "menu";
                
            }
            if (menuIds == null || menuIds.isEmpty()) {
                return "redirect:/menu?error=none_selected";
            }
            Order order = new Order();
            order.setUser(user);
            order.setPaymentStatus("false");
            order.setOrderTime(LocalDateTime.now().toString());
            int totalAmount = 0;

            // 先取得所有菜單，用來對照正確的索引位置
            List<Menu> allMenus = menuRepository.findAll();

            for (Long selectedId : menuIds) {
                Menu menu = menuRepository.findById(selectedId).orElse(null);
                if (menu != null) {
                    // 找出這個菜單在所有顯示菜單中的正確索引值
                    int index = -1;
                    for (int i = 0; i < allMenus.size(); i++) {
                        if (allMenus.get(i).getId().equals(selectedId)) {
                            index = i;
                            break;
                        }
                    }

                    if (index != -1 && index < allQuantities.size()) {
                        int qty = allQuantities.get(index);
                        OrderDetail detail = new OrderDetail();
                        detail.setOrder(order);
                        detail.setMenu(menu);
                        detail.setQuantity(qty);
                        detail.setPrice(menu.getPrice());
                        totalAmount += (menu.getPrice() * qty);
                        order.getOrderDetails().add(detail);
                    }
                }
            }
            order.setTotal(totalAmount);
            orderRepository.save(order);
            model.addAttribute("order", order);
            return "paymentStatus";
        }
    }
    @PostMapping("/paymentstatus_2")
    public String paymentStatus(HttpSession session , Model model) {
        User user = (User) session.getAttribute("user"); 
        if (user == null) {
            return "redirect:/login"; 
        }else{
            Order latestOrder = orderRepository.findTopByUserOrderByIdDesc(user);
            model.addAttribute("lastOrder", latestOrder);
            latestOrder.setPaymentStatus("true");
            orderRepository.save(latestOrder);
            return "redirect:/menu";  
        }
    }  
    @PostMapping("/history_2")
    public String history(HttpSession session , Model model) {
        User user = (User) session.getAttribute("user"); 
        if (user == null) {
            return "redirect:/login"; 
        }else{ 
            List<Order> orders = orderRepository.findByUser(user);
            System.out.print("歷史訂單有多少筆:" + orders.size() + "\n");
            if (orders.size() <= 0) {
                System.out.print("歷史訂單沒有資料" + "\n");
                model.addAttribute("orders", orders);
                model.addAttribute("currentUser", user);
                return "history";
            }
            System.out.print("歷史訂單第一筆的使用者名稱:" + orders.get(0).getUser().getUsername() + "\n");
            System.out.print("歷史訂單第一筆的使用者ID:" + orders.get(0).getUser().getId() + "\n");
            System.out.print("歷史訂單第一筆的ID:" + orders.get(0).getId() + "\n");
            System.out.print("歷史訂單第一筆料理:" + orders.get(0).getOrderDetails().get(0).getMenu().getName() + "\n");
            System.out.print("歷史訂單第一筆總價:" + orders.get(0).getTotal() + "\n");
            System.out.print("歷史訂單第一筆付款狀態:" + orders.get(0).getPaymentStatus() + "\n");
            System.out.print("歷史訂單第一筆訂單時間:" + orders.get(0).getOrderTime() + "\n");
            model.addAttribute("orders", orders);
            model.addAttribute("currentUser", user);
            return "history"; 
        }
    }
    @PostMapping("/cancel-order")
    public String cancelOrder(@RequestParam("orderId") Long orderId, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user"); 
        if (user == null) {
            return "redirect:/login"; 
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getUser().getId().equals(user.getId())) {
            orderRepository.delete(order);
        }
        return history(session, model);
    }
    @PostMapping("/pay")
    public String pay(@RequestParam("id") Long id, HttpSession session , Model model) {
        User user = (User) session.getAttribute("user"); 
        if (user == null) {
            return "redirect:/login"; 
        }else{
            Order order = orderRepository.findById(id).orElse(null);
            if (order != null && order.getUser().getId().equals(user.getId())) 
            {
                order.setPaymentStatus("true");
                orderRepository.save(order);
            }
            return history(session, model);
        }
    }

    @RequestMapping("/")
    public String start() {
        return "home";
    }

    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user"); 
        if (user == null) {
            return "redirect:/login"; 
        }
        model.addAttribute("currentUser", user); 
        return "profile";
    }
    
    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login"; 
    }
    @GetMapping("/resetName")
        public String resetNamePage() {
            return "resetName"; 
    }
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); 
        return "redirect:/login"; 
    }
    @GetMapping("/forgotPassword")
    public String forgotPasswordPage() {
        return "forgottenpassword"; 
    }
    @GetMapping("/menu")
    public String menuPage(Model model) {
        List<Menu> menus = menuRepository.findAll();
        model.addAttribute("menus", menus);
        boolean isOpen = true;
        model.addAttribute("isOpen", isOpen);
        return "menu";
    }
    @GetMapping("/paymentStatus")
    public String paymentStatusPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user"); 
        model.addAttribute("currentUser", user); 
        return "paymentStatus";
    }

    @GetMapping("/history")
    public String historyPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user"); 
        model.addAttribute("currentUser", user); 
        return "history";
    }
}
