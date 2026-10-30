package br.com.fiap.infratech.advice;

import br.com.fiap.infratech.exception.DadosInvalidosException;
import br.com.fiap.infratech.exception.RecursoNaoEncontradoException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class ApplicationExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleInvalidArgument(MethodArgumentNotValidException error) {
        Map<String, String> errorMap = new HashMap<>();
        List<FieldError> campos = error.getBindingResult().getFieldErrors();
        for (FieldError campo : campos) {
            errorMap.put(campo.getField(), campo.getDefaultMessage());
        }
        return errorMap;
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public Map<String, String> recursoNaoEncontrado(RecursoNaoEncontradoException erro) {
        return Map.of("erro", erro.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(DadosInvalidosException.class)
    public Map<String, String> dadosInvalidos(DadosInvalidosException erro) {
        return Map.of("erro", erro.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statusExplicito(ResponseStatusException erro) {
        // Evitar sendError: o dispatch protegido de /error pode mascarar 409 como 403.
        return ResponseEntity.status(erro.getStatusCode()).body(Map.of("erro",
                erro.getReason() != null ? erro.getReason() : "Não foi possível concluir a operação!"));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public Map<String, String> formatoInvalido(Exception erro) {
        return Map.of("erro", "Dados inválidos. Confira os tipos dos campos e as datas no formato AAAA-MM-DD!");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleIntegrityViolation(DataAccessException erro) {
        StringBuilder mensagens = new StringBuilder();
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa.getMessage() != null) {
                mensagens.append(causa.getMessage()).append('\n');
            }
            if (causa instanceof SQLException sql) {
                for (SQLException proxima = sql.getNextException(); proxima != null; proxima = proxima.getNextException()) {
                    mensagens.append(proxima.getMessage()).append('\n');
                }
            }
        }
        String detalhe = mensagens.toString();
        Matcher negocio = Pattern.compile("ORA-20001:\\s*([^\\r\\n]+)").matcher(detalhe);
        if (negocio.find()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("erro", negocio.group(1).trim()));
        }
        Matcher legado = Pattern.compile("ORA-20002:\\s*([^\\r\\n]+)").matcher(detalhe);
        if (legado.find()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", legado.group(1).trim()));
        }
        if (detalhe.contains("ORA-02292")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", "Não é possível excluir: existem registros vinculados a este recurso!"));
        }
        if (detalhe.contains("ORA-02291")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", "A unidade vinculada não existe mais!"));
        }
        if (detalhe.contains("ORA-00001")) {
            String mensagem = "Registro já cadastrado!";
            if (detalhe.contains("UK_USUARIOS_EMAIL")) {
                mensagem = "Usuário já cadastrado!";
            } else if (detalhe.contains("UQ_LICENCA_NUMERO")) {
                mensagem = "Já existe uma licença com este número!";
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", mensagem));
        }
        if (detalhe.contains("ORA-02290") || detalhe.contains("ORA-01400") || detalhe.contains("ORA-01438") || detalhe.contains("ORA-12899")) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Dados fora dos limites ou valores permitidos!"));
        }
        if (erro instanceof DataIntegrityViolationException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", "Os dados conflitam com um registro existente ou vinculado!"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "Não foi possível concluir a operação no banco de dados!"));
    }
}
