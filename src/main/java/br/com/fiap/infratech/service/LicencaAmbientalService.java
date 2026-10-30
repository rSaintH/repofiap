package br.com.fiap.infratech.service;

import br.com.fiap.infratech.dto.LicencaAmbientalCadastroDTO;
import br.com.fiap.infratech.dto.LicencaAmbientalExibicaoDTO;
import br.com.fiap.infratech.exception.DadosInvalidosException;
import br.com.fiap.infratech.exception.RecursoNaoEncontradoException;
import br.com.fiap.infratech.model.LicencaAmbiental;
import br.com.fiap.infratech.repository.LicencaAmbientalRepository;
import br.com.fiap.infratech.repository.UnidadeOperacionalRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class LicencaAmbientalService {
    @Autowired
    private LicencaAmbientalRepository repository;

    @Autowired
    private UnidadeOperacionalRepository unidadeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AtualizacaoParcial atualizacaoParcial;

    public List<LicencaAmbientalExibicaoDTO> listarTodos() {
        return repository.findAll().stream().map(LicencaAmbientalExibicaoDTO::new).toList();
    }

    public LicencaAmbientalExibicaoDTO buscarPorId(Long id) {
        return new LicencaAmbientalExibicaoDTO(buscarLicenca(id));
    }

    @Transactional
    public LicencaAmbientalExibicaoDTO salvar(LicencaAmbientalCadastroDTO dados) {
        validarDados(dados);
        LicencaAmbiental licenca = new LicencaAmbiental();
        preencher(licenca, dados);
        repository.saveAndFlush(licenca);
        entityManager.refresh(licenca);
        return new LicencaAmbientalExibicaoDTO(licenca);
    }

    @Transactional
    public LicencaAmbientalExibicaoDTO atualizar(Long id, LicencaAmbientalCadastroDTO dados) {
        LicencaAmbiental licenca = buscarLicenca(id);
        validarDados(dados);
        preencher(licenca, dados);
        repository.saveAndFlush(licenca);
        entityManager.refresh(licenca);
        return new LicencaAmbientalExibicaoDTO(licenca);
    }

    @Transactional
    public LicencaAmbientalExibicaoDTO atualizarParcialmente(Long id, Map<String, Object> campos) {
        LicencaAmbientalCadastroDTO dados = atualizacaoParcial.mesclar(buscarPorId(id), campos, LicencaAmbientalCadastroDTO.class);
        return atualizar(id, dados);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarLicenca(id));
        repository.flush();
    }

    private LicencaAmbiental buscarLicenca(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Licença não encontrada!"));
    }

    private void validarDados(LicencaAmbientalCadastroDTO dados) {
        if (!unidadeRepository.existsById(dados.idUnidade())) {
            throw new RecursoNaoEncontradoException("Unidade não encontrada!");
        }
        if (!dados.dataValidade().isAfter(dados.dataEmissao())) {
            throw new DadosInvalidosException("A validade deve ser posterior à data de emissão!");
        }
    }

    private void preencher(LicencaAmbiental licenca, LicencaAmbientalCadastroDTO dados) {
        BeanUtils.copyProperties(dados, licenca);
        // Somente a suspensão é informada manualmente. Os outros status são calculados no trigger.
        licenca.setStatus("SUSPENSA".equals(dados.status()) ? "SUSPENSA" : null);
    }
}
