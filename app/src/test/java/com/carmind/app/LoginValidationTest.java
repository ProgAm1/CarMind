package com.carmind.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LoginValidationTest {
    @Test public void blankFormRequestsEmailFirst() {
        assertEquals(LoginValidation.Error.EMAIL_REQUIRED, LoginValidation.validate("", ""));
    }
    @Test public void malformedEmailIsRejected() {
        for (String value : new String[]{"driver", "driver@", "@example.com", "driver@example", "driver @example.com", "driver@@example.com"}) {
            assertEquals(value, LoginValidation.Error.EMAIL_INVALID, LoginValidation.validate(value, "password"));
        }
    }
    @Test public void validEmailNeedsPassword() {
        assertEquals(LoginValidation.Error.PASSWORD_REQUIRED, LoginValidation.validate("driver@example.com", ""));
    }
    @Test public void validCredentialsReachFirebase() {
        assertEquals(LoginValidation.Error.NONE, LoginValidation.validate("driver+car@example.com", "password"));
    }
    @Test public void loginDoesNotApplyNewPasswordRules() {
        assertEquals(LoginValidation.Error.NONE, LoginValidation.validate("driver@example.com", "a"));
    }
    @Test public void passwordSpacesRemainCredentials() {
        assertEquals(LoginValidation.Error.NONE, LoginValidation.validate("driver@example.com", "  password  "));
    }
}
