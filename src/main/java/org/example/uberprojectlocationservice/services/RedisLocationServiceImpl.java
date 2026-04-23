package org.example.uberprojectlocationservice.services;

import org.example.uberprojectlocationservice.dto.DriverLocationDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedisLocationServiceImpl implements LocationService{

    private static final String DRIVER_GEO_OPS_KEY = "drivers";
    private static final String DRIVER_ONLINE_KEY_PREFIX = "drivers:online:";
    private static final String DRIVER_LAST_SEEN_KEY_PREFIX = "drivers:last-seen:";

    private final Double searchRadius;
    private final long freshnessWindowSeconds;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLocationServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.location.search-radius-km:5.0}") Double searchRadius,
            @Value("${app.location.driver-freshness-seconds:120}") long freshnessWindowSeconds
    ){
        this.stringRedisTemplate=stringRedisTemplate;
        this.searchRadius = searchRadius;
        this.freshnessWindowSeconds = freshnessWindowSeconds;
    }

    @Override
    public Boolean saveDriverLocation(String driverId, Double latitude, Double longitude) {
        GeoOperations<String,String> geoOps =stringRedisTemplate.opsForGeo();
        geoOps.add(
                DRIVER_GEO_OPS_KEY,
                new RedisGeoCommands.GeoLocation<>(
                        driverId,
                        new Point(latitude, longitude)));
        markDriverOnline(driverId);
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
                    .latitude(point.getX())
                    .longitude(point.getY())
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
        stringRedisTemplate.opsForGeo().remove(DRIVER_GEO_OPS_KEY, driverId);
        return true;
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
}


