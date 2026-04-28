package org.example.uberprojectlocationservice.services;

import org.example.uberprojectlocationservice.dto.BookingTripMetricsDto;
import org.example.uberprojectlocationservice.dto.DriverLocationDto;
import org.example.uberprojectlocationservice.dto.DriverTrackingDto;
import org.example.uberprojectlocationservice.events.DriverTrackingEvent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class RedisLocationServiceImpl implements LocationService{

    private static final String DRIVER_GEO_OPS_KEY = "drivers";
    private static final String DRIVER_ONLINE_KEY_PREFIX = "drivers:online:";
    private static final String DRIVER_LAST_SEEN_KEY_PREFIX = "drivers:last-seen:";
    private static final String DRIVER_BOOKING_KEY_PREFIX = "drivers:booking:";
    private static final String DRIVER_STAGE_KEY_PREFIX = "drivers:stage:";
    private static final String BOOKING_DRIVER_KEY_PREFIX = "booking:driver:";
    private static final String BOOKING_STAGE_KEY_PREFIX = "booking:stage:";
    private static final String BOOKING_DISTANCE_KEY_PREFIX = "booking:distance:";
    private static final String BOOKING_LAST_POINT_KEY_PREFIX = "booking:last-point:";
    private static final String BOOKING_LAST_UPDATED_KEY_PREFIX = "booking:last-updated:";

    private final Double searchRadius;
    private final long freshnessWindowSeconds;

    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTrackingProducerService kafkaTrackingProducerService;

    public RedisLocationServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.location.search-radius-km:5.0}") Double searchRadius,
            @Value("${app.location.driver-freshness-seconds:120}") long freshnessWindowSeconds,
            KafkaTrackingProducerService kafkaTrackingProducerService
    ){
        this.stringRedisTemplate=stringRedisTemplate;
        this.searchRadius = searchRadius;
        this.freshnessWindowSeconds = freshnessWindowSeconds;
        this.kafkaTrackingProducerService = kafkaTrackingProducerService;
    }

    @Override
    public Boolean saveDriverLocation(String driverId, Double latitude, Double longitude, String bookingId, String trackingStage) {
        GeoOperations<String,String> geoOps =stringRedisTemplate.opsForGeo();
        geoOps.add(
                DRIVER_GEO_OPS_KEY,
                new RedisGeoCommands.GeoLocation<>(
                        driverId,
                        new Point(longitude, latitude)));
        markDriverOnline(driverId);
        updateDriverJourneyContext(driverId, latitude, longitude, bookingId, trackingStage);
        publishTrackingEvent(driverId, latitude, longitude, bookingId, trackingStage);
        return true;
    }

    @Override
    public List<DriverLocationDto> getNearbyDrivers(Double latitude, Double longitude) {
        GeoOperations<String,String> geoOps =stringRedisTemplate.opsForGeo();
        Distance distance = new Distance(searchRadius, Metrics.KILOMETERS);
        Circle within = new Circle(new Point(latitude,longitude),distance);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = geoOps.radius(DRIVER_GEO_OPS_KEY,within);

        List<DriverLocationDto> drivers=new ArrayList<>();
        if (results == null) {
            return drivers;
        }
        for(GeoResult<RedisGeoCommands.GeoLocation<String>> result:results){
            String driverId = result.getContent().getName();
            if (!isDriverOnline(driverId) || isDriverStale(driverId)) {
                continue;
            }
            List<Point> positions = geoOps.position(DRIVER_GEO_OPS_KEY, result.getContent().getName());
            if(positions == null || positions.isEmpty()){
                continue;
            }
            Point point = positions.get(0);
            DriverLocationDto driverLocation = DriverLocationDto.builder()
                    .driverId(driverId)
                    .latitude(point.getY())
                    .longitude(point.getX())
                    .build();
            drivers.add(driverLocation);
        }
        drivers.sort(Comparator.comparingDouble(driver ->
                Math.pow(driver.getLatitude() - latitude, 2) + Math.pow(driver.getLongitude() - longitude, 2)
        ));
        return drivers;
    }

    @Override
    public Boolean markDriverOnline(String driverId) {
        stringRedisTemplate.opsForValue().set(DRIVER_ONLINE_KEY_PREFIX + driverId, "true", freshnessWindowSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(DRIVER_LAST_SEEN_KEY_PREFIX + driverId, String.valueOf(System.currentTimeMillis()), freshnessWindowSeconds, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public Boolean markDriverOffline(String driverId) {
        stringRedisTemplate.delete(DRIVER_ONLINE_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(DRIVER_LAST_SEEN_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(DRIVER_BOOKING_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(DRIVER_STAGE_KEY_PREFIX + driverId);
        stringRedisTemplate.opsForGeo().remove(DRIVER_GEO_OPS_KEY, driverId);
        return true;
    }

    @Override
    public DriverTrackingDto getDriverLocation(String driverId) {
        GeoOperations<String,String> geoOps =stringRedisTemplate.opsForGeo();
        List<Point> positions = geoOps.position(DRIVER_GEO_OPS_KEY, driverId);
        if (positions == null || positions.isEmpty() || positions.get(0) == null) {
            throw new IllegalArgumentException("Driver location not found for " + driverId);
        }
        Point point = positions.get(0);
        return DriverTrackingDto.builder()
                .driverId(driverId)
                .latitude(point.getY())
                .longitude(point.getX())
                .online(isDriverOnline(driverId) && !isDriverStale(driverId))
                .bookingId(stringRedisTemplate.opsForValue().get(DRIVER_BOOKING_KEY_PREFIX + driverId))
                .trackingStage(stringRedisTemplate.opsForValue().get(DRIVER_STAGE_KEY_PREFIX + driverId))
                .lastUpdatedAtEpochMs(parseLongOrNull(stringRedisTemplate.opsForValue().get(DRIVER_LAST_SEEN_KEY_PREFIX + driverId)))
                .build();
    }

    @Override
    public BookingTripMetricsDto getBookingTripMetrics(String bookingId) {
        String driverId = stringRedisTemplate.opsForValue().get(BOOKING_DRIVER_KEY_PREFIX + bookingId);
        String trackingStage = stringRedisTemplate.opsForValue().get(BOOKING_STAGE_KEY_PREFIX + bookingId);
        Long lastUpdated = parseLongOrNull(stringRedisTemplate.opsForValue().get(BOOKING_LAST_UPDATED_KEY_PREFIX + bookingId));
        Long actualDistance = Math.round(parseDoubleOrZero(stringRedisTemplate.opsForValue().get(BOOKING_DISTANCE_KEY_PREFIX + bookingId)));

        Double currentLatitude = null;
        Double currentLongitude = null;
        if (driverId != null && !driverId.isBlank()) {
            DriverTrackingDto driverTracking = getDriverLocation(driverId);
            currentLatitude = driverTracking.getLatitude();
            currentLongitude = driverTracking.getLongitude();
        }

        return BookingTripMetricsDto.builder()
                .bookingId(bookingId)
                .driverId(driverId)
                .trackingStage(trackingStage)
                .actualDistanceMeters(actualDistance)
                .currentLatitude(currentLatitude)
                .currentLongitude(currentLongitude)
                .lastUpdatedAtEpochMs(lastUpdated)
                .build();
    }

    private boolean isDriverOnline(String driverId) {
        return Boolean.parseBoolean(stringRedisTemplate.opsForValue().get(DRIVER_ONLINE_KEY_PREFIX + driverId));
    }

    private boolean isDriverStale(String driverId) {
        String lastSeen = stringRedisTemplate.opsForValue().get(DRIVER_LAST_SEEN_KEY_PREFIX + driverId);
        if (lastSeen == null) {
            return true;
        }
        long ageMillis = System.currentTimeMillis() - Long.parseLong(lastSeen);
        return ageMillis > freshnessWindowSeconds * 1000;
    }

    private void updateDriverJourneyContext(String driverId, Double latitude, Double longitude, String bookingId, String trackingStage) {
        if (bookingId == null || bookingId.isBlank()) {
            return;
        }

        String normalizedStage = trackingStage == null || trackingStage.isBlank() ? "IDLE" : trackingStage;
        stringRedisTemplate.opsForValue().set(DRIVER_BOOKING_KEY_PREFIX + driverId, bookingId, freshnessWindowSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(DRIVER_STAGE_KEY_PREFIX + driverId, normalizedStage, freshnessWindowSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(BOOKING_DRIVER_KEY_PREFIX + bookingId, driverId, freshnessWindowSeconds, TimeUnit.DAYS);
        stringRedisTemplate.opsForValue().set(BOOKING_STAGE_KEY_PREFIX + bookingId, normalizedStage, freshnessWindowSeconds, TimeUnit.DAYS);
        stringRedisTemplate.opsForValue().set(BOOKING_LAST_UPDATED_KEY_PREFIX + bookingId, String.valueOf(System.currentTimeMillis()), freshnessWindowSeconds, TimeUnit.DAYS);

        if (!"ON_TRIP".equalsIgnoreCase(normalizedStage)) {
            return;
        }

        String lastPointValue = stringRedisTemplate.opsForValue().get(BOOKING_LAST_POINT_KEY_PREFIX + bookingId);
        if (lastPointValue != null && !lastPointValue.isBlank()) {
            String[] parts = lastPointValue.split(",");
            if (parts.length == 2) {
                double previousLatitude = Double.parseDouble(parts[0]);
                double previousLongitude = Double.parseDouble(parts[1]);
                double incrementalDistance = calculateDistanceMeters(previousLatitude, previousLongitude, latitude, longitude);
                double currentDistance = parseDoubleOrZero(stringRedisTemplate.opsForValue().get(BOOKING_DISTANCE_KEY_PREFIX + bookingId));
                stringRedisTemplate.opsForValue().set(
                        BOOKING_DISTANCE_KEY_PREFIX + bookingId,
                        String.valueOf(currentDistance + incrementalDistance),
                        freshnessWindowSeconds,
                        TimeUnit.DAYS
                );
            }
        } else {
            stringRedisTemplate.opsForValue().set(
                    BOOKING_DISTANCE_KEY_PREFIX + bookingId,
                    String.valueOf(0.0),
                    freshnessWindowSeconds,
                    TimeUnit.DAYS
            );
        }

        stringRedisTemplate.opsForValue().set(
                BOOKING_LAST_POINT_KEY_PREFIX + bookingId,
                latitude + "," + longitude,
                freshnessWindowSeconds,
                TimeUnit.DAYS
        );
    }

    private double calculateDistanceMeters(double startLatitude, double startLongitude, double endLatitude, double endLongitude) {
        double earthRadiusMeters = 6371000.0;
        double latDistance = Math.toRadians(endLatitude - startLatitude);
        double lonDistance = Math.toRadians(endLongitude - startLongitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(startLatitude)) * Math.cos(Math.toRadians(endLatitude))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    private long parseLongOrNull(String value) {
        return value == null || value.isBlank() ? 0L : Long.parseLong(value);
    }

    private double parseDoubleOrZero(String value) {
        return value == null || value.isBlank() ? 0.0 : Double.parseDouble(value);
    }

    private void publishTrackingEvent(String driverId, Double latitude, Double longitude, String bookingId, String trackingStage) {
        if (bookingId == null || bookingId.isBlank()) {
            return;
        }
        Long actualDistanceMeters = Math.round(parseDoubleOrZero(stringRedisTemplate.opsForValue().get(BOOKING_DISTANCE_KEY_PREFIX + bookingId)));
        kafkaTrackingProducerService.publishDriverTrackingEvent(DriverTrackingEvent.builder()
                .bookingId(bookingId)
                .driverId(driverId)
                .latitude(latitude)
                .longitude(longitude)
                .trackingStage(trackingStage == null || trackingStage.isBlank() ? "IDLE" : trackingStage)
                .actualDistanceMeters(actualDistanceMeters)
                .occurredAt(LocalDateTime.now())
                .build());
    }
}

