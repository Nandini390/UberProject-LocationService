package org.example.uberprojectlocationservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.uberprojectlocationservice.events.DriverTrackingEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaTrackingProducerService {

    private static final String DRIVER_TRACKING_TOPIC = "driver.tracking";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishDriverTrackingEvent(DriverTrackingEvent event) {
        try {
            kafkaTemplate.send(DRIVER_TRACKING_TOPIC, event.getBookingId(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to publish driver tracking event", exception);
        }
    }
}
