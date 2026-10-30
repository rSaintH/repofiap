package br.com.fiap.infratech.repository;

import br.com.fiap.infratech.model.EmissaoCarbono;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmissaoCarbonoRepository extends JpaRepository<EmissaoCarbono, Long> {
}
