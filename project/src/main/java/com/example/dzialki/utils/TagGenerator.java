package com.example.dzialki.utils;

import java.util.Random;

public class TagGenerator {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TAG_LENGTH = 4;
    private static final Random RANDOM = new Random();

    public static String generateTag() {
        StringBuilder tag = new StringBuilder();
        for (int i = 0; i < TAG_LENGTH; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            tag.append(CHARACTERS.charAt(index));
        }
        return tag.toString();
    }
}