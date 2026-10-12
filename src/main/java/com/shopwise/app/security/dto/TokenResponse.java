package com.shopwise.app.security.dto;

import java.util.List;

/** Reponse du login : jeton a envoyer dans l'en-tete "Authorization: Bearer <accessToken>". */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String username, List<String> roles) {
}