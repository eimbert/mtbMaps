package com.paygoon.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Id;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter	
@NoArgsConstructor
@Entity
@Table(name = "loans")
public class Loan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "lender_id", columnDefinition = "BIGINT UNSIGNED")
    private AppUser lender;

    @ManyToOne
    @JoinColumn(name = "borrower_id", columnDefinition = "BIGINT UNSIGNED")
    private AppUser borrower;

    private BigDecimal amount;
    private LocalDate dueDate;
    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean accepted = false;
    private boolean paid = false;
    private boolean requiresVerification = false;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    public enum Status {
        PENDING, ACTIVE, COMPLETED, OVERDUE
    }

}
