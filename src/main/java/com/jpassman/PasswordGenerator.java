package com.jpassman;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PasswordGenerator {
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[{]};:',<.>/?";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length, boolean useLower, boolean useUpper, boolean useDigits, boolean useSymbols) {
        if (length < 1) {
            throw new IllegalArgumentException("Length must be at least 1");
        }
        
        StringBuilder charPool = new StringBuilder();
        List<Character> guaranteedChars = new ArrayList<>();

        if (useLower) {
            charPool.append(LOWERCASE);
            guaranteedChars.add(LOWERCASE.charAt(RANDOM.nextInt(LOWERCASE.length())));
        }
        if (useUpper) {
            charPool.append(UPPERCASE);
            guaranteedChars.add(UPPERCASE.charAt(RANDOM.nextInt(UPPERCASE.length())));
        }
        if (useDigits) {
            charPool.append(DIGITS);
            guaranteedChars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        if (useSymbols) {
            charPool.append(SYMBOLS);
            guaranteedChars.add(SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length())));
        }

        if (charPool.length() == 0) {
            throw new IllegalArgumentException("At least one character set must be selected");
        }

        // Fill remaining length
        int remainingLength = length - guaranteedChars.size();
        for (int i = 0; i < remainingLength; i++) {
            int randomIndex = RANDOM.nextInt(charPool.length());
            guaranteedChars.add(charPool.charAt(randomIndex));
        }

        // Shuffle the characters
        Collections.shuffle(guaranteedChars, RANDOM);

        StringBuilder password = new StringBuilder();
        for (char c : guaranteedChars) {
            password.append(c);
        }

        return password.toString();
    }
}
