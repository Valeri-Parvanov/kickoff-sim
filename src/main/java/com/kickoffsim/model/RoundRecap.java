package com.kickoffsim.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "round_recaps", uniqueConstraints = @UniqueConstraint(
        name = "uk_round_recap_league_round_locale",
        columnNames = {"league_id", "round_number", "locale_tag"}))
public class RoundRecap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private League league;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "locale_tag", nullable = false, length = 2)
    private String localeTag;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @Column(nullable = false, length = 64)
    private String sourceFingerprint;
}
