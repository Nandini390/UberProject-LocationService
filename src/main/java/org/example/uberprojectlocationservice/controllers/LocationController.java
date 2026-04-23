package org.example.uberprojectlocationservice.controllers;

import jakarta.validation.Valid;
import org.example.uberprojectlocationservice.dto.DriverLocationDto;
import org.example.uberprojectlocationservice.dto.NearbyDriversRequestDto;
import org.example.uberprojectlocationservice.dto.saveDriverLocationRequestDto;
import org.example.uberprojectlocationservice.services.LocationService;
import org.springframework.data.geo.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private LocationService locationService;

    public LocationController(LocationService locationService){
        this.locationService=locationService;
    }

    @PostMapping("/drivers")
    public ResponseEntity<Boolean> saveDriverLocation(@Valid @RequestBody saveDriverLocationRequestDto saveDriverLocationRequestDto){
         boolean response= locationService.saveDriverLocation(saveDriverLocationRequestDto.getDriverId(), saveDriverLocationRequestDto.getLatitude(), saveDriverLocationRequestDto.getLongitude());
         return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/drivers/{driverId}/online")
    public ResponseEntity<Boolean> markDriverOnline(@PathVariable String driverId) {
        return new ResponseEntity<>(locationService.markDriverOnline(driverId), HttpStatus.OK);
    }

    @PostMapping("/drivers/{driverId}/offline")
    public ResponseEntity<Boolean> markDriverOffline(@PathVariable String driverId) {
        return new ResponseEntity<>(locationService.markDriverOffline(driverId), HttpStatus.OK);
    }



    @PostMapping("/nearby/drivers")
    public ResponseEntity<List<DriverLocationDto>> getNearbyDrivers(@Valid @RequestBody NearbyDriversRequestDto nearbyDriversRequestDto){
        List<DriverLocationDto> drivers=locationService.getNearbyDrivers(nearbyDriversRequestDto.getLatitude(), nearbyDriversRequestDto.getLongitude());
        return new ResponseEntity<>(drivers,HttpStatus.OK);
    }
}
