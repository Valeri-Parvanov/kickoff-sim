package bg.softuni.footballleague.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ViewerZone {

    public static final ZoneId LEAGUE_ZONE = ZoneId.of("Europe/Sofia");
    private static final String COOKIE_NAME = "tz";

    public ZoneId resolve(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return LEAGUE_ZONE;
        }
        for (Cookie cookie : request.getCookies()) {
            if (!COOKIE_NAME.equals(cookie.getName())) continue;
            try {
                return ZoneId.of(URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                return LEAGUE_ZONE;
            }
        }
        return LEAGUE_ZONE;
    }

    public LocalDate today(ZoneId viewerZone) {
        return LocalDate.now(viewerZone);
    }

    public LocalDate dateOf(LocalDateTime playedAt, ZoneId viewerZone) {
        return playedAt.atZone(LEAGUE_ZONE).withZoneSameInstant(viewerZone).toLocalDate();
    }

    public LocalDateTime startOfDay(LocalDate viewerDate, ZoneId viewerZone) {
        return viewerDate.atStartOfDay(viewerZone).withZoneSameInstant(LEAGUE_ZONE).toLocalDateTime();
    }
}
