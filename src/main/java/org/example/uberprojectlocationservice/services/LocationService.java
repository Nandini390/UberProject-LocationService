package org.example.uberprojectlocationservice.services;

import org.example.uberprojectlocationservice.dto.DriverLocationDto;
import org.example.uberprojectlocationservice.dto.DriverTrackingDto;
import org.example.uberprojectlocationservice.dto.BookingTripMetricsDto;

import java.util.List;

public interface LocationService {
    Boolean saveDriverLocation(String driverId, Double latitude, Double longitude, String bookingId, String trackingStage);

    List<DriverLocationDto> getNearbyDrivers(Double latitude, Double longitude);

    Boolean markDriverOnline(String driverId);

    Boolean markDriverOffline(String driverId);

    DriverTrackingDto getDriverLocation(String driverId);

    BookingTripMetricsDto getBookingTripMetrics(String bookingId);
}
