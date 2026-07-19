package com.kickoffsim.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "leagues")
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private LocalDate scheduleStartDate;

    @Column
    private LocalTime scheduleStartTime;

    @OneToMany(mappedBy = "league", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Team> teams = new ArrayList<>();
}
