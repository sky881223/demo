package com.example.demo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;
import jakarta.servlet.http.HttpSession;
@Controller
public class HistoryController {
    @Autowired
    private OrderRepository orderRepository;
    @RequestMapping("/history")
    public String ShowHistory(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        List<Order> orders = orderRepository.findByUser(user);

        model.addAttribute("orders", orders);
        model.addAttribute("user", user);
        return "history";
    }
}
