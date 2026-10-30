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
@Table(name = "ALERTA_COMPLIANCE")
@Getter
@Setter
@NoArgsConstructor
public class AlertaCompliance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ALERTA")
    private Long idAlerta;

    @Column(name = "ID_UNIDADE")
    private Long idUnidade;

    @Column(name = "TIPO_ALERTA")
    private String tipoAlerta;

    @Column(name = "DESCRICAO")
    private String descricao;

    @JdbcTypeCode(SqlTypes.DATE)
    @Column(name = "DATA_ALERTA", columnDefinition = "DATE")
    private LocalDate dataAlerta;

    @Column(name = "STATUS")
    private String status;

}
