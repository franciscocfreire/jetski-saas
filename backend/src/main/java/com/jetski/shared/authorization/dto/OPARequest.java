package com.jetski.shared.authorization.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper para requisição ao OPA.
 * OPA espera {"input": {...}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OPARequest {
    private OPAInput input;

    public static OPARequest of(OPAInput input) {
        return new OPARequest(input);
    }
}
