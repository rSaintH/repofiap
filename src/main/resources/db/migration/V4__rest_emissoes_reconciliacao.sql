-- V1-V3 permanecem imutáveis. Sem reset, alteração dos seeds ou exclusão de histórico.
ALTER TABLE COMPENSACAO_CARBONO ADD (MES_REFERENCIA CHAR(7));
ALTER TABLE COMPENSACAO_CARBONO ADD CONSTRAINT CK_COMP_MES_REF
    CHECK (MES_REFERENCIA IS NULL OR REGEXP_LIKE(MES_REFERENCIA, '^[0-9]{4}-(0[1-9]|1[0-2])$'));

-- V2 não gravava o mês da obrigação (DATA_REGISTRO era SYSDATE, inclusive para
-- emissão retroativa). Só atribuir quando existe exatamente um mês de emissões
-- na unidade: caso dos seeds V3. Histórico ambíguo permanece com referência NULL.
-- Não modificar quantidade, projeto, origem ou andamento de registros existentes.
UPDATE COMPENSACAO_CARBONO c
SET MES_REFERENCIA = (
    SELECT MIN(TO_CHAR(e.DATA_REGISTRO, 'YYYY-MM'))
    FROM EMISSAO_CARBONO e WHERE e.ID_UNIDADE = c.ID_UNIDADE
)
WHERE c.ORIGEM = 'AUTOMATICA' AND c.PROJETO = 'A_DEFINIR'
  AND (SELECT COUNT(DISTINCT TO_CHAR(e.DATA_REGISTRO, 'YYYY-MM'))
       FROM EMISSAO_CARBONO e WHERE e.ID_UNIDADE = c.ID_UNIDADE) = 1;

-- A reconciliação deixa de depender de UPDATE na unidade. Isso permite excluir
-- todas as compensações via CRUD; sugestões só reaparecem na próxima emissão.
DROP TRIGGER TRG_COMPENSACAO_AUTOMATICA;
DROP TRIGGER TRG_ALERTA_LIMITE_EMISSAO;
DROP TRIGGER TRG_BLOQUEIO_SEM_LICENCA;

CREATE OR REPLACE PROCEDURE PRC_RECALCULA_EMISSOES(P_ID_UNIDADE IN NUMBER) IS
    V_LIMITE NUMBER;
    V_MES CHAR(7);
    V_TOTAL NUMBER;
    V_COMPROMETIDO NUMBER;
    V_PENDENTE NUMBER;
    V_ID NUMBER;
BEGIN
    SELECT LIMITE_CO2_MENSAL INTO V_LIMITE
      FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = P_ID_UNIDADE FOR UPDATE;

    -- Mês exibido = maior mês com lançamentos, não o mês do último INSERT.
    SELECT MAX(TO_CHAR(DATA_REGISTRO, 'YYYY-MM')) INTO V_MES
      FROM EMISSAO_CARBONO WHERE ID_UNIDADE = P_ID_UNIDADE;
    SELECT NVL(SUM(QTD_TCO2E), 0) INTO V_TOTAL
      FROM EMISSAO_CARBONO
     WHERE ID_UNIDADE = P_ID_UNIDADE AND TO_CHAR(DATA_REGISTRO, 'YYYY-MM') = V_MES;
    UPDATE UNIDADE_OPERACIONAL
       SET MES_REFERENCIA = V_MES, EMISSAO_ACUMULADA_MES = V_TOTAL
     WHERE ID_UNIDADE = P_ID_UNIDADE;

    -- Meses sem emissões mas com sugestão antiga também entram para zerar pendências.
    FOR r IN (
        SELECT TO_CHAR(DATA_REGISTRO, 'YYYY-MM') AS MES FROM EMISSAO_CARBONO
         WHERE ID_UNIDADE = P_ID_UNIDADE
        UNION
        SELECT MES_REFERENCIA AS MES FROM COMPENSACAO_CARBONO
         WHERE ID_UNIDADE = P_ID_UNIDADE AND ORIGEM = 'AUTOMATICA' AND MES_REFERENCIA IS NOT NULL
    ) LOOP
        SELECT NVL(SUM(QTD_TCO2E), 0) INTO V_TOTAL FROM EMISSAO_CARBONO
         WHERE ID_UNIDADE = P_ID_UNIDADE AND TO_CHAR(DATA_REGISTRO, 'YYYY-MM') = r.MES;
        -- Nunca reduzir/apagar compensação em andamento ou concluída. Ela já
        -- compromete parte do excedente mensal e não deve gerar outra sugestão igual.
        SELECT NVL(SUM(QTD_TCO2E), 0) INTO V_COMPROMETIDO FROM COMPENSACAO_CARBONO
         WHERE ID_UNIDADE = P_ID_UNIDADE AND ORIGEM = 'AUTOMATICA'
           AND MES_REFERENCIA = r.MES AND STATUS IN ('EM_ANDAMENTO', 'CONCLUIDA');
        V_PENDENTE := GREATEST(V_TOTAL - V_LIMITE - V_COMPROMETIDO, 0);
        SELECT MIN(ID_COMPENSACAO) INTO V_ID FROM COMPENSACAO_CARBONO
         WHERE ID_UNIDADE = P_ID_UNIDADE AND ORIGEM = 'AUTOMATICA'
           AND MES_REFERENCIA = r.MES AND STATUS = 'PENDENTE';
        IF V_PENDENTE = 0 THEN
            DELETE FROM COMPENSACAO_CARBONO
             WHERE ID_UNIDADE = P_ID_UNIDADE AND ORIGEM = 'AUTOMATICA'
               AND MES_REFERENCIA = r.MES AND STATUS = 'PENDENTE';
        ELSIF V_ID IS NOT NULL THEN
            UPDATE COMPENSACAO_CARBONO SET QTD_TCO2E = V_PENDENTE WHERE ID_COMPENSACAO = V_ID;
            DELETE FROM COMPENSACAO_CARBONO
             WHERE ID_UNIDADE = P_ID_UNIDADE AND ORIGEM = 'AUTOMATICA'
               AND MES_REFERENCIA = r.MES AND STATUS = 'PENDENTE' AND ID_COMPENSACAO <> V_ID;
        ELSE
            INSERT INTO COMPENSACAO_CARBONO
                (ID_UNIDADE, PROJETO, QTD_TCO2E, DATA_REGISTRO, STATUS, ORIGEM, MES_REFERENCIA)
            VALUES (P_ID_UNIDADE, 'A_DEFINIR', V_PENDENTE, SYSDATE, 'PENDENTE', 'AUTOMATICA', r.MES);
        END IF;
        -- Alertas representam eventos históricos, não o saldo atual do mês.
        IF V_TOTAL > V_LIMITE THEN
            INSERT INTO ALERTA_COMPLIANCE (ID_UNIDADE, TIPO_ALERTA, DESCRICAO, DATA_ALERTA, STATUS)
            VALUES (P_ID_UNIDADE, 'LIMITE_EMISSAO',
                'Emissão acumulada de ' || r.MES || ' = ' || TO_CHAR(V_TOTAL) ||
                ' tCO2e, acima do limite de ' || TO_CHAR(V_LIMITE) ||
                ' tCO2e (excedente: ' || TO_CHAR(V_TOTAL - V_LIMITE) || ' tCO2e).', SYSDATE, 'ABERTO');
        END IF;
    END LOOP;
END;
/

CREATE OR REPLACE TRIGGER TRG_EMISSAO_REST
FOR INSERT OR UPDATE OR DELETE ON EMISSAO_CARBONO
COMPOUND TRIGGER
    TYPE T_UNIDADES IS TABLE OF NUMBER INDEX BY VARCHAR2(40);
    V_UNIDADES T_UNIDADES;

    PROCEDURE BLOQUEAR_UNIDADE(P_ID IN NUMBER) IS
        V_ID NUMBER;
        V_LEGADO NUMBER;
    BEGIN
        IF P_ID IS NOT NULL THEN
            SELECT ID_UNIDADE INTO V_ID FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = P_ID FOR UPDATE;
            V_UNIDADES(TO_CHAR(P_ID)) := P_ID;
            -- Não inventar o mês de automáticas legadas em QUALQUER status.
            -- Concluir não resolve a referência desconhecida nem evita duplicação.
            -- Exigir conversão MANUAL, exclusão ou atribuição explícita revisada.
            SELECT COUNT(*) INTO V_LEGADO FROM COMPENSACAO_CARBONO
             WHERE ID_UNIDADE = P_ID AND ORIGEM = 'AUTOMATICA'
               AND MES_REFERENCIA IS NULL;
            IF V_LEGADO > 0 THEN
                RAISE_APPLICATION_ERROR(-20002,
                    'Compensações automáticas legadas sem mês de referência: revisar antes de alterar emissões.');
            END IF;
        END IF;
    END;

    BEFORE EACH ROW IS
        V_QTD_LO NUMBER;
        V_NOME UNIDADE_OPERACIONAL.NOME%TYPE;
    BEGIN
        IF INSERTING OR UPDATING THEN
            SELECT COUNT(*) INTO V_QTD_LO FROM LICENCA_AMBIENTAL
             WHERE ID_UNIDADE = :NEW.ID_UNIDADE AND TIPO = 'LO' AND STATUS <> 'SUSPENSA'
               AND TRUNC(:NEW.DATA_REGISTRO) BETWEEN TRUNC(DATA_EMISSAO) AND TRUNC(DATA_VALIDADE);
            IF V_QTD_LO = 0 THEN
                SELECT NOME INTO V_NOME FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = :NEW.ID_UNIDADE;
                PRC_REGISTRA_ALERTA(:NEW.ID_UNIDADE, 'OPERACAO_SEM_LICENCA',
                    'Tentativa de registrar ' || TO_CHAR(:NEW.QTD_TCO2E) || ' tCO2e (' || :NEW.FONTE ||
                    ') na unidade "' || V_NOME || '" sem Licença de Operação válida. Registro bloqueado.');
                RAISE_APPLICATION_ERROR(-20001,
                    'Unidade ' || :NEW.ID_UNIDADE || ' (' || V_NOME ||
                    ') sem Licença de Operação válida. Operação bloqueada.');
            END IF;
        END IF;
        -- Serializar por unidade antes de alterar lançamentos. Para mudança de
        -- unidade, adquirir ambas na mesma ordem (evita inversão A->B / B->A).
        IF UPDATING AND :OLD.ID_UNIDADE <> :NEW.ID_UNIDADE THEN
            BLOQUEAR_UNIDADE(LEAST(:OLD.ID_UNIDADE, :NEW.ID_UNIDADE));
            BLOQUEAR_UNIDADE(GREATEST(:OLD.ID_UNIDADE, :NEW.ID_UNIDADE));
        ELSIF DELETING THEN
            BLOQUEAR_UNIDADE(:OLD.ID_UNIDADE);
        ELSE
            BLOQUEAR_UNIDADE(:NEW.ID_UNIDADE);
        END IF;
    END BEFORE EACH ROW;

    AFTER STATEMENT IS
        V_CHAVE VARCHAR2(40);
    BEGIN
        V_CHAVE := V_UNIDADES.FIRST;
        WHILE V_CHAVE IS NOT NULL LOOP
            PRC_RECALCULA_EMISSOES(V_UNIDADES(V_CHAVE));
            V_CHAVE := V_UNIDADES.NEXT(V_CHAVE);
        END LOOP;
    END AFTER STATEMENT;
END;
/

-- Falhar a migration se o Oracle tiver criado objetos INVALID em vez de
-- postergar o erro de compilação até a primeira chamada REST.
DECLARE
    V_ERROS NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_ERROS FROM USER_ERRORS
     WHERE NAME IN ('PRC_RECALCULA_EMISSOES', 'TRG_EMISSAO_REST') AND ATTRIBUTE = 'ERROR';
    IF V_ERROS > 0 THEN
        RAISE_APPLICATION_ERROR(-20003, 'Erro de compilação da V4: consultar USER_ERRORS.');
    END IF;
END;
/
