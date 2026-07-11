package bg.softuni.footballleague.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne
    @JoinColumn(name = "scorer_id", nullable = false)
    private Player scorer;

    @ManyToOne
    @JoinColumn(name = "assistant_id")
    private Player assistant;

    private Integer minute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Half half;

    @Column(nullable = false)
    private boolean ownGoal = false;

    @Column(nullable = false)
    private boolean penalty = false;
}
