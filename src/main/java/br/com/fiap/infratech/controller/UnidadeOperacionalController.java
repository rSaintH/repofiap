package br.com.fiap.infratech.controller;

import br.com.fiap.infratech.dto.UnidadeOperacionalExibicaoDTO;
import br.com.fiap.infratech.dto.UnidadeOperacionalCadastroDTO;
import jakarta.validation.Valid;
import br.com.fiap.infratech.service.UnidadeOperacionalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unidades")
public class UnidadeOperacionalController {
    @Autowired
    private UnidadeOperacionalService service;

    @GetMapping
    public List<UnidadeOperacionalExibicaoDTO> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public UnidadeOperacionalExibicaoDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnidadeOperacionalExibicaoDTO salvar(@RequestBody @Valid UnidadeOperacionalCadastroDTO dados) {
        return service.salvar(dados);
    }

    @PutMapping("/{id}")
    public UnidadeOperacionalExibicaoDTO atualizar(@PathVariable Long id, @RequestBody @Valid UnidadeOperacionalCadastroDTO dados) {
        return service.atualizar(id, dados);
    }

    @PatchMapping("/{id}")
    public UnidadeOperacionalExibicaoDTO atualizarParcialmente(@PathVariable Long id, @RequestBody Map<String, Object> campos) {
        return service.atualizarParcialmente(id, campos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
