package br.com.fiap.infratech.controller;

import br.com.fiap.infratech.dto.LicencaAmbientalExibicaoDTO;
import br.com.fiap.infratech.dto.LicencaAmbientalCadastroDTO;
import jakarta.validation.Valid;
import br.com.fiap.infratech.service.LicencaAmbientalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/licencas")
public class LicencaAmbientalController {
    @Autowired
    private LicencaAmbientalService service;

    @GetMapping
    public List<LicencaAmbientalExibicaoDTO> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public LicencaAmbientalExibicaoDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LicencaAmbientalExibicaoDTO salvar(@RequestBody @Valid LicencaAmbientalCadastroDTO dados) {
        return service.salvar(dados);
    }

    @PutMapping("/{id}")
    public LicencaAmbientalExibicaoDTO atualizar(@PathVariable Long id, @RequestBody @Valid LicencaAmbientalCadastroDTO dados) {
        return service.atualizar(id, dados);
    }

    @PatchMapping("/{id}")
    public LicencaAmbientalExibicaoDTO atualizarParcialmente(@PathVariable Long id, @RequestBody Map<String, Object> campos) {
        return service.atualizarParcialmente(id, campos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
