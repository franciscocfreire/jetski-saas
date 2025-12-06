package com.jetski.signup.domain;

/**
 * Status enum for tenant signup requests.
 */
public enum SignupStatus {
    /**
     * Signup created, waiting for user to activate via email link.
     */
    PENDING,

    /**
     * User activated the account successfully.
     */
    ACTIVATED,

    /**
     * Activation token has expired (48h default).
     */
    EXPIRED
}
