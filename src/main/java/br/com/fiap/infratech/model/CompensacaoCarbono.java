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
@Table(name = "COMPENSACAO_CARBONO")
@Getter
@Setter
@NoArgsConstructor
public class CompensacaoCarbono {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_COMPENSACAO")
    private Long idCompensacao;

    @Column(name = "ID_UNIDADE")
    private Long idUnidade;

    @Column(name = "PROJETO")
    private String projeto;

    @Column(name = "QTD_TCO2E", precision = 12, scale = 3)
    private BigDecimal qtdTco2e;

    @JdbcTypeCode(SqlTypes.DATE)
    @Column(name = "DATA_REGISTRO", columnDefinition = "DATE")
    private LocalDate dataRegistro;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "ORIGEM")
    private String origem;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "MES_REFERENCIA", length = 7)
    private String mesReferencia;

}
