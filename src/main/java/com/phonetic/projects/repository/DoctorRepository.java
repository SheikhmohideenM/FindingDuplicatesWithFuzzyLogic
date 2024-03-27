package com.phonetic.projects.repository;

import com.phonetic.projects.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    List<Doctor> findByFirstnameIgnoreCase(String firstname);

    List<Doctor> findByContactNumber(String contactNumber);
}
