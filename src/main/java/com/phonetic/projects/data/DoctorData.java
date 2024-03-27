package com.phonetic.projects.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoctorData {

    private UUID id;

    private String firstname;

    private String doctorId;

    private String lastname;

    private String contactNumber;

    private LocalDate dateOfBirth;

}
