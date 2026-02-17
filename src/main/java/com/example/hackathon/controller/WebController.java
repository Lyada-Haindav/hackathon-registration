package com.example.hackathon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/faculty-login")
    public String facultyLogin() {
        return "faculty-login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/faculty-register")
    public String facultyRegister() {
        return "faculty-register";
    }

    @GetMapping("/faculty")
    public String faculty() {
        return "faculty-dashboard";
    }

    @GetMapping("/faculty/events")
    public String facultyEvents() {
        return "faculty-events";
    }

    @GetMapping("/faculty/forms")
    public String facultyForms() {
        return "faculty-forms";
    }

    @GetMapping("/faculty/problems")
    public String facultyProblems() {
        return "faculty-problems";
    }

    @GetMapping("/faculty/evaluation")
    public String facultyEvaluation() {
        return "faculty-evaluation";
    }

    @GetMapping("/faculty/teams")
    public String facultyTeams() {
        return "faculty-teams";
    }

    @GetMapping("/faculty/deployment")
    public String facultyDeployment() {
        return "faculty-deployment";
    }

    @GetMapping("/user")
    public String user() {
        return "user-dashboard";
    }

    @GetMapping("/problem-statements")
    public String problemStatements() {
        return "problem-statements";
    }

    @GetMapping("/leaderboard")
    public String leaderboard() {
        return "top-teams";
    }

    @GetMapping("/top-teams")
    public String topTeams() {
        return "redirect:/leaderboard";
    }
}
