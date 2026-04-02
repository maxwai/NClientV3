package com.maxwai.nclientv3.settings;

import androidx.annotation.NonNull;

public final class AuthCredentials {
    public enum Type {
        API_KEY
    }

    @NonNull
    private final Type type;
    @NonNull
    private final String secret;

    public AuthCredentials(@NonNull Type type, @NonNull String secret) {
        this.type = type;
        this.secret = secret;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getSecret() {
        return secret;
    }

    @NonNull
    public String toAuthorizationHeader() {
        switch (type) {
            case API_KEY:
            default:
                return "Key " + secret;
        }
    }
}
