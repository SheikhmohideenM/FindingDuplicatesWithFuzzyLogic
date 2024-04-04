package com.phonetic.projects.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "doctor")
@Data
public class Doctor implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String doctorId;

    private String firstname;

    private String lastname;

    private String contactNumber;

    private LocalDate dateOfBirth;

    private String ssnNumber;


}
