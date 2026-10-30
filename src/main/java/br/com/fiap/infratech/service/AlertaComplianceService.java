package br.com.fiap.infratech.service;

import br.com.fiap.infratech.dto.AlertaComplianceCadastroDTO;
import br.com.fiap.infratech.dto.AlertaComplianceExibicaoDTO;
import br.com.fiap.infratech.exception.RecursoNaoEncontradoException;
import br.com.fiap.infratech.model.AlertaCompliance;
import br.com.fiap.infratech.repository.AlertaComplianceRepository;
import br.com.fiap.infratech.repository.UnidadeOperacionalRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class AlertaComplianceService {
    @Autowired
    private AlertaComplianceRepository repository;

    @Autowired
    private UnidadeOperacionalRepository unidadeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AtualizacaoParcial atualizacaoParcial;

    public List<AlertaComplianceExibicaoDTO> listarTodos() {
        return repository.findAll().stream().map(AlertaComplianceExibicaoDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public AlertaComplianceExibicaoDTO buscarPorId(Long id) {
        AlertaCompliance registro = buscarRegistro(id);
        entityManager.refresh(registro);
        return new AlertaComplianceExibicaoDTO(registro);
    }

    @Transactional
    public AlertaComplianceExibicaoDTO salvar(AlertaComplianceCadastroDTO dados) {
        AlertaCompliance registro = new AlertaCompliance();
        preencher(registro, dados);
        repository.saveAndFlush(registro);
        entityManager.refresh(registro);
        return new AlertaComplianceExibicaoDTO(registro);
    }

    @Transactional
    public AlertaComplianceExibicaoDTO atualizar(Long id, AlertaComplianceCadastroDTO dados) {
        AlertaCompliance registro = buscarRegistro(id);
        preencher(registro, dados);
        repository.saveAndFlush(registro);
        entityManager.refresh(registro);
        return new AlertaComplianceExibicaoDTO(registro);
    }

    @Transactional
    public AlertaComplianceExibicaoDTO atualizarParcialmente(Long id, Map<String, Object> campos) {
        AlertaComplianceCadastroDTO dados = atualizacaoParcial.mesclar(buscarPorId(id), campos, AlertaComplianceCadastroDTO.class);
        return atualizar(id, dados);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarRegistro(id));
        repository.flush();
    }

    private AlertaCompliance buscarRegistro(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Alerta não encontrado!"));
    }

    private void preencher(AlertaCompliance registro, AlertaComplianceCadastroDTO dados) {
        if (!unidadeRepository.existsById(dados.idUnidade())) {
            throw new RecursoNaoEncontradoException("Unidade não encontrada!");
        }
        BeanUtils.copyProperties(dados, registro);
        if (registro.getDataAlerta() == null) {
            registro.setDataAlerta(LocalDate.now());
        }
        if (registro.getStatus() == null) registro.setStatus("ABERTO");
    }
}
