package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/leagues")
public class LeagueController {

    private final LeagueService leagueService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("leagues", leagueService.findAll());
        return "leagues/list";
    }
}
