package br.com.fiap.infratech.repository;

import br.com.fiap.infratech.model.CompensacaoCarbono;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensacaoCarbonoRepository extends JpaRepository<CompensacaoCarbono, Long> {
}
