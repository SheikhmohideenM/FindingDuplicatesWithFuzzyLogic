package com.phonetic.projects.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoctorData {

    private UUID id;

    private String firstName;

    private String doctorId;

    private String lastName;

    private String contactNumber;

    private LocalDate dateOfBirth;

    private String ssnNumber;

}
