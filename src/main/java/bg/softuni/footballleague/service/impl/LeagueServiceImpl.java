package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeagueServiceImpl implements LeagueService {

    private final LeagueRepository leagueRepository;

    @Override
    public List<LeagueDto> findAll() {
        return leagueRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public LeagueDto findById(UUID id) {
        return toDto(getLeagueOrThrow(id));
    }

    @Override
    public LeagueDto create(LeagueDto leagueDto) {
        League league = new League();
        mapToEntity(leagueDto, league);
        return toDto(leagueRepository.save(league));
    }

    @Override
    public LeagueDto update(UUID id, LeagueDto leagueDto) {
        League league = getLeagueOrThrow(id);
        mapToEntity(leagueDto, league);
        return toDto(leagueRepository.save(league));
    }

    @Override
    public void delete(UUID id) {
        leagueRepository.delete(getLeagueOrThrow(id));
    }

    private League getLeagueOrThrow(UUID id) {
        return leagueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("League with id %s not found".formatted(id)));
    }

    private void mapToEntity(LeagueDto leagueDto, League league) {
        league.setName(leagueDto.getName());
        league.setCountry(leagueDto.getCountry());
    }

    private LeagueDto toDto(League league) {
        LeagueDto leagueDto = new LeagueDto();
        leagueDto.setId(league.getId());
        leagueDto.setName(league.getName());
        leagueDto.setCountry(league.getCountry());
        return leagueDto;
    }
}
