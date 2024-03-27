package com.phonetic.projects.service;

import com.phonetic.projects.data.DoctorData;
import com.phonetic.projects.entity.Doctor;
import org.apache.commons.codec.EncoderException;
import org.springframework.http.ResponseEntity;

public interface DoctorWritePlatformService {
    ResponseEntity<?> createDoctor(DoctorData doctorData) throws EncoderException;
}
