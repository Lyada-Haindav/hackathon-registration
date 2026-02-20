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

    @GetMapping("/organizer/login")
    public String organizerLogin() {
        return "faculty-login";
    }

    @GetMapping("/faculty-login")
    public String facultyLoginRedirect() {
        return "redirect:/organizer/login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @GetMapping("/resend-verification")
    public String resendVerification() {
        return "resend-verification";
    }

    @GetMapping("/reset-password")
    public String resetPassword() {
        return "reset-password";
    }

    @GetMapping("/verify-email")
    public String verifyEmail() {
        return "verify-email";
    }

    @GetMapping("/organizer/register")
    public String organizerRegister() {
        return "faculty-register";
    }

    @GetMapping("/organizer/approve")
    public String organizerApprove() {
        return "organizer-approve";
    }

    @GetMapping("/faculty-register")
    public String facultyRegisterRedirect() {
        return "redirect:/organizer/register";
    }

    @GetMapping("/organizer")
    public String organizerAccess() {
        return "organizer-access";
    }

    @GetMapping("/organizer/dashboard")
    public String organizerDashboard() {
        return "faculty-dashboard";
    }

    @GetMapping("/organizer/events")
    public String organizerEvents() {
        return "faculty-events";
    }

    @GetMapping("/organizer/forms")
    public String organizerForms() {
        return "faculty-forms";
    }

    @GetMapping("/organizer/problems")
    public String organizerProblems() {
        return "faculty-problems";
    }

    @GetMapping("/organizer/evaluation")
    public String organizerEvaluation() {
        return "faculty-evaluation";
    }

    @GetMapping("/organizer/teams")
    public String organizerTeams() {
        return "faculty-teams";
    }

    @GetMapping("/organizer/deployment")
    public String organizerDeployment() {
        return "faculty-deployment";
    }

    @GetMapping("/faculty")
    public String facultyDashboardRedirect() {
        return "redirect:/organizer/dashboard";
    }

    @GetMapping("/faculty/events")
    public String facultyEventsRedirect() {
        return "redirect:/organizer/events";
    }

    @GetMapping("/faculty/forms")
    public String facultyFormsRedirect() {
        return "redirect:/organizer/forms";
    }

    @GetMapping("/faculty/problems")
    public String facultyProblemsRedirect() {
        return "redirect:/organizer/problems";
    }

    @GetMapping("/faculty/evaluation")
    public String facultyEvaluationRedirect() {
        return "redirect:/organizer/evaluation";
    }

    @GetMapping("/faculty/teams")
    public String facultyTeamsRedirect() {
        return "redirect:/organizer/teams";
    }

    @GetMapping("/faculty/deployment")
    public String facultyDeploymentRedirect() {
        return "redirect:/organizer/deployment";
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
