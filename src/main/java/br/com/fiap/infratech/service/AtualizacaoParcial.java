package br.com.fiap.infratech.service;

import br.com.fiap.infratech.exception.DadosInvalidosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AtualizacaoParcial {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    // A lista permitida vem somente do DTO de entrada, nunca da entidade/DTO de saída.
    public <T> T mesclar(Object atual, Map<String, Object> campos, Class<T> tipo) {
        Set<String> permitidos = Arrays.stream(tipo.getRecordComponents())
                .map(componente -> componente.getName()).collect(Collectors.toSet());
        if (!permitidos.containsAll(campos.keySet())) {
            throw new DadosInvalidosException("Campo desconhecido, identificador imutável ou campo calculado no PATCH!");
        }
        ObjectNode origem = objectMapper.valueToTree(atual);
        ObjectNode mesclado = objectMapper.createObjectNode();
        for (String campo : permitidos) {
            mesclado.set(campo, origem.get(campo));
        }
        // containsKey diferencia omissão de null explícito; não ignorar os nulos recebidos.
        campos.forEach((campo, valor) -> mesclado.set(campo, objectMapper.valueToTree(valor)));
        T dados;
        try {
            dados = objectMapper.convertValue(mesclado, tipo);
        } catch (IllegalArgumentException erro) {
            throw new DadosInvalidosException("Tipos de campos ou datas inválidos no PATCH!");
        }
        var violacoes = validator.validate(dados);
        if (!violacoes.isEmpty()) {
            throw new DadosInvalidosException(violacoes.iterator().next().getMessage());
        }
        return dados;
    }
}
