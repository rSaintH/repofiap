package br.com.fiap.infratech.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "LICENCA_AMBIENTAL")
@Getter
@Setter
@NoArgsConstructor
public class LicencaAmbiental {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_LICENCA")
    private Long idLicenca;

    @Column(name = "ID_UNIDADE")
    private Long idUnidade;

    @Column(name = "NUMERO")
    private String numero;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TIPO", length = 2)
    private String tipo;

    @Column(name = "ORGAO_EMISSOR")
    private String orgaoEmissor;

    @JdbcTypeCode(SqlTypes.DATE)
    @Column(name = "DATA_EMISSAO", columnDefinition = "DATE")
    private LocalDate dataEmissao;

    @JdbcTypeCode(SqlTypes.DATE)
    @Column(name = "DATA_VALIDADE", columnDefinition = "DATE")
    private LocalDate dataValidade;

    @Column(name = "STATUS")
    private String status;

}
