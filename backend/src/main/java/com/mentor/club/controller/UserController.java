package com.mentor.club.controller;

import com.mentor.club.model.authentication.AuthenticationRequest;
import com.mentor.club.model.password.ChangeForgottenPasswordRequest;
import com.mentor.club.model.password.ChangePasswordRequest;
import com.mentor.club.model.user.NewUser;
import com.mentor.club.service.JwtService;
import com.mentor.club.service.PasswordService;
import com.mentor.club.service.UserService;
import io.swagger.annotations.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/user")
@Api(value = "User authentication")
public class UserController {
    private UserService userService;
    private PasswordService passwordService;
    private JwtService jwtService;

    public UserController(UserService userService, PasswordService passwordService, JwtService jwtService) {
        this.userService = userService;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    @PostMapping
    @RequestMapping("/authenticate")
    @ApiOperation(value = "Credentials-based authentication request")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Authorized"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "User not found"),
    })
    public ResponseEntity authenticate(@ApiParam(value = "Credentials in JSON format 'username/password'")
                                       @RequestBody AuthenticationRequest authentication,
                                       HttpServletResponse response) {
        return userService.authenticate(authentication, response);
    }

    @PostMapping
    @ApiOperation(value = "New user request")
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "Unauthorized"),
    })
    public ResponseEntity createNewUser(@ApiParam(value = "New user request") @RequestBody NewUser newUser) {
        return userService.createNewUser(newUser);
    }

    @GetMapping
    @RequestMapping("/confirm-email/{emailConfirmTokenId}")
    @ApiOperation(value = "Confirm email address")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Authorized"),
            @ApiResponse(code = 404, message = "EmailConfirmToken id not found"),
    })
    public ResponseEntity confirmEmail(@ApiParam(value = "Confirm email") @PathVariable UUID emailConfirmTokenId) {
        return userService.confirmEmail(emailConfirmTokenId);
    }

    @PostMapping
    @RequestMapping("/reset-forgotten-password")
    @ApiOperation(value = "Reset password request")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Authorized"),
            @ApiResponse(code = 404, message = "User not found"),
    })
    public ResponseEntity generateResetForgottenPasswordEmail(@ApiParam(value = "Reset password") @RequestParam String email) {
        return passwordService.generateResetForgottenPasswordEmail(email);
    }

    @PostMapping
    @RequestMapping("/change-forgotten-password")
    @ApiOperation(value = "Change forgotten password request")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Authorized"),
            @ApiResponse(code = 401, message = "Invalid password"),
    })
    public ResponseEntity changeForgottenPassword(@ApiParam(value = "Reset password")
                                                  @RequestBody ChangeForgottenPasswordRequest changeForgottenPasswordRequest) {
        return passwordService.changeForgottenPassword(changeForgottenPasswordRequest);
    }

    @PostMapping
    @RequestMapping("/change-password")
    @ApiOperation(value = "Change password request")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Authorized"),
            @ApiResponse(code = 401, message = "Invalid password"),
    })
    public ResponseEntity changePassword(@RequestBody ChangePasswordRequest changePasswordRequest,
                                         @RequestHeader(name = "Authorization") String authorization) {
        return passwordService.changePassword(changePasswordRequest, authorization);
    }

    @PostMapping
    @RequestMapping("/logout")
    @ApiOperation(value = "Logout user")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Authorized"),
            @ApiResponse(code = 401, message = "Unauthorized"),
    })
    public ResponseEntity logout(@ApiParam(value = "Logout user")
                                 @RequestHeader(name = "Authorization") String authorization,
                                 @RequestParam String username) {
        return userService.logout(authorization, username);
    }

    @GetMapping
    @RequestMapping("/public-key")
    @ApiOperation(value = "Request the public key")
    public ResponseEntity getPublicKey() {
        return jwtService.getPublicKey();
    }
}
