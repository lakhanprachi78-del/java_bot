package com.ugrocapital.losbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ugrocapital.losbot.entity.LoanApplication;

public interface LoanApplicationJpaRepository extends JpaRepository<LoanApplication, Long> {
}