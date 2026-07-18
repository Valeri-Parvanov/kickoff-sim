package bg.softuni.footballleague.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @PositiveOrZero
    private Integer homeScore;

    @PositiveOrZero
    private Integer awayScore;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime playedAt;

    private Integer roundNumber;

    private boolean kickoffNotified;
    private boolean halftimeNotified;
    private boolean fulltimeNotified;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("half ASC, minute ASC")
    private List<Goal> goals = new ArrayList<>();
}
