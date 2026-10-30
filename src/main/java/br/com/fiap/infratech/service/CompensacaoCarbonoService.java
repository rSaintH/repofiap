package br.com.fiap.infratech.service;

import br.com.fiap.infratech.dto.CompensacaoCarbonoCadastroDTO;
import br.com.fiap.infratech.dto.CompensacaoCarbonoExibicaoDTO;
import br.com.fiap.infratech.exception.RecursoNaoEncontradoException;
import br.com.fiap.infratech.exception.DadosInvalidosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import br.com.fiap.infratech.model.CompensacaoCarbono;
import br.com.fiap.infratech.repository.CompensacaoCarbonoRepository;
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
public class CompensacaoCarbonoService {
    @Autowired
    private CompensacaoCarbonoRepository repository;

    @Autowired
    private UnidadeOperacionalRepository unidadeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AtualizacaoParcial atualizacaoParcial;

    @Autowired
    private ObjectMapper objectMapper;

    public List<CompensacaoCarbonoExibicaoDTO> listarTodos() {
        return repository.findAll().stream().map(CompensacaoCarbonoExibicaoDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public CompensacaoCarbonoExibicaoDTO buscarPorId(Long id) {
        CompensacaoCarbono registro = buscarRegistro(id);
        entityManager.refresh(registro);
        return new CompensacaoCarbonoExibicaoDTO(registro);
    }

    @Transactional
    public CompensacaoCarbonoExibicaoDTO salvar(CompensacaoCarbonoCadastroDTO dados) {
        CompensacaoCarbono registro = new CompensacaoCarbono();
        bloquearUnidade(dados.idUnidade());
        preencher(registro, dados);
        repository.saveAndFlush(registro);
        entityManager.refresh(registro);
        return new CompensacaoCarbonoExibicaoDTO(registro);
    }

    @Transactional
    public CompensacaoCarbonoExibicaoDTO atualizar(Long id, CompensacaoCarbonoCadastroDTO dados) {
        CompensacaoCarbono registro = bloquearERecarregar(buscarRegistro(id), dados.idUnidade());
        preencher(registro, dados);
        repository.saveAndFlush(registro);
        entityManager.refresh(registro);
        return new CompensacaoCarbonoExibicaoDTO(registro);
    }

    @Transactional
    public CompensacaoCarbonoExibicaoDTO atualizarParcialmente(Long id, Map<String, Object> campos) {
        CompensacaoCarbono registro = buscarRegistro(id);
        Long destino = registro.getIdUnidade();
        // Ler só o destino para ordenar os bloqueios; NÃO mesclar o DTO antigo.
        if (campos.containsKey("idUnidade")) {
            try {
                destino = objectMapper.convertValue(campos.get("idUnidade"), Long.class);
            } catch (IllegalArgumentException erro) {
                throw new DadosInvalidosException("Unidade inválida no PATCH!");
            }
            if (destino == null || destino <= 0) {
                throw new DadosInvalidosException("Unidade inválida no PATCH!");
            }
        }
        registro = bloquearERecarregar(registro, destino);
        CompensacaoCarbonoCadastroDTO dados = atualizacaoParcial.mesclar(
                new CompensacaoCarbonoExibicaoDTO(registro), campos, CompensacaoCarbonoCadastroDTO.class);
        preencher(registro, dados);
        repository.saveAndFlush(registro);
        entityManager.refresh(registro);
        return new CompensacaoCarbonoExibicaoDTO(registro);
    }

    @Transactional
    public void excluir(Long id) {
        CompensacaoCarbono registro = buscarRegistro(id);
        registro = bloquearERecarregar(registro, registro.getIdUnidade());
        repository.delete(registro);
        repository.flush();
    }

    private CompensacaoCarbono bloquearERecarregar(CompensacaoCarbono registro, Long destino) {
        Long anterior = registro.getIdUnidade();
        Long id = registro.getIdCompensacao();
        bloquearUnidade(Math.min(anterior, destino));
        if (!anterior.equals(destino)) bloquearUnidade(Math.max(anterior, destino));
        // O trigger pode ter atualizado ou excluído a linha enquanto aguardávamos.
        // Remover o snapshot do contexto evita merge obsoleto e ressurreição/500.
        entityManager.detach(registro);
        CompensacaoCarbono atual = buscarRegistro(id);
        if (!anterior.equals(atual.getIdUnidade())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A unidade da compensação mudou durante a operação. Tente novamente!");
        }
        return atual;
    }

    private void bloquearUnidade(Long id) {
        // Mesmo bloqueio usado pela reconciliação; não editar uma pendência
        // enquanto o trigger calcula seu saldo/status na outra transação.
        if (entityManager.createNativeQuery("SELECT ID_UNIDADE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ? FOR UPDATE")
                .setParameter(1, id).getResultList().isEmpty()) {
            throw new RecursoNaoEncontradoException("Unidade não encontrada!");
        }
    }

    private CompensacaoCarbono buscarRegistro(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Compensação não encontrada!"));
    }

    private void preencher(CompensacaoCarbono registro, CompensacaoCarbonoCadastroDTO dados) {
        if (!unidadeRepository.existsById(dados.idUnidade())) {
            throw new RecursoNaoEncontradoException("Unidade não encontrada!");
        }
        Long unidadeAnterior = registro.getIdUnidade();
        String origemAnterior = registro.getOrigem();
        BeanUtils.copyProperties(dados, registro);
        if (registro.getDataRegistro() == null) {
            registro.setDataRegistro(LocalDate.now());
        }
        if (registro.getStatus() == null) registro.setStatus("PENDENTE");
        if (registro.getOrigem() == null) registro.setOrigem("MANUAL");
        // A referência pertence à sugestão gerada pelo banco, não é entrada livre.
        if (!"AUTOMATICA".equals(registro.getOrigem())) {
            registro.setMesReferencia(null);
        } else if (registro.getIdCompensacao() == null || !"AUTOMATICA".equals(origemAnterior)
                || (registro.getMesReferencia() != null && !dados.idUnidade().equals(unidadeAnterior))) {
            // Legado AUTOMATICA com mês NULL continua desconhecido, mesmo ao mover.
            registro.setMesReferencia(registro.getDataRegistro().toString().substring(0, 7));
        }
    }
}
