package org.example.uberprojectlocationservice.controllers;

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
    public ResponseEntity<Boolean> saveDriverLocation(@RequestBody saveDriverLocationRequestDto saveDriverLocationRequestDto){
         try{
             boolean response= locationService.saveDriverLocation(saveDriverLocationRequestDto.getDriverId(), saveDriverLocationRequestDto.getLatitude(), saveDriverLocationRequestDto.getLongitude());
             return new ResponseEntity<>(response, HttpStatus.CREATED);
         } catch (Exception e) {
             return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
         }
    }



    @PostMapping("/nearby/drivers")
    public ResponseEntity<List<DriverLocationDto>> getNearbyDrivers(@RequestBody NearbyDriversRequestDto nearbyDriversRequestDto){
        try{
            List<DriverLocationDto> drivers=locationService.getNearbyDrivers(nearbyDriversRequestDto.getLatitude(), nearbyDriversRequestDto.getLongitude());
            return new ResponseEntity<>(drivers,HttpStatus.OK);
        }catch(Exception e){
            return new ResponseEntity<>(new ArrayList<>(),HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
