package org.example.uberprojectlocationservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverTrackingDto {
    private String driverId;
    private Double latitude;
    private Double longitude;
    private Boolean online;
    private String bookingId;
    private String trackingStage;
    private Long lastUpdatedAtEpochMs;
}
