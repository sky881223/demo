package com.example.demo;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

@Controller
public class UserController {
    @Autowired // 注入所需的服務與 Repository
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private MenuRepository menuRepository;

    // 統一密碼強度驗證：至少8位，含大小寫字母與數字
    private boolean isValidPassword(String password) {
        return password != null && password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*[0-9].*");
    }

    // 註冊表單提交：驗證密碼強度並發送電子郵件驗證碼
    @PostMapping("/saveUser")
    public String handleForm(@RequestParam("email") String email, @RequestParam("name") String name,
            @RequestParam("password") String password, Model model, HttpSession session) {
        if (isValidPassword(password)) {
            String code = sendVerificationCode(email);
            session.setAttribute("email", email);
            session.setAttribute("code", code);
            session.setAttribute("name", name);
            session.setAttribute("password", password);
            session.removeAttribute("newpassword"); // 確保不會誤判為重設密碼流程
            return "pincode";
        } else {
            model.addAttribute("error", "パスワードは8文字以上にしてください。");
            System.out.println("パスワードは8文字以上にしてください。");
            return "signup";
        }
    }

    @Autowired
    private JavaMailSender mailSender;

    // 發送驗證碼郵件
    public String sendVerificationCode(String toEmail) {
        // 隨機產生 6 位數驗證碼
        String code = String.valueOf((int) ((Math.random() * 900000) + 100000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("107407002@stud.sju.edu.tw");
        message.setTo(toEmail);
        message.setSubject("コード認証");
        message.setText("確認コードのお知らせ。\r\n以下の確認コードをサイトに入力してください。\r\nコード：" + code + "です。\r\nこのコードは５分鐘內有効。\r\n※このメールは送信専用メールアドレスから自動送信されています。\r\n" + //
                        "　ご返信いただいてもお答えできませんのでご了承ください。");

        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("發信失敗，錯誤原因：" + e.getMessage());
            e.printStackTrace();
        }
        return code;
    }

    // 付款成功後發送訂單確認信
    public void sendOrderConfirmationEmail(User user, Order order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("107407002@stud.sju.edu.tw"); // 請確保這裡的發件人郵箱與你的郵件服務配置一致
        message.setTo(user.getEmail());
        message.setSubject("ご注文と支払いが完了しました - 注文ID: " + order.getId());

        StringBuilder emailBody = new StringBuilder();
        emailBody.append(user.getUsername()).append("様、\n\n");
        emailBody.append("ご注文と支払いが正常に完了しました。\n");
        emailBody.append("ご注文の詳細は以下の通りです。\n\n");
        emailBody.append("注文ID: ").append(order.getId()).append("\n");
        emailBody.append("注文日時: ").append(order.getOrderTime()).append("\n\n");
        emailBody.append("--- ご注文内容 ---\n");
        for (OrderDetail detail : order.getOrderDetails()) {
            emailBody.append(detail.getMenu().getName())
                    .append(" x ")
                    .append(detail.getQuantity())
                    .append(" = ")
                    .append(detail.getPrice() * detail.getQuantity())
                    .append("円\n");
        }
        emailBody.append("------------------\n");
        emailBody.append("合計金額: ").append(order.getTotal()).append("円\n");
        if (order.getNote() != null && !order.getNote().trim().isEmpty()) {
            emailBody.append("備考: ").append(order.getNote()).append("\n");
        }
        emailBody.append("\nご利用ありがとうございます。\nまたのご利用をお待ちしております。\n");
        message.setText(emailBody.toString());

        try {
            mailSender.send(message);
            System.out.println("注文確認メールを " + user.getEmail() + " に送信しました。");
        } catch (Exception e) {
            System.err.println("注文確認メールの送信に失敗しました。エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 驗證輸入的 Pin Code 是否正確
    @PostMapping("/verifyCode")
    public String verifyCode(@RequestParam("verifyCode") String verifyCode, HttpSession session, Model model) {
        String correctCode = (String) session.getAttribute("code");
        if (verifyCode.equals(correctCode)) {
            System.out.println("驗證碼正確");
            if (session.getAttribute("name") != null) {
                User user = new User();
                user.setEmail((String) session.getAttribute("email"));
                user.setUsername((String) session.getAttribute("name"));
                user.setPassword((String) session.getAttribute("password"));
                user.setPoints(0); // 新用戶點數初始化為0
                userRepository.save(user);
            } else if (session.getAttribute("newpassword") != null) {
                User user = userRepository.findByEmail((String) session.getAttribute("email"));
                if (user != null) {
                    user.setPassword((String) session.getAttribute("password"));
                    userRepository.save(user);
                } else {
                    model.addAttribute("error", "ユーザーが見つかりませんでした。");
                    return "forgottenpassword";
                }
            }
            // 驗證成功後清除所有相關暫存 Session，避免影響下次操作
            session.removeAttribute("code");
            session.removeAttribute("name");
            session.removeAttribute("newpassword");
            session.removeAttribute("password");
            return "login";
        } else {
            model.addAttribute("error", "コード間違いました。もう一度入力してください。");
            return "pincode";
        }
    }

    // 使用者登入邏輯
    @PostMapping("/login_2")
    public String login(@RequestParam("email") String email, @RequestParam("password") String password, Model model,
            HttpSession session) {
        System.out.println("正在登入");
        session.setAttribute("email", email);
        session.setAttribute("password", password);
        User user = userRepository.findByEmail(email);
        if (user != null && user.getPassword().equals(password)) {
            session.setAttribute("user", user);
            System.out.println("密碼正確");
            return "redirect:/menu";
        } else {
            model.addAttribute("error", "メールアドレスまたはパスワードが間違っています。");
            System.out.println("メールアドレスまたはパスワードが間違っています。");
            return "login";
        }
    }

    // 忘記密碼處理：發送驗證碼
    @PostMapping("/forgottenpassword_2")
    public String forgottenpassword(@RequestParam("email") String email,
            @RequestParam("newpassword") String newPassword, Model model, HttpSession session) {
        System.out.println("正在重置密碼");
        if (isValidPassword(newPassword)) {
            String code = sendVerificationCode(email);
            session.setAttribute("email", email);
            session.setAttribute("code", code);
            session.setAttribute("password", newPassword);
            session.setAttribute("newpassword", newPassword);
            session.removeAttribute("name"); // 重要：移除註冊用的名字，確保 verifyCode 進入正確分支
            return "pincode";
        } else {
            model.addAttribute("error", "パスワードは8文字以上にしてください。");
            return "forgottenpassword";
        }
    }

    // 重設使用者名稱
    @PostMapping("/resetName_2")
    public String resetName(@RequestParam("newname") String newName, Model model, HttpSession session) {
        String email = (String) session.getAttribute("email");
        System.out.println("從 Session 拿到的 Email: " + email);
        String password = (String) session.getAttribute("password");
        System.out.println("從 Session 拿到的 Password: " + password);
        User user = userRepository.findByEmailAndPassword(email, password);
        if (user != null) {
            user.setUsername(newName);
            user = userRepository.save(user); // 取得儲存後包含 ID 的完整物件
            
            // 重點：更新 Session 裡的使用者資料，讓導覽列同步更新
            session.setAttribute("user", user);
            
            // 改用重定向，讓頁面重新執行 showProfile 的邏輯
            return "redirect:/profile";
        } else {
            model.addAttribute("error", "ユーザーが見つかりませんでした。");
        }
        return "resetName";
    }

    // 加入購物車並建立初始訂單 (處理營業時間、點數扣抵、支付方式選擇)
    @PostMapping("/add-to-cart")
    public String addToCart(
            @RequestParam(value = "menuIds", required = false) List<Long> menuIds,
            @RequestParam(value = "paymentMethod", defaultValue = "cash") String paymentMethod,
            @RequestParam(value = "usedPoints", defaultValue = "0") int usedPoints,
            @RequestParam(value = "note", required = false) String note,
            HttpServletRequest request,
            HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        System.out.println("進入 /add-to-cart");
        if (user == null) {
            return "redirect:/login";
        } else {
            // 檢查營業時間 (06:00 - 23:45)
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
            order.setPaymentMethod(paymentMethod);

            // 驗證點數扣抵：範圍檢查，不可為負數且不可超過使用者擁有的點數
            usedPoints = Math.max(0, Math.min(usedPoints, user.getPoints()));
            order.setUsedPoints(usedPoints);

            order.setNote(note);
            order.setOrderTime(LocalDateTime.now().toString());
            int totalAmount = 0;

            for (Long selectedId : menuIds) {
                Menu menu = menuRepository.findById(selectedId).orElse(null);
                if (menu != null) {
                    // 從 request 中精確獲取該 ID 對應的數量
                    String qtyRaw = request.getParameter("quantity_" + selectedId);
                    int qty = (qtyRaw != null && !qtyRaw.isEmpty()) ? Integer.parseInt(qtyRaw) : 1;

                    OrderDetail detail = new OrderDetail();
                    detail.setOrder(order);
                    detail.setMenu(menu);
                    detail.setQuantity(qty);
                    detail.setPrice(menu.getPrice());
                    totalAmount += (menu.getPrice() * qty);
                    order.getOrderDetails().add(detail);
                }
            }

            // 計算最終金額 (總額 - 點數抵扣)
            int finalAmount = totalAmount - usedPoints;
            if (finalAmount < 0) {
                order.setUsedPoints(totalAmount); // 若點數多於總額，只抵扣到0元
                finalAmount = 0;
            }
            order.setTotal(finalAmount);

            orderRepository.save(order);
            model.addAttribute("order", order);
            // 將 orderId 存入 session 或 model 以便付款確認時使用，避免誤扣
            session.setAttribute("pendingOrderId", order.getId());

            // 如果選擇信用卡，導向刷卡頁面
            if ("credit_card".equals(paymentMethod)) {
                return "credit_card_payment";
            }

            return "paymentStatus";
        }
    }

    // 模擬信用卡刷卡處理
    @PostMapping("/process-credit-card")
    public String processCreditCard(
            @RequestParam("cardNumber") String cardNumber,
            @RequestParam("expiry") String expiry,
            @RequestParam("cvv") String cvv,
            HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        // 這裡進行模擬刷卡邏輯
        String lastFourDigits = (cardNumber != null && cardNumber.length() >= 4)
                ? cardNumber.substring(cardNumber.length() - 4)
                : "****";
        System.out.println("正在處理使用者 " + user.getUsername() + " 的信用卡支付，卡號後四碼: " + lastFourDigits);

        // 取得待處理訂單並更新狀態
        Long idToPay = (Long) session.getAttribute("pendingOrderId");
        if (idToPay != null) {
            paymentStatus(idToPay, session, model);
        }
        return "paymentStatus"; // 刷卡完留在狀態頁面看結果
    }

    // 更新付款狀態並計算點數 (核心支付完成邏輯)
    @PostMapping("/paymentstatus_2")
    public String paymentStatus(@RequestParam(value = "orderId", required = false) Long orderId, HttpSession session,
            Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        } else {
            // 優先使用傳入的 ID，若無則從 session 拿，最後才抓最新的（增加安全性）
            Long idToPay = (orderId != null) ? orderId : (Long) session.getAttribute("pendingOrderId");
            Order order = (idToPay != null) ? orderRepository.findById(idToPay).orElse(null)
                    : orderRepository.findTopByUserOrderByIdDesc(user);

            if (order != null && "false".equals(order.getPaymentStatus())) {
                // 1. 取得資料庫中最新的使用者物件，避免點數計算錯誤
                User dbUser = userRepository.findById(user.getId()).orElse(user);

                // 2. 扣除該訂單使用的點數
                int newPoints = dbUser.getPoints() - order.getUsedPoints();
                dbUser.setPoints(Math.max(0, newPoints)); // 再次確保點數不為負數

                // 3. 更新訂單狀態並發送郵件
                order.setPaymentStatus("true");
                orderRepository.save(order);
                sendOrderConfirmationEmail(dbUser, order); // 傳入 dbUser 確保資料最新

                // 4. 計算並增加新獲得的點數，最後存入資料庫與更新 Session
                addLoyaltyPoints(dbUser, order, session);
                session.removeAttribute("pendingOrderId"); // 清除待處理訂單ID
            }
            model.addAttribute("order", order); // 確保 order 物件傳回前端
            model.addAttribute("message", "決済が正常に完了しました！");
            return "paymentStatus"; // 改為返回頁面而非重定向，這樣才看得到「支払済」
        }
    }

    // 點數回饋邏輯：消費額的 1%
    public void addLoyaltyPoints(User user, Order order, HttpSession session) {
        if (order.getTotal() > 0) {
            int pointsEarned = (int) Math.floor(order.getTotal() * 0.01); // 消費金額的1%點數，向下取整
            order.setEarnedPoints(pointsEarned); // 紀錄到訂單中，以便顯示明細
            orderRepository.save(order);
            user.setPoints(user.getPoints() + pointsEarned);
        }
        // 無論是否有新獲得點數，都必須執行 save，以確保前面的「點數扣除」被永久儲存
        userRepository.save(user);
        session.setAttribute("user", user);
        System.out.println("用戶 " + user.getUsername() + " 點數更新完成。目前總點數: " + user.getPoints());
    }

    // 寶くじ功能：花費 10 點抽獎
    @PostMapping("/play-lottery")
    public String playLottery(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        User dbUser = userRepository.findById(user.getId()).orElse(user);
        int cost = 10;

        if (dbUser.getPoints() < cost) {
            session.setAttribute("lotteryMessage", "エラー: ポイントが足りません（10pt必要です）。");
            return "redirect:/profile";
        }

        // 扣除成本
        dbUser.setPoints(dbUser.getPoints() - cost);

        // 隨機抽獎邏輯
        int rand = (int) (Math.random() * 100); // 0-99
        int winAmount = 0;
        String message;

        if (rand < 5) { // 5% 機率
            winAmount = 100;
            message = "🎉 おめでとうございます！1等 100pt 獲得しました！";
        } else if (rand < 15) {
            winAmount = 50;
            message = "✨ 当たり！2等 50pt 獲得しました！";
        } else if (rand < 30) { 
            winAmount = 20;
            message = "👍 小当たり！3等 20pt 獲得しました！";
        } else {
            message = "残念！はずれです。また挑戦してください！";
        }

        dbUser.setPoints(dbUser.getPoints() + winAmount);
        userRepository.save(dbUser);
        session.setAttribute("user", dbUser);
        session.setAttribute("lotteryMessage", message);

        return "redirect:/profile";
    }

    // 顯示點數歷史與目前的點數
    @GetMapping("/points-history")
    public String pointsHistory(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        // 獲取最新點數
        User latestUser = userRepository.findById(user.getId()).orElse(user);
        List<Order> orders = orderRepository.findByUser(latestUser);

        model.addAttribute("orders", orders);
        model.addAttribute("currentUser", latestUser);
        return "points_history";
    }

    // 顯示使用者的訂單歷史紀錄
    @PostMapping("/history_2")
    public String history(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        } else {
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

    // 使用者取消尚未付款的訂單
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

    // 從歷史紀錄點擊支付未完成的訂單
    @PostMapping("/pay")
    public String pay(@RequestParam("id") Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        } else {
            // 呼叫統一的支付處理邏輯，確保點數也會被扣除
            return paymentStatus(id, session, model);
        }
    }

    @RequestMapping("/")
    public String start() {
        return "home";
    }

    // 顯示個人資料頁面：包含會員等級計算、累積消費、升級進度等
    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        // 從資料庫重新取得最新的使用者資料，確保顯示的點數是最即時的
        User latestUser = userRepository.findById(user.getId()).orElse(user);

        // 1. 獲取所有訂單以計算統計數據
        List<Order> allOrders = orderRepository.findByUser(latestUser);

         // 2. 獲取最近 3 筆訂單，增加頁面內容豐富度
        List<Order> recentOrders = allOrders.stream()
                .sorted((o1, o2) -> o2.getId().compareTo(o1.getId()))
                .limit(3)
                .toList();

        // 4. 計算累積消費總額 (僅計算已支付訂單)
        int totalSpent = allOrders.stream()
                .filter(o -> "true".equals(o.getPaymentStatus()))
                .mapToInt(Order::getTotal)
                .sum();

        // 3. 修正：基於「累積消費金額」計算會員等級，避免因使用點數而降級
        // 假設：1 點 = 100 元消費，則 50點=5000元, 100點=10000元, 500點=50000元, 1000點=100000元
        String tier = "一般会員";
        double progress = 0;
        int pointsToNext = 0;

        if (totalSpent >= 100000) {
            tier = "ダイヤ会員";
            progress = 100.0;
            pointsToNext = 0;
        } else if (totalSpent >= 50000) {
            tier = "ゴールド会員";
            progress = ((totalSpent - 50000) / 50000.0) * 100;
            pointsToNext = (100000 - totalSpent) / 100;
        } else if (totalSpent >= 10000) {
            tier = "シルバー会員";
            progress = ((totalSpent - 10000) / 40000.0) * 100;
            pointsToNext = (50000 - totalSpent) / 100;
        } else if (totalSpent >= 5000) {
            tier = "ブロンズ会員";
            progress = ((totalSpent - 5000) / 5000.0) * 100;
            pointsToNext = (10000 - totalSpent) / 100;
        } else {
            tier = "一般会員";
            progress = (totalSpent / 5000.0) * 100;
            pointsToNext = (5000 - totalSpent) / 100;
        }

        // 獲取抽獎結果訊息並從 Session 移除（確保只顯示一次）
        String lotteryMessage = (String) session.getAttribute("lotteryMessage");
        if (lotteryMessage != null) {
            model.addAttribute("lotteryResult", lotteryMessage);
            session.removeAttribute("lotteryMessage");
        }

        model.addAttribute("currentUser", latestUser);
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("tier", tier);
        model.addAttribute("progress", progress);
        model.addAttribute("pointsToNext", pointsToNext);
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("orderCount", allOrders.size());

        return "profile";
    }

    // 上傳個人頭像
    @PostMapping("/profile/uploadPhoto")
    public String uploadPhoto(@RequestParam("imageFile") MultipartFile imageFile, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
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

                // 確保從資料庫取得最新的 User 物件進行更新
                User dbUser = userRepository.findById(user.getId()).orElse(user);
                dbUser.setPhoto("/uploads/" + fileName);
                userRepository.save(dbUser);

                // 更新 Session 中的使用者資訊
                session.setAttribute("user", dbUser);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "redirect:/profile";
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
        if (user == null) {
            return "redirect:/login";
        }
        List<Order> orders = orderRepository.findByUser(user);
        model.addAttribute("orders", orders);
        model.addAttribute("currentUser", user);
        return "history";
    }
}