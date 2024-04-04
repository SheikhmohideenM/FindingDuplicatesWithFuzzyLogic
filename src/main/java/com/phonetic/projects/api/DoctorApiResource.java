package com.phonetic.projects.api;

import com.phonetic.projects.data.DoctorData;
import com.phonetic.projects.service.DoctorReadPlatformService;
import com.phonetic.projects.service.DoctorWritePlatformService;
import lombok.AllArgsConstructor;
import org.apache.commons.codec.EncoderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
public class DoctorApiResource {

    private final DoctorReadPlatformService readPlatformService;

    private final DoctorWritePlatformService writePlatformService;


    @PostMapping("/add-doctor")
    public ResponseEntity<?> createDoctor(@RequestBody DoctorData doctorData) {
        try {
            return writePlatformService.createDoctor(doctorData);
        } catch (EncoderException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error occurred while processing the request.");
        }
    }
}
