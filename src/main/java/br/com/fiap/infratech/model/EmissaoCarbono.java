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
@Table(name = "EMISSAO_CARBONO")
@Getter
@Setter
@NoArgsConstructor
public class EmissaoCarbono {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_EMISSAO")
    private Long idEmissao;

    @Column(name = "ID_UNIDADE")
    private Long idUnidade;

    @JdbcTypeCode(SqlTypes.DATE)
    @Column(name = "DATA_REGISTRO", columnDefinition = "DATE")
    private LocalDate dataRegistro;

    @Column(name = "FONTE")
    private String fonte;

    @Column(name = "ESCOPO")
    private Integer escopo;

    @Column(name = "QTD_TCO2E", precision = 12, scale = 3)
    private BigDecimal qtdTco2e;

}
