package br.com.fiap.infratech.dto;

import br.com.fiap.infratech.model.Usuario;

public record UsuarioExibicaoDTO(
        Long usuarioId,
        String nome,
        String email) {

    public UsuarioExibicaoDTO(Usuario usuario) {
        this(
            usuario.getUsuarioId(),
            usuario.getNome(),
            usuario.getEmail());
    }

}
