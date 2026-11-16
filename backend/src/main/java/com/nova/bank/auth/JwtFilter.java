package com.nova.bank.auth;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Bind verified active sessions to Spring Security. */
@Component public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final AuthService auth;
    public JwtFilter(JwtService jwt, AuthService auth) { this.jwt=jwt; this.auth=auth; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header=request.getHeader("Authorization");
        if (header!=null && header.startsWith("Bearer ")) {
            JwtService.TokenIds ids=null;
            try { ids=jwt.verify(header.substring(7)); } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) { /* invalid bearer is unauthenticated */ }
            if (ids!=null) auth.authenticate(ids).ifPresent(p -> SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(p,null,java.util.List.of(new SimpleGrantedAuthority("ROLE_"+p.role())))));
        }
        chain.doFilter(request,response);
    }
}
