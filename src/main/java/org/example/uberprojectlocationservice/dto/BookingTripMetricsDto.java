package org.example.uberprojectlocationservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingTripMetricsDto {
    private String bookingId;
    private String driverId;
    private String trackingStage;
    private Long actualDistanceMeters;
    private Double currentLatitude;
    private Double currentLongitude;
    private Long lastUpdatedAtEpochMs;
}
