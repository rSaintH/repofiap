package br.com.fiap.infratech.service;

import br.com.fiap.infratech.dto.UnidadeOperacionalCadastroDTO;
import br.com.fiap.infratech.dto.UnidadeOperacionalExibicaoDTO;
import br.com.fiap.infratech.exception.RecursoNaoEncontradoException;
import br.com.fiap.infratech.model.UnidadeOperacional;
import br.com.fiap.infratech.repository.UnidadeOperacionalRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UnidadeOperacionalService {
    @Autowired
    private UnidadeOperacionalRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AtualizacaoParcial atualizacaoParcial;

    public List<UnidadeOperacionalExibicaoDTO> listarTodos() {
        return repository.findAll().stream().map(UnidadeOperacionalExibicaoDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public UnidadeOperacionalExibicaoDTO buscarPorId(Long id) {
        UnidadeOperacional unidade = buscarUnidade(id);
        entityManager.refresh(unidade);
        return new UnidadeOperacionalExibicaoDTO(unidade);
    }

    @Transactional
    public UnidadeOperacionalExibicaoDTO salvar(UnidadeOperacionalCadastroDTO dados) {
        UnidadeOperacional unidade = new UnidadeOperacional();
        BeanUtils.copyProperties(dados, unidade);
        repository.saveAndFlush(unidade);
        entityManager.refresh(unidade);
        return new UnidadeOperacionalExibicaoDTO(unidade);
    }

    @Transactional
    public UnidadeOperacionalExibicaoDTO atualizar(Long id, UnidadeOperacionalCadastroDTO dados) {
        UnidadeOperacional unidade = buscarUnidade(id);
        // Acumulado e mês de referência não fazem parte dos dados de entrada.
        BeanUtils.copyProperties(dados, unidade);
        repository.saveAndFlush(unidade);
        entityManager.refresh(unidade);
        return new UnidadeOperacionalExibicaoDTO(unidade);
    }

    @Transactional
    public UnidadeOperacionalExibicaoDTO atualizarParcialmente(Long id, Map<String, Object> campos) {
        UnidadeOperacionalCadastroDTO dados = atualizacaoParcial.mesclar(buscarPorId(id), campos, UnidadeOperacionalCadastroDTO.class);
        return atualizar(id, dados);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarUnidade(id));
        repository.flush();
    }

    private UnidadeOperacional buscarUnidade(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Unidade não encontrada!"));
    }
}
