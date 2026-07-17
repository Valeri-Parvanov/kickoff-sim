package bg.softuni.footballleague.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewerZoneCoverageTest {

    private final ViewerZone viewerZone = new ViewerZone();

    @Test
    void resolve_nullRequest_returnsLeagueZone() {
        assertThat(viewerZone.resolve(null)).isEqualTo(ViewerZone.LEAGUE_ZONE);
    }

    @Test
    void resolve_noCookies_returnsLeagueZone() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        assertThat(viewerZone.resolve(request)).isEqualTo(ViewerZone.LEAGUE_ZONE);
    }

    @Test
    void resolve_validTzCookie_returnsThatZone() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("tz", "America/New_York")});

        assertThat(viewerZone.resolve(request)).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void resolve_invalidTzCookie_returnsLeagueZone() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("tz", "Not/AZone")});

        assertThat(viewerZone.resolve(request)).isEqualTo(ViewerZone.LEAGUE_ZONE);
    }

    @Test
    void resolve_otherCookieOnly_returnsLeagueZone() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("session", "abc")});

        assertThat(viewerZone.resolve(request)).isEqualTo(ViewerZone.LEAGUE_ZONE);
    }

    @Test
    void today_returnsDateInZone() {
        assertThat(viewerZone.today(ViewerZone.LEAGUE_ZONE))
                .isEqualTo(LocalDate.now(ViewerZone.LEAGUE_ZONE));
    }

    @Test
    void dateOf_convertsBetweenZones() {
        LocalDateTime playedAt = LocalDateTime.of(2026, 1, 1, 0, 30);

        LocalDate result = viewerZone.dateOf(playedAt, ZoneId.of("America/New_York"));

        assertThat(result).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void startOfDay_returnsLeagueZoneDateTime() {
        LocalDate date = LocalDate.of(2026, 5, 10);

        assertThat(viewerZone.startOfDay(date, ViewerZone.LEAGUE_ZONE))
                .isEqualTo(LocalDateTime.of(2026, 5, 10, 0, 0));
    }
}
