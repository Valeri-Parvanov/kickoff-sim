package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.MatchDto;

import java.util.List;

public interface MatchService {

    List<MatchDto> findAll();

    List<MatchDto> findAllByTeam(Long teamId);

    MatchDto findById(Long id);

    MatchDto create(MatchDto matchDto);

    MatchDto update(Long id, MatchDto matchDto);

    void delete(Long id);
}
