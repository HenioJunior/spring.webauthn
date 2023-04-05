package com.spring.webauthn.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.spring.webauthn.user.AppUser;
import com.spring.webauthn.utility.Utility;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;


@Controller
public class AuthController {

    private RelyingParty relyingParty;
    private RegistrationService service;

    AuthController(RegistrationService service, RelyingParty relyingPary) {
        this.relyingParty = relyingPary;
        this.service = service;
    }

    @GetMapping("/")
    public String welcome() {
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerUser(AppUser user) {
        return "register";
    }

    @PostMapping("/register")
    @ResponseBody
    public String newUserRegistration(@RequestParam String username,
                                      @RequestParam String display,
                                      HttpSession session
    ) {
        AppUser existingUser = service.getUserRepo().findByUsername(username);
        if (existingUser == null) {
            UserIdentity userIdentity = UserIdentity.builder()
                    .name(username)
                    .displayName(display)
                    .id(Utility.generateRandom(32))
                    .build();
            AppUser saveUser = new AppUser(userIdentity);
            service.getUserRepo().save(saveUser);
            String response = newAuthRegistration(saveUser, session);
            return response;
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username " + username
                    + " already exists. Choose a new name.");
        }
    }

    @PostMapping("/registerauth")
    @ResponseBody
    public String newAuthRegistration(
            @RequestParam AppUser user,
            HttpSession session
    ) {
        AppUser existingUser = service.getUserRepo().findByHandle(user.getHandle());
        if (existingUser != null) {
            UserIdentity userIdentity = user.toUserIdentity();
            StartRegistrationOptions registrationOptions = StartRegistrationOptions.builder()
                    .user(userIdentity)
                    .build();
            PublicKeyCredentialCreationOptions registration = relyingParty.startRegistration(registrationOptions);
            session.setAttribute(userIdentity.getDisplayName(), registration);
            try {
                return registration.toCredentialsCreateJson();
            } catch (JsonProcessingException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing JSON.", e);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User " + user.getUsername() + " does not exist. Please register.");
        }
    }
}
