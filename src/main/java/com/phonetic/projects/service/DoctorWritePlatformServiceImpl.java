package com.phonetic.projects.service;

import com.phonetic.projects.data.DoctorData;
import com.phonetic.projects.entity.Doctor;
import com.phonetic.projects.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DoctorWritePlatformServiceImpl implements DoctorWritePlatformService {

    private final DoctorRepository doctorRepository;

    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    private final Soundex soundex = new Soundex();

    private int sequentialDoctorNumber = 1;

    private final JdbcTemplate jdbcTemplate;

    private static final String FETCH_THRESHOLD_QUERY = "SELECT table_name, column_name, CAST(weightage AS FLOAT) AS weightage FROM similarity_config WHERE status = 'Y'";

    @Override
    public ResponseEntity<?> createDoctor(DoctorData doctorData) throws EncoderException {

        Doctor doctor = new Doctor();
        // Fetch threshold percentage, status, and attribute weights from the database
        List<Map<String, Object>> thresholdDataList = jdbcTemplate.queryForList(FETCH_THRESHOLD_QUERY);
        // Check for potential duplicates
        List<Doctor> potentialDuplicates = findMatchingDoctors(doctorData);

        // If no duplicates found, proceed to save the new doctor
        if (potentialDuplicates.isEmpty()) {
            doctor.setFirstName(doctorData.getFirstName());
            doctor.setLastName(doctorData.getLastName());
            doctor.setContactNumber(doctorData.getContactNumber());
            doctor.setDateOfBirth(doctorData.getDateOfBirth());
            doctor.setSsnNumber(doctorData.getSsnNumber());
            // Generate and set doctorId with static number "100", sequential number, and 2 alphabet length
            doctor.setDoctorId(IDGenerator.generateDoctorId(100, sequentialDoctorNumber++, 2));

            doctorRepository.save(doctor);

            // Return success response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("DoctorId",doctor.getDoctorId());
            response.put("First Name",doctor.getFirstName());
            response.put("Last Name",doctor.getLastName());
            response.put("Date Of Birth",doctor.getDateOfBirth());
            response.put("SSN Number",doctor.getSsnNumber());
            response.put("message", "Doctor data inserted successfully.");
            return ResponseEntity.ok().body(response);
        }
        else {
            List<Map<String, Object>> duplicates = new ArrayList<>();

            for (Doctor duplicateDoctor : potentialDuplicates) {
                Map<String, Double> individualSimilarities = calculateSimilarityPercentage(doctorData, duplicateDoctor);
                double overallSimilarityPercentage = 0.0;

                for (Map<String, Object> thresholdData : thresholdDataList) {
                    String tableName = (String) thresholdData.get("table_name");
                    if ("doctor".equals(tableName)) {
                        String columnName = (String) thresholdData.get("column_name");
                        Double weightage = (Double) thresholdData.get("weightage");
                        // Convert the columnName to match the keys in individualSimilarities
                        String key = convertColumnName(columnName);
                        // Check if the key exists in individualSimilarities
                        if (individualSimilarities.containsKey(key)) {
                            Double attributeSimilarityPercentage = individualSimilarities.get(key);
                            double weightedSimilarityPercentage = attributeSimilarityPercentage * weightage / 100.0;
                            overallSimilarityPercentage += weightedSimilarityPercentage;
                        }
                    } else {
                        String columnName = (String) thresholdData.get("column_name");
                        Double weightage = (Double) thresholdData.get("weightage");
                        String key = convertColumnName(columnName);
                        if (individualSimilarities.containsKey(key)) {
                            Double attributeSimilarityPercentage = individualSimilarities.get(key);
                            double weightedSimilarityPercentage = attributeSimilarityPercentage * weightage / 100.0;
                            overallSimilarityPercentage += weightedSimilarityPercentage;
                        }
                    }
                }
                double finalWeightageAverage = overallSimilarityPercentage / individualSimilarities.size();

                // If similarity percentage exceeds the threshold, consider it as a duplicate
                //if (overallSimilarityPercentage >= thresholdPercentage.doubleValue()) {
                    // Construct the response map
                    Map<String, Object> duplicateData = new LinkedHashMap<>(); // Use LinkedHashMap to maintain insertion order
                    duplicateData.put("id", duplicateDoctor.getId());
                    duplicateData.put("doctor_id", duplicateDoctor.getDoctorId());
                    duplicateData.put("similarity_percentage", String.format("%.2f%%", overallSimilarityPercentage));
                    duplicateData.put("finalWeightageAverage", finalWeightageAverage);
                    duplicateData.put("firstname", duplicateDoctor.getFirstName());
                    duplicateData.put("lastname", duplicateDoctor.getLastName());
                    duplicateData.put("ssnNumber", duplicateDoctor.getSsnNumber());
                    duplicateData.put("contact_number", duplicateDoctor.getContactNumber());
                    String individualSimilarityMessage = String.format("First Name Similarity: %.2f%%, Last Name Similarity: %.2f%%, Contact Number Similarity: %.2f%%, SSN Number Similarity: %.2f%%",
                            individualSimilarities.get("firstName"), individualSimilarities.get("lastName"), individualSimilarities.get("contactNumber"), individualSimilarities.get("ssnNumber"));
                    duplicateData.put("individual_similarity", individualSimilarityMessage);
                    duplicates.add(duplicateData);
                //}
            }

            doctor.setFirstName(doctorData.getFirstName());
            doctor.setLastName(doctorData.getLastName());
            doctor.setContactNumber(doctorData.getContactNumber());
            doctor.setDateOfBirth(doctorData.getDateOfBirth());
            doctor.setSsnNumber(doctorData.getSsnNumber());
            // Generate and set doctorId with static number "100", sequential number, and 2 alphabet length
            doctor.setDoctorId(IDGenerator.generateDoctorId(100, sequentialDoctorNumber++, 2));

            doctorRepository.save(doctor);

            // If duplicates found, return the response with duplicate details
            if (!duplicates.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("currentDoctorData", doctorData);
                response.put("totalDuplicates", duplicates.size());
                response.put("duplicates", duplicates);
                String warningMessage = "Multiple doctors found with the same SSN Number ID.";
                response.put("warningMessage", warningMessage);
                return ResponseEntity.ok().body(response);
            }

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Warning: Multiple doctors found with the same SSN Number ID.");
        }
    }

    private String convertColumnName(String columnName) {
        String[] parts = columnName.split("_");
        StringBuilder keyBuilder = new StringBuilder();
        for(int i= 0; i < parts.length; i++){
            String part = parts[i];
            if(i > 0){
                keyBuilder.append(Character.toUpperCase(part.charAt(0)));
                keyBuilder.append(part.substring(1));
            }else {
                keyBuilder.append(part.toLowerCase());
            }
        }
        return keyBuilder.toString();
    }


    private Map<String, Double> calculateSimilarityPercentage(DoctorData doctorData, Doctor doctor) {
        Map<String, Double> similarityMap = new HashMap<>();

        double firstNameSimilarityPercentage = calculateIndividualSimilarityPercentage(doctorData.getFirstName(), doctor.getFirstName());
        double lastNameSimilarityPercentage = calculateIndividualSimilarityPercentage(doctorData.getLastName(), doctor.getLastName());
        double contactNumberSimilarityPercentage = calculateIndividualSimilarityPercentage(doctorData.getContactNumber(), doctor.getContactNumber());
        double ssnNumberSimilarityPercentage = calculateIndividualSimilarityPercentage(doctorData.getSsnNumber(), doctor.getSsnNumber());

        similarityMap.put("firstName", firstNameSimilarityPercentage);
        similarityMap.put("lastName", lastNameSimilarityPercentage);
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
        int firstNameDistance = levenshteinDistance.apply(doctorData.getFirstName().toLowerCase(), doctor.getFirstName().toLowerCase());
        int lastNameDistance = levenshteinDistance.apply(doctorData.getLastName().toLowerCase(), doctor.getLastName().toLowerCase());
        int contactNumberDistance = levenshteinDistance.apply(doctorData.getContactNumber().toLowerCase(), doctor.getContactNumber().toLowerCase());
        int ssnNumberDistance = levenshteinDistance.apply(doctorData.getSsnNumber().toLowerCase(), doctor.getSsnNumber().toLowerCase());

        // Check if any of the distances exceed the threshold for similarity
        boolean levenshteinSimilarity = firstNameDistance <= 2 || lastNameDistance <= 2 || contactNumberDistance <= 2 || ssnNumberDistance <= 2;

        // Return true if Levenshtein distance similarity is detected
        return levenshteinSimilarity;
    }

    private boolean isPhoneticMatch(DoctorData doctorData, Doctor doctor) throws EncoderException {
        // Get Soundex codes for first name, last name, contact number, and SSN number
        String firstNameSoundex = soundex.encode(doctorData.getFirstName());
        String lastNameSoundex = soundex.encode(doctorData.getLastName());
        String contactNumberSoundex = soundex.encode(doctorData.getContactNumber());
        String ssNumberSoundex = soundex.encode(doctorData.getSsnNumber());

        // Check if Soundex codes indicate similarity
        boolean soundexSimilarity =
                firstNameSoundex.equals(soundex.encode(doctor.getFirstName())) ||
                        lastNameSoundex.equals(soundex.encode(doctor.getLastName())) ||
                        contactNumberSoundex.equals(soundex.encode(doctor.getContactNumber())) ||
                        ssNumberSoundex.equals(soundex.encode(doctor.getSsnNumber()));

        // Return true if Soundex similarity is detected
        return soundexSimilarity;
    }
}

