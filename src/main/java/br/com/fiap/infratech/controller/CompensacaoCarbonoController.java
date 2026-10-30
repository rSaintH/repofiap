package br.com.fiap.infratech.controller;

import br.com.fiap.infratech.dto.CompensacaoCarbonoExibicaoDTO;
import br.com.fiap.infratech.dto.CompensacaoCarbonoCadastroDTO;
import jakarta.validation.Valid;
import br.com.fiap.infratech.service.CompensacaoCarbonoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compensacoes")
public class CompensacaoCarbonoController {
    @Autowired
    private CompensacaoCarbonoService service;

    @GetMapping
    public List<CompensacaoCarbonoExibicaoDTO> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public CompensacaoCarbonoExibicaoDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompensacaoCarbonoExibicaoDTO salvar(@RequestBody @Valid CompensacaoCarbonoCadastroDTO dados) {
        return service.salvar(dados);
    }

    @PutMapping("/{id}")
    public CompensacaoCarbonoExibicaoDTO atualizar(@PathVariable Long id, @RequestBody @Valid CompensacaoCarbonoCadastroDTO dados) {
        return service.atualizar(id, dados);
    }

    @PatchMapping("/{id}")
    public CompensacaoCarbonoExibicaoDTO atualizarParcialmente(@PathVariable Long id, @RequestBody Map<String, Object> campos) {
        return service.atualizarParcialmente(id, campos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
