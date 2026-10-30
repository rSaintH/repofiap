package br.com.fiap.infratech.controller;

import br.com.fiap.infratech.dto.AlertaComplianceExibicaoDTO;
import br.com.fiap.infratech.dto.AlertaComplianceCadastroDTO;
import jakarta.validation.Valid;
import br.com.fiap.infratech.service.AlertaComplianceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alertas")
public class AlertaComplianceController {
    @Autowired
    private AlertaComplianceService service;

    @GetMapping
    public List<AlertaComplianceExibicaoDTO> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public AlertaComplianceExibicaoDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertaComplianceExibicaoDTO salvar(@RequestBody @Valid AlertaComplianceCadastroDTO dados) {
        return service.salvar(dados);
    }

    @PutMapping("/{id}")
    public AlertaComplianceExibicaoDTO atualizar(@PathVariable Long id, @RequestBody @Valid AlertaComplianceCadastroDTO dados) {
        return service.atualizar(id, dados);
    }

    @PatchMapping("/{id}")
    public AlertaComplianceExibicaoDTO atualizarParcialmente(@PathVariable Long id, @RequestBody Map<String, Object> campos) {
        return service.atualizarParcialmente(id, campos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
