package org.example.uberprojectlocationservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class saveDriverLocationRequestDto {
    @NotBlank(message = "driverId is required")
    String driverId;
    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "latitude must be <= 90")
    Double latitude;
    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "longitude must be <= 180")
    Double longitude;
    String bookingId;
    @Pattern(regexp = "^(TO_PICKUP|ON_TRIP|IDLE)?$", message = "trackingStage must be TO_PICKUP, ON_TRIP, or IDLE")
    String trackingStage;
}
