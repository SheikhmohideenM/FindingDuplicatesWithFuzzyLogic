package com.phonetic.projects.service;

import java.util.Random;

public class IDGenerator {

    public static String generateDoctorId(int staticNumber, int sequentialNumber, int alphabetLength ){

        StringBuilder sb = new StringBuilder();

        // Append static number
        sb.append(staticNumber);

        // Append sequential number
        sb.append(sequentialNumber);

        // Append random alphabets
        Random random = new Random();
        for (int i = 0; i < alphabetLength; i++) {
            char randomChar ;

            if(random.nextBoolean()){
                randomChar = (char) (random.nextInt(10) + '0');
            }else {
                randomChar = (char) (random.nextInt(26) + 'A'); // Uppercase alphabet
            }
            sb.append(randomChar);
        }

        return sb.toString();
    }
}
