package org.example.uberprojectlocationservice.events;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DriverTrackingEvent {
    private final String bookingId;
    private final String driverId;
    private final Double latitude;
    private final Double longitude;
    private final String trackingStage;
    private final Long actualDistanceMeters;
    private final LocalDateTime occurredAt;
}
