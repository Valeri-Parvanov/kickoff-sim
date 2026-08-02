package com.kickoffsim.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "goals", indexes = {
        @Index(name = "idx_goals_match_id", columnList = "match_id"),
        @Index(name = "idx_goals_notified_match", columnList = "notified, match_id")
})
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scorer_id", nullable = false)
    private Player scorer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_id")
    private Player assistant;

    private Integer minute;

    private Integer offsetSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Half half;

    @Column(nullable = false)
    private boolean ownGoal = false;

    @Column(nullable = false)
    private boolean penalty = false;

    private boolean notified;
}
