package com.wayline.notification.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wayline.notification.domain.NotificationDelivery;
import com.wayline.notification.infrastructure.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {
    @Mock private ObjectMapper objectMapper;
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private NotificationDeliveryRepository deliveryRepository;

    @Test
    void durablyQueuesUniquePaymentEvent() throws Exception {
        NotificationService service = new NotificationService(objectMapper, restClientBuilder, deliveryRepository);
        ReflectionTestUtils.setField(service, "webhookUrl", "https://merchant.example/webhook");
        JsonNode event = mock(JsonNode.class);
        JsonNode eventIdNode = mock(JsonNode.class);
        when(objectMapper.readTree("{\"eventId\":\"evt-1\"}")).thenReturn(event);
        when(event.get("eventId")).thenReturn(eventIdNode);
        when(eventIdNode.isNull()).thenReturn(false);
        when(eventIdNode.asText()).thenReturn("evt-1");
        when(deliveryRepository.findByEventId("evt-1")).thenReturn(Optional.empty());

        service.handlePaymentEvent("PaymentSucceeded", "{\"eventId\":\"evt-1\"}");

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("evt-1", captor.getValue().getEventId());
        verify(restClientBuilder, never()).build();
    }

    @Test
    void duplicatePaymentEventDoesNotCreateAnotherDelivery() throws Exception {
        NotificationService service = new NotificationService(objectMapper, restClientBuilder, deliveryRepository);
        JsonNode event = mock(JsonNode.class);
        JsonNode eventIdNode = mock(JsonNode.class);
        when(objectMapper.readTree("{\"eventId\":\"evt-2\"}")).thenReturn(event);
        when(event.get("eventId")).thenReturn(eventIdNode);
        when(eventIdNode.isNull()).thenReturn(false);
        when(eventIdNode.asText()).thenReturn("evt-2");
        when(deliveryRepository.findByEventId("evt-2"))
            .thenReturn(Optional.of(NotificationDelivery.builder().eventId("evt-2").build()));

        service.handlePaymentEvent("PaymentSucceeded", "{\"eventId\":\"evt-2\"}");

        verify(deliveryRepository, never()).save(any(NotificationDelivery.class));
    }
}
