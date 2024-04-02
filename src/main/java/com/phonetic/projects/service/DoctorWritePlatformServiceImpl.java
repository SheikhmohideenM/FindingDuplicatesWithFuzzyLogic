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

import java.util.*;

@Service
@RequiredArgsConstructor
public class DoctorWritePlatformServiceImpl implements DoctorWritePlatformService {

    private final DoctorRepository doctorRepository;
    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    private final Soundex soundex = new Soundex();
    private int sequentialDoctorNumber = 1;

    private static final double THRESHOLD = 80.0;

    @Override
    public ResponseEntity<?> createDoctor(DoctorData doctorData) throws EncoderException {
        Doctor doctor = new Doctor();
        doctor.setFirstname(doctorData.getFirstname());
        doctor.setLastname(doctorData.getLastname());
        doctor.setContactNumber(doctorData.getContactNumber());
        doctor.setDateOfBirth(doctorData.getDateOfBirth());
        doctor.setSsnNumber(doctorData.getSsnNumber());
        // Generate and set doctorId with static number "100", sequential number, and 2 alphabet length
        doctor.setDoctorId(IDGenerator.generateDoctorId(100, sequentialDoctorNumber++, 2));

        Doctor createdDoctor = doctorRepository.save(doctor);

        // Check for duplicate first names or contact numbers before creating a new doctor
        List<Doctor> potentialDuplicates = findMatchingDoctors(doctorData);

        List<String> message = new ArrayList<>();

        if (!potentialDuplicates.isEmpty()) {
            message.add("A doctor with the same name already exists.");
        }

        // If duplicates found, construct response with error messages and duplicate details
        if (!potentialDuplicates.isEmpty()) {
            List<Map<String, Object>> duplicates = new ArrayList<>();

            // Iterate over the combined list of duplicates
            for (Doctor duplicateDoctor : potentialDuplicates) {

                // Calculate similarity percentages for each attribute
                Map<String, Double> individualSimilarities = calculateSimilarityPercentage(doctorData, duplicateDoctor);
                double firstNameSimilarityPercentage = individualSimilarities.get("firstName") * 0.5;
                double contactNumberSimilarityPercentage = individualSimilarities.get("contactNumber") * 0.2;
                double ssnNumberSimilarityPercentage = individualSimilarities.get("ssnNumber") * 1.0;

                // Calculate overall similarity percentage
                double overallSimilarityPercentage = (firstNameSimilarityPercentage + contactNumberSimilarityPercentage + ssnNumberSimilarityPercentage) / 1.7;

                if (overallSimilarityPercentage >= THRESHOLD) {
                    Map<String, Object> duplicateData = new HashMap<>();
                    duplicateData.put("id", duplicateDoctor.getId());
                    duplicateData.put("contact_number", duplicateDoctor.getContactNumber());
                    duplicateData.put("date_of_birth", duplicateDoctor.getDateOfBirth());
                    duplicateData.put("doctor_id", duplicateDoctor.getDoctorId());
                    duplicateData.put("firstname", duplicateDoctor.getFirstname());
                    duplicateData.put("lastname", duplicateDoctor.getLastname());
                    duplicateData.put("ssnNumber", duplicateDoctor.getSsnNumber());
                    duplicateData.put("similarity_percentage", overallSimilarityPercentage);
                    duplicateData.put("similarity_message", String.format("First Name Similarity: %.2f%%, Contact Number Similarity: %.2f%%, SSN Number Similarity: %.2f%%",
                            firstNameSimilarityPercentage, contactNumberSimilarityPercentage, ssnNumberSimilarityPercentage));
                    duplicates.add(duplicateData);
                }
            }

            Map<String, Object> response = new HashMap<>();
            if (duplicates.isEmpty()) {
                response.put("message", "Doctor data inserted successfully.");
            } else {
                response.put("isDuplicate", message);
                response.put("totalDuplicates", duplicates.size());
                response.put("duplicates", duplicates);
            }
            return ResponseEntity.ok().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdDoctor);
    }

    private Map<String, Double> calculateSimilarityPercentage(DoctorData doctorData, Doctor doctor) {
        Map<String, Double> similarityMap = new HashMap<>();

        double firstNameSimilarityPercentage = calculateIndividualSimilarityPercentage(doctorData.getFirstname(), doctor.getFirstname());
        double contactNumberSimilarityPercentage = calculateIndividualSimilarityPercentage(doctorData.getContactNumber(), doctor.getContactNumber());
        double ssnNumberSimilarityPercentage = calculateIndividualSimilarityPercentage(doctorData.getSsnNumber(), doctor.getSsnNumber());

        similarityMap.put("firstName", firstNameSimilarityPercentage);
        similarityMap.put("contactNumber", contactNumberSimilarityPercentage);
        similarityMap.put("ssnNumber", ssnNumberSimilarityPercentage);

        return similarityMap;
    }

    private double calculateIndividualSimilarityPercentage(String str1, String str2) {
        int maxLength = Math.max(str1.length(), str2.length());
        int levenshteinsDistance = levenshteinDistance.apply(str1.toLowerCase(), str2.toLowerCase());
        return ((double) (maxLength - levenshteinsDistance) / maxLength) * 100;
    }

    private List<Doctor> findMatchingDoctors(DoctorData doctorData) throws EncoderException {
        List<Doctor> matchingDoctors = new ArrayList<>();
        List<Doctor> allDoctors = doctorRepository.findAll();

        for (Doctor doctor : allDoctors) {
            if (isFuzzyMatch(doctorData, doctor) && isPhoneticMatch(doctorData, doctor)) {
                matchingDoctors.add(doctor);
            }
        }
        return matchingDoctors;
    }

    private boolean isFuzzyMatch(DoctorData doctorData, Doctor doctor) throws EncoderException {
        // Calculate Levenshtein distances for first name, last name, contact number, and SSN number
        int firstNameDistance = levenshteinDistance.apply(doctorData.getFirstname().toLowerCase(), doctor.getFirstname().toLowerCase());
        int lastNameDistance = levenshteinDistance.apply(doctorData.getLastname().toLowerCase(), doctor.getLastname().toLowerCase());
        int contactNumberDistance = levenshteinDistance.apply(doctorData.getContactNumber().toLowerCase(), doctor.getContactNumber().toLowerCase());
        int ssnNumberDistance = levenshteinDistance.apply(doctorData.getSsnNumber().toLowerCase(), doctor.getSsnNumber().toLowerCase());

        // Check if any of the distances exceed the threshold for similarity
        boolean levenshteinSimilarity = firstNameDistance <= 2
                && lastNameDistance <= 2
                && contactNumberDistance <= 2
                && ssnNumberDistance <= 2;

        // Return true if Levenshtein distance similarity is detected
        return levenshteinSimilarity;
    }

    private boolean isPhoneticMatch(DoctorData doctorData, Doctor doctor) throws EncoderException {
        // Get Soundex codes for first name, last name, contact number, and SSN number
        String firstNameSoundex = soundex.encode(doctorData.getFirstname());
        String lastNameSoundex = soundex.encode(doctorData.getLastname());
        String contactNumberSoundex = soundex.encode(doctorData.getContactNumber());
        String ssNumberSoundex = soundex.encode(doctorData.getSsnNumber());

        // Check if Soundex codes indicate similarity
        boolean soundexSimilarity =
                firstNameSoundex.equals(soundex.encode(doctor.getFirstname())) ||
                        lastNameSoundex.equals(soundex.encode(doctor.getLastname())) ||
                        contactNumberSoundex.equals(soundex.encode(doctor.getContactNumber())) ||
                        ssNumberSoundex.equals(soundex.encode(doctor.getSsnNumber()));

        // Return true if Soundex similarity is detected
        return soundexSimilarity;
    }
}

