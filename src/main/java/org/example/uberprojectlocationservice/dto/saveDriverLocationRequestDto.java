package org.example.uberprojectlocationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class saveDriverLocationRequestDto {
    String driverId;
    Double latitude;
    Double longitude;
}
