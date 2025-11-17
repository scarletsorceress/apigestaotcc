package com.example.demo;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

@Service
public class JwtService {

    private final String secret = "MEU_SEGREDO_SUPER_SEGURO";
    private final Algorithm algorithm = Algorithm.HMAC256(secret);

    // Tempo de expiração em 2 horas
    private final long expirationMs = 2 * 60 * 60 * 1000;

    public String gerarToken(String username) {
        return JWT.create()
                .withSubject(username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMs))
                .sign(algorithm);
    }

    public String validarToken(String token) {
        try {
            return JWT.require(algorithm)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException e) {
            return null; // Token inválido
        }
    }
}
