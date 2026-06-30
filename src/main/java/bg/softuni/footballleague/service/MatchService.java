package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.MatchDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface MatchService {

    List<MatchDto> findAll();

    List<MatchDto> findAll(Sort sort);

    List<MatchDto> findAllByTeam(UUID teamId);

    MatchDto findById(UUID id);

    MatchDto create(MatchDto matchDto);

    MatchDto update(UUID id, MatchDto matchDto);

    void delete(UUID id);
}
