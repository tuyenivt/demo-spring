package com.example.security;

import com.example.security.config.SecurityConfig;
import com.example.security.controller.HomeController;
import com.example.security.controller.LeadersController;
import com.example.security.controller.LoginController;
import com.example.security.controller.SystemsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({HomeController.class, LoginController.class, LeadersController.class, SystemsController.class})
@Import(SecurityConfig.class)
class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-login/"));
    }

    @Test
    void loginPageIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/my-login/"))
                .andExpect(status().isOk())
                .andExpect(view().name("login/index"));
    }

    @Test
    void validCredentialsAuthenticateWithUserRoles() throws Exception {
        mockMvc.perform(formLogin("/my-authenticate").user("peter").password("123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("peter").withRoles("EMPLOYEE", "MANAGER"));
    }

    @Test
    void invalidCredentialsRedirectToLoginWithError() throws Exception {
        mockMvc.perform(formLogin("/my-authenticate").user("john").password("wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-login/?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void rememberMeSetsCookieWhenRequested() throws Exception {
        mockMvc.perform(post("/my-authenticate")
                        .param("username", "john")
                        .param("password", "123")
                        .param("remember-me", "on")
                        .with(csrf()))
                .andExpect(authenticated().withUsername("john"))
                .andExpect(cookie().exists("remember-me"))
                .andExpect(cookie().maxAge("remember-me", 86400));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void logoutRedirectsToLoginPage() throws Exception {
        mockMvc.perform(logout())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-login/?logout"))
                .andExpect(unauthenticated());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanAccessHome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotAccessLeaders() throws Exception {
        mockMvc.perform(get("/leaders/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotAccessSystems() throws Exception {
        mockMvc.perform(get("/systems/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerCanAccessLeaders() throws Exception {
        mockMvc.perform(get("/leaders/"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerCannotAccessSystems() throws Exception {
        mockMvc.perform(get("/systems/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"EMPLOYEE", "ADMIN"})
    void adminCanAccessSystems() throws Exception {
        mockMvc.perform(get("/systems/"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"EMPLOYEE", "MANAGER"})
    void managerWithEmployeeCanAccessBothHomeAndLeaders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/leaders/"))
                .andExpect(status().isOk());
    }

    @Test
    void accessDeniedPageIsAccessible() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void accessDeniedPageIsAccessibleForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk());
    }
}
