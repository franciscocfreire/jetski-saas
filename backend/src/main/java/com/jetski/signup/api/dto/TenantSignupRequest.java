package com.jetski.signup.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for public tenant signup (new user creating a company).
 *
 * @param razaoSocial Legal business name of the company
 * @param slug URL-friendly identifier (lowercase, alphanumeric, hyphens only)
 * @param cnpj Brazilian CNPJ (optional)
 * @param adminEmail Email address for the first admin user
 * @param adminNome Full name of the first admin user
 */
public record TenantSignupRequest(
    @NotBlank(message = "Razão social é obrigatória")
    @Size(min = 3, max = 200, message = "Razão social deve ter entre 3 e 200 caracteres")
    String razaoSocial,

    @NotBlank(message = "Slug é obrigatório")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug deve conter apenas letras minúsculas, números e hífens")
    @Size(min = 3, max = 50, message = "Slug deve ter entre 3 e 50 caracteres")
    String slug,

    @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
    String cnpj,

    @NotBlank(message = "Email do administrador é obrigatório")
    @Email(message = "Email do administrador inválido")
    String adminEmail,

    @NotBlank(message = "Nome do administrador é obrigatório")
    @Size(min = 2, max = 200, message = "Nome do administrador deve ter entre 2 e 200 caracteres")
    String adminNome
) {}
