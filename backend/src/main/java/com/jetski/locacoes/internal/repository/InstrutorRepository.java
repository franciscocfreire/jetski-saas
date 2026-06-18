package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Instrutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InstrutorRepository extends JpaRepository<Instrutor, UUID> {

    List<Instrutor> findByAtivoTrueOrderByNome();

    List<Instrutor> findAllByOrderByNome();
}
