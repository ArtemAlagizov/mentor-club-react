package com.mentor.club.controller;

import com.mentor.club.model.authentication.AuthenticationRequest;
import com.mentor.club.model.password.ChangeForgottenPasswordRequest;
import com.mentor.club.model.password.ChangePasswordRequest;
import com.mentor.club.model.user.NewUser;
import com.mentor.club.service.PasswordService;
import com.mentor.club.service.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class UserControllerTest {
    @InjectMocks
    private UserController userController;

    @Mock
    private UserService userService;

    @Mock
    private PasswordService passwordService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test_authenticate_callsAuthenticateOfUserService() {
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        userController.authenticate(authenticationRequest, httpServletResponse);

        verify(userService, times(1)).authenticate(authenticationRequest, httpServletResponse);
    }

    @Test
    public void test_createNewUser_callsCreateNewUserOfUserService() {
        NewUser newUser = new NewUser();

        userController.createNewUser(newUser);

        verify(userService, times(1)).createNewUser(newUser);
    }

    @Test
    public void test_confirmEmail_callsConfirmEmailOfUserService() {
        UUID emailConfirmTokenId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        userController.confirmEmail(emailConfirmTokenId.toString(), deviceId, httpServletResponse);

        verify(userService, times(1)).confirmEmail(emailConfirmTokenId.toString(), deviceId, httpServletResponse);
    }

    @Test
    public void test_generateResetForgottenPassword_callsGenerateResetForgottenPasswordOfPasswordService() {
        String userEmail = "test-user-email";

        userController.generateResetForgottenPasswordEmail(userEmail);

        verify(passwordService, times(1)).generateResetForgottenPasswordEmail(userEmail);
    }

    @Test
    public void test_changeForgottenPassword_callsChangeForgottenPasswordOfPasswordService() {
        ChangeForgottenPasswordRequest changeForgottenPasswordRequest = new ChangeForgottenPasswordRequest();

        userController.changeForgottenPassword(changeForgottenPasswordRequest);

        verify(passwordService, times(1)).changeForgottenPassword(changeForgottenPasswordRequest);
    }

    @Test
    public void test_changePassword_callsChangePasswordOfPasswordService() {
        ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest();
        String authorization = "test-authorization";

        userController.changePassword(changePasswordRequest, authorization);

        verify(passwordService, times(1)).changePassword(changePasswordRequest, authorization);
    }

    @Test
    public void test_logout_callsLogoutOfUserService() {
        String authorization = "";
        UUID deviceId = UUID.randomUUID();

        userController.logout(authorization, deviceId);

        verify(userService, times(1)).logout(authorization, deviceId);
    }
}
