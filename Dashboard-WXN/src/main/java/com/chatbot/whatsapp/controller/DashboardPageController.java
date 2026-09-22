package com.chatbot.whatsapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Entrega a interface web operacional empacotada com a aplicacao. */
@Controller
public class DashboardPageController {

    @GetMapping({"/dashboard", "/dashboard/"})
    public String dashboard() {
        return "forward:/dashboard/index.html";
    }

    @GetMapping({"/simulator", "/simulator/"})
    public String simulator() {
        return "forward:/simulator/index.html";
    }
}
