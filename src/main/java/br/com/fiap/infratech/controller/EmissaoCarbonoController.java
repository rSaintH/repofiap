package br.com.fiap.infratech.controller;

import br.com.fiap.infratech.dto.EmissaoCarbonoExibicaoDTO;
import br.com.fiap.infratech.dto.EmissaoCarbonoCadastroDTO;
import jakarta.validation.Valid;
import br.com.fiap.infratech.service.EmissaoCarbonoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emissoes")
public class EmissaoCarbonoController {
    @Autowired
    private EmissaoCarbonoService service;

    @GetMapping
    public List<EmissaoCarbonoExibicaoDTO> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public EmissaoCarbonoExibicaoDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmissaoCarbonoExibicaoDTO salvar(@RequestBody @Valid EmissaoCarbonoCadastroDTO dados) {
        return service.salvar(dados);
    }

    @PutMapping("/{id}")
    public EmissaoCarbonoExibicaoDTO atualizar(@PathVariable Long id, @RequestBody @Valid EmissaoCarbonoCadastroDTO dados) {
        return service.atualizar(id, dados);
    }

    @PatchMapping("/{id}")
    public EmissaoCarbonoExibicaoDTO atualizarParcialmente(@PathVariable Long id, @RequestBody Map<String, Object> campos) {
        return service.atualizarParcialmente(id, campos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
