package com.vamsik.vkdb.ui.controller;

import com.vamsik.vkdb.ui.service.SocketService;
import com.vamsik.vkdb.ui.service.SocketService.SocketEntry;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;

@Controller
@RequestMapping("/")
public class HomeController {

    @Autowired
    private SocketService socketService;

    @GetMapping("/login")
    public ModelAndView login() {
        return new ModelAndView("login.html");
    }

    @PostMapping("/login")
    public ModelAndView doLogin(@RequestParam String host, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            String[] parts = host.split(":");
            String hostname = parts[0];
            int port = Integer.parseInt(parts[1]);

            // Test connection
            socketService.getAllEntries(hostname, port);

            session.setAttribute("host", hostname);
            session.setAttribute("port", port);
            return new ModelAndView("redirect:/");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to connect to server: " + e.getMessage());
            return new ModelAndView("redirect:/login");
        }
    }

    @GetMapping
    public ModelAndView index(HttpSession session) {
        if (session.getAttribute("host") == null) {
            return new ModelAndView("redirect:/login");
        }

        String host = (String) session.getAttribute("host");
        int port = (int) session.getAttribute("port");

        ModelAndView mv = new ModelAndView("index.html");
        try {
            List<SocketEntry> entries = socketService.getAllEntries(host, port);
            mv.addObject("entries", entries);
            mv.addObject("connectionText", "Connected");
        } catch (IOException e) {
            mv.addObject("connectionText", "Not Connected");
            mv.addObject("error", "Failed to fetch entries: " + e.getMessage());
        }
        return mv;
    }

    @PostMapping("/add")
    public ModelAndView addEntry(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) String ttl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("host") == null) {
            return new ModelAndView("redirect:/login");
        }

        String host = (String) session.getAttribute("host");
        int port = (int) session.getAttribute("port");

        try {
            socketService.setEntry(host, port, key, value, Long.parseLong(ttl));
            redirectAttributes.addFlashAttribute("success", "Entry added successfully");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add entry: " + e.getMessage());
        }
        return new ModelAndView("redirect:/");
    }

    @GetMapping("/delete")
    public ModelAndView deleteEntry(@RequestParam String key, HttpSession session) {
        try {
            String host = (String) session.getAttribute("host");
            int port = (int) session.getAttribute("port");
            socketService.deleteEntry(host, port, key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ModelAndView("redirect:/");
    }
}
