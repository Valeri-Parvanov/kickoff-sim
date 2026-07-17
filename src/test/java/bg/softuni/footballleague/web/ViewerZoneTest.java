package bg.softuni.footballleague.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerZoneTest {

    private final ViewerZone viewerZone = new ViewerZone();

    @Test
    void dateOf_sameZoneAsLeague_keepsTheMatchDay() {
        LocalDateTime playedAt = LocalDateTime.of(2026, 7, 14, 22, 0);

        assertThat(viewerZone.dateOf(playedAt, ViewerZone.LEAGUE_ZONE))
                .isEqualTo(LocalDate.of(2026, 7, 14));
    }

    @Test
    void dateOf_lateSofiaEvening_isNextDayForSeoulViewer() {
        LocalDateTime playedAt = LocalDateTime.of(2026, 7, 14, 22, 0);

        assertThat(viewerZone.dateOf(playedAt, ZoneId.of("Asia/Seoul")))
                .isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void dateOf_justAfterSofiaMidnight_isStillPreviousDayForBerlinViewer() {
        LocalDateTime playedAt = LocalDateTime.of(2026, 7, 15, 0, 30);

        assertThat(viewerZone.dateOf(playedAt, ZoneId.of("Europe/Berlin")))
                .isEqualTo(LocalDate.of(2026, 7, 14));
    }

    @Test
    void startOfDay_berlinDayStartsAnHourLaterInLeagueZone() {
        LocalDateTime start = viewerZone.startOfDay(LocalDate.of(2026, 7, 15), ZoneId.of("Europe/Berlin"));

        assertThat(start).isEqualTo(LocalDateTime.of(2026, 7, 15, 1, 0));
    }

    @Test
    void resolve_noCookies_fallsBackToLeagueZone() {
        assertThat(viewerZone.resolve(new MockHttpServletRequest()))
                .isEqualTo(ViewerZone.LEAGUE_ZONE);
    }

    @Test
    void resolve_encodedCookie_returnsViewerZone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("tz", "Asia%2FSeoul"));

        assertThat(viewerZone.resolve(request)).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void resolve_garbageCookie_fallsBackToLeagueZone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("tz", "not-a-real-zone"));

        assertThat(viewerZone.resolve(request)).isEqualTo(ViewerZone.LEAGUE_ZONE);
    }
}
