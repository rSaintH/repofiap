package br.com.fiap.infratech.service;

import br.com.fiap.infratech.dto.EmissaoCarbonoCadastroDTO;
import br.com.fiap.infratech.dto.EmissaoCarbonoExibicaoDTO;
import br.com.fiap.infratech.exception.RecursoNaoEncontradoException;
import br.com.fiap.infratech.model.EmissaoCarbono;
import br.com.fiap.infratech.repository.EmissaoCarbonoRepository;
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
public class EmissaoCarbonoService {
    @Autowired
    private EmissaoCarbonoRepository repository;

    @Autowired
    private UnidadeOperacionalRepository unidadeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AtualizacaoParcial atualizacaoParcial;

    public List<EmissaoCarbonoExibicaoDTO> listarTodos() {
        return repository.findAll().stream().map(EmissaoCarbonoExibicaoDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public EmissaoCarbonoExibicaoDTO buscarPorId(Long id) {
        EmissaoCarbono registro = buscarRegistro(id);
        entityManager.refresh(registro);
        return new EmissaoCarbonoExibicaoDTO(registro);
    }

    @Transactional
    public EmissaoCarbonoExibicaoDTO salvar(EmissaoCarbonoCadastroDTO dados) {
        EmissaoCarbono registro = new EmissaoCarbono();
        preencher(registro, dados);
        repository.saveAndFlush(registro);
        entityManager.refresh(registro);
        return new EmissaoCarbonoExibicaoDTO(registro);
    }

    @Transactional
    public EmissaoCarbonoExibicaoDTO atualizar(Long id, EmissaoCarbonoCadastroDTO dados) {
        EmissaoCarbono registro = buscarRegistro(id);
        preencher(registro, dados);
        repository.saveAndFlush(registro);
        entityManager.refresh(registro);
        return new EmissaoCarbonoExibicaoDTO(registro);
    }

    @Transactional
    public EmissaoCarbonoExibicaoDTO atualizarParcialmente(Long id, Map<String, Object> campos) {
        EmissaoCarbonoCadastroDTO dados = atualizacaoParcial.mesclar(buscarPorId(id), campos, EmissaoCarbonoCadastroDTO.class);
        return atualizar(id, dados);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarRegistro(id));
        repository.flush();
    }

    private EmissaoCarbono buscarRegistro(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Emissão não encontrada!"));
    }

    private void preencher(EmissaoCarbono registro, EmissaoCarbonoCadastroDTO dados) {
        if (!unidadeRepository.existsById(dados.idUnidade())) {
            throw new RecursoNaoEncontradoException("Unidade não encontrada!");
        }
        BeanUtils.copyProperties(dados, registro);
        if (registro.getDataRegistro() == null) {
            registro.setDataRegistro(LocalDate.now());
        }
    }
}
