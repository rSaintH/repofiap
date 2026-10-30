package br.com.fiap.infratech.config.security;

import br.com.fiap.infratech.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class VerificarToken extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null) {
            if (!authorizationHeader.startsWith("Bearer ")) {
                SecurityContextHolder.clearContext();
                responderTokenInvalido(response);
                return;
            }
            String token = authorizationHeader.substring(7).trim();
            String login = tokenService.validarToken(token);
            if (login == null || login.isBlank()) {
                SecurityContextHolder.clearContext();
                responderTokenInvalido(response);
                return;
            }
            UserDetails usuario = usuarioRepository.findByEmail(login);
            if (usuario == null) {
                SecurityContextHolder.clearContext();
                responderTokenInvalido(response);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);

    }

    private void responderTokenInvalido(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"erro\":\"Token inválido ou expirado!\"}");
    }
}