package com.carmind.app;

import java.util.regex.Pattern;

final class LoginValidation {
    enum Error { NONE, EMAIL_REQUIRED, EMAIL_INVALID, PASSWORD_REQUIRED }
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$");

    static Error validate(String email, String password) {
        if (email.isEmpty()) return Error.EMAIL_REQUIRED;
        if (!EMAIL.matcher(email).matches()) return Error.EMAIL_INVALID;
        if (password.isEmpty()) return Error.PASSWORD_REQUIRED;
        // Firebase checks credentials. Do not impose a registration policy on login.
        return Error.NONE;
    }
}
