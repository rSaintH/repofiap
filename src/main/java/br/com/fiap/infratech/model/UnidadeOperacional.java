package br.com.fiap.infratech.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name="UNIDADE_OPERACIONAL")
@Getter
@Setter
@NoArgsConstructor
public class UnidadeOperacional {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ID_UNIDADE")
    private Long idUnidade;

    private String nome;
    private String cidade;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "UF", length = 2)
    private String uf;

    private String setor;

    @Column(name="LIMITE_CO2_MENSAL")
    private BigDecimal limiteCo2Mensal;

    @Column(name="EMISSAO_ACUMULADA_MES", insertable = false, updatable = false)
    private BigDecimal emissaoAcumuladaMes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "MES_REFERENCIA", length = 7, insertable = false, updatable = false)
    private String mesReferencia;

}


