package com.example.demo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

@Service
public class JwtService {

    private final String secret = "MEUSEGREDOSUPERSECRETO123";

    public String gerarToken(String username) {
        return JWT.create()
                .withSubject(username)
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256(secret));
    }

    public String validarToken(String token) {
        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret))
                .build()
                .verify(token);

        return jwt.getSubject(); // retorna o username
    }
}
