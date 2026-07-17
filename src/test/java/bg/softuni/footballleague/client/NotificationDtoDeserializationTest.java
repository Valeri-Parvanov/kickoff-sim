package bg.softuni.footballleague.client;

import bg.softuni.footballleague.config.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDtoDeserializationTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Test
    void decodesNotificationWithFieldsTheClientDoesNotModel() throws Exception {
        String json = """
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "userId": "22222222-2222-2222-2222-222222222222",
                  "matchId": "33333333-3333-3333-3333-333333333333",
                  "message": "LIVE: Home 1:0 Away · 32'",
                  "type": "MATCH_UPDATE",
                  "read": false,
                  "createdAt": "2026-07-14T12:00:00"
                }
                """;

        NotificationDto dto = objectMapper.readValue(json, NotificationDto.class);

        assertThat(dto.getMessage()).isEqualTo("LIVE: Home 1:0 Away · 32'");
        assertThat(dto.getType()).isEqualTo("MATCH_UPDATE");
        assertThat(dto.isRead()).isFalse();
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @Test
    void decodesSubscriptionListFromService() throws Exception {
        String json = """
                [
                  {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "userId": "22222222-2222-2222-2222-222222222222",
                    "entityType": "MATCH",
                    "entityId": "33333333-3333-3333-3333-333333333333",
                    "createdAt": "2026-07-14T12:00:00"
                  }
                ]
                """;

        SubscriptionDto[] subs = objectMapper.readValue(json, SubscriptionDto[].class);

        assertThat(subs).hasSize(1);
        assertThat(subs[0].getEntityType()).isEqualTo("MATCH");
    }
}
