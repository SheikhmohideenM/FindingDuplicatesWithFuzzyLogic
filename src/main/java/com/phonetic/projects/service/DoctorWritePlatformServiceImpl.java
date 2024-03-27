package com.phonetic.projects.service;

import com.phonetic.projects.data.DoctorData;
import com.phonetic.projects.entity.Doctor;
import com.phonetic.projects.exceptions.DuplicateDataException;
import com.phonetic.projects.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.print.Doc;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DoctorWritePlatformServiceImpl implements DoctorWritePlatformService{

    private final DoctorRepository doctorRepository;
    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    private final Soundex soundex = new Soundex();
    private int sequentialDoctorNumber = 1;

    @Override
    public ResponseEntity<?> createDoctor(DoctorData doctorData) throws EncoderException {

        Doctor doctor = new Doctor();
        doctor.setFirstname(doctorData.getFirstname());
        doctor.setLastname(doctorData.getLastname());
        doctor.setContactNumber(doctorData.getContactNumber());
        doctor.setDateOfBirth(doctorData.getDateOfBirth());
        // Generate and set doctorId with static number "100", sequential number, and 2 alphabet length
        doctor.setDoctorId(IDGenerator.generateDoctorId(100, sequentialDoctorNumber++, 2));

        //List<Doctor> matchingDoctors = findMatchingDoctors(doctorData);

        //if (!matchingDoctors.isEmpty()) {
        //    List<UUID> mergedDoctorIds = mergeDoctors(matchingDoctors, doctorData);
        //    Map<String, Object> response = new HashMap<>();
        //    response.put("mergedDoctorIds", mergedDoctorIds);
        //    return ResponseEntity.ok().body("Doctor data merged successfully." + response);
            //return doctorRepository.findById(mergedDoctorIds.get(0)).orElse(null);
        //}
        Doctor createdDoctor = doctorRepository.save(doctor);

        // Check for duplicate first names or contact numbers before creating a new doctor
        //List<Doctor> duplicateFirstNames = doctorRepository.findByFirstnameIgnoreCase(doctorData.getFirstname());
        //List<Doctor> duplicateContactNumbers = doctorRepository.findByContactNumber(doctorData.getContactNumber());

        // Check for duplicate first names or contact numbers before creating a new doctor
        List<Doctor> duplicateDatas = findMatchingDoctors(doctorData);

        List<String> message = new ArrayList<>();

        if (!duplicateDatas.isEmpty()) {
            message.add("A doctor with the same name already exists.");
        }

        // If duplicates found, construct response with error messages and duplicate details
        if (!message.isEmpty()) {
            List<Map<String, Object>> duplicates = new ArrayList<>();

            // Combine both lists of duplicates
            List<Doctor> combinedDuplicates = new ArrayList<>();
            combinedDuplicates.addAll(duplicateDatas);

            // Iterate over the combined list of duplicates
            for (Doctor duplicateDoctor : combinedDuplicates) {
                Map<String, Object> duplicateData = new HashMap<>();
                duplicateData.put("id", doctor.getId());
                duplicateData.put("contact_number", duplicateDoctor.getContactNumber());
                duplicateData.put("date_of_birth", duplicateDoctor.getDateOfBirth());
                duplicateData.put("doctor_id", duplicateDoctor.getDoctorId());
                duplicateData.put("firstname", duplicateDoctor.getFirstname());
                duplicateData.put("lastname", duplicateDoctor.getLastname());
                duplicates.add(duplicateData);

                // Calculate similarity percentages
                double firstNameSimilarityPercentage = calculateSimilarityPercentage(doctorData.getFirstname(), duplicateDoctor.getFirstname());
                double lastNameSimilarityPercentage = calculateSimilarityPercentage(doctorData.getLastname(), duplicateDoctor.getLastname());
                double contactNumberSimilarityPercentage = calculateSimilarityPercentage(doctorData.getContactNumber(), duplicateDoctor.getContactNumber());

                // Add similarity message to duplicate data
                duplicateData.put("similarity_message", String.format("First Name Similarity: %.2f%%, Last Name Similarity: %.2f%%, Contact Number Similarity: %.2f%%",
                        firstNameSimilarityPercentage, lastNameSimilarityPercentage, contactNumberSimilarityPercentage));

                duplicates.add(duplicateData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("isDuplicate ?", message);
            response.put("totalDuplicates", duplicates.size());
            response.put("duplicates", duplicates);

            return ResponseEntity.ok().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdDoctor);
    }

    private List<UUID> mergeDoctors(List<Doctor> matchingDoctors, DoctorData newDoctorData) {
        List<UUID> mergedDoctorIds = new ArrayList<>();
        UUID maxId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        for (Doctor doctor : matchingDoctors) {
            doctor.setLastname(newDoctorData.getLastname());
            doctor.setContactNumber(newDoctorData.getContactNumber());
            doctor.setDateOfBirth(newDoctorData.getDateOfBirth());
            doctor.setFirstname(newDoctorData.getFirstname());
            doctor.setDoctorId(IDGenerator.generateDoctorId(100, sequentialDoctorNumber++, 2));

            Doctor mergedDoctor = doctorRepository.save(doctor);
            mergedDoctorIds.add(mergedDoctor.getId());

            if (mergedDoctor.getId().compareTo(maxId)> 0 ) {
                maxId = mergedDoctor.getId();
            }
        }

        UUID nextId = UUID.fromString(maxId.toString());
        nextId = new UUID(nextId.getMostSignificantBits(), nextId.getLeastSignificantBits() + 1);

        mergedDoctorIds.add(nextId);
        return mergedDoctorIds;
    }

    private List<Doctor> findMatchingDoctors(DoctorData doctorData) throws EncoderException {

        List<Doctor> matchingDoctors = new ArrayList<>();
        List<Doctor> allDoctors = doctorRepository.findAll();

        for (Doctor doctor : allDoctors) {
            if (isFuzzyMatch(doctorData, doctor) || isPhoneticMatch(doctorData, doctor)) {
                matchingDoctors.add(doctor);
            }
        }
        return matchingDoctors;
    }


    private boolean isFuzzyMatch(DoctorData doctorData, Doctor doctor) throws EncoderException {
        // Calculate Levenshtein distances for first name, last name, and contact number
        int firstNameDistance = levenshteinDistance.apply(doctorData.getFirstname().toLowerCase(), doctor.getFirstname().toLowerCase());
        int lastNameDistance = levenshteinDistance.apply(doctorData.getLastname().toLowerCase(), doctor.getLastname().toLowerCase());
        int contactNumberDistance = levenshteinDistance.apply(doctorData.getContactNumber().toLowerCase(), doctor.getContactNumber().toLowerCase());

        // Calculate similarity percentages using Jaro-Winkler distance
        double firstNameSimilarityPercentage = calculateSimilarityPercentage(doctorData.getFirstname(), doctor.getFirstname());
        double lastNameSimilarityPercentage = calculateSimilarityPercentage(doctorData.getLastname(), doctor.getLastname());
        double contactNumberSimilarityPercentage = calculateSimilarityPercentage(doctorData.getContactNumber(), doctor.getContactNumber());

        // Print the similarity message
        System.out.printf("First Name: %s - Similarity: %.2f%%, Last Name: %s - Similarity: %.2f%%, Contact Number: %s - Similarity: %.2f%%\n",
                doctor.getFirstname(), firstNameSimilarityPercentage, doctor.getLastname(), lastNameSimilarityPercentage, doctor.getContactNumber(), contactNumberSimilarityPercentage);

        // Check if Levenshtein distances indicate similarity
        boolean levenshteinSimilarity = firstNameDistance <= 2 && lastNameDistance <= 2 && contactNumberDistance <= 2;

        // Return true if Levenshtein distance similarity is detected
        return levenshteinSimilarity;
    }

    private double calculateSimilarityPercentage(String str1, String str2) {
        int maxLength = Math.max(str1.length(), str2.length());
        int levenshteinsDistance = levenshteinDistance.apply(str1.toLowerCase(), str2.toLowerCase());
        double similarityPercentage = ((double) (maxLength - levenshteinsDistance) / maxLength) * 100;
        return similarityPercentage;
    }

    private boolean isPhoneticMatch(DoctorData doctorData, Doctor doctor) throws EncoderException {
        // Get Soundex codes for first name, last name, and contact number
        String firstNameSoundex = soundex.encode(doctorData.getFirstname());
        String lastNameSoundex = soundex.encode(doctorData.getLastname());
        String contactNumberSoundex = soundex.encode(doctorData.getContactNumber());

        // Check if Soundex codes indicate similarity
        boolean soundexSimilarity = firstNameSoundex.equals(soundex.encode(doctor.getFirstname())) &&
                lastNameSoundex.equals(soundex.encode(doctor.getLastname())) &&
                contactNumberSoundex.equals(soundex.encode(doctor.getContactNumber()));

        // Return true if Soundex similarity is detected
        return soundexSimilarity;
    }
}
