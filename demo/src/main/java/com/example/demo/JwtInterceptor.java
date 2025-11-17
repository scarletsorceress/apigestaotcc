package com.example.demo;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String SECRET = "MEUSEGREDOSUPERSECRETO123"; // mesma chave do JwtService

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            response.getWriter().write("Token não enviado");
            return false;
        }

        String token = header.replace("Bearer ", "");

        try {
            JWT.require(Algorithm.HMAC256(SECRET))
                    .build()
                    .verify(token);
            return true;

        } catch (JWTVerificationException e) {
            response.setStatus(401);
            response.getWriter().write("Token inválido ou expirado");
            return false;
        }
    }
}
