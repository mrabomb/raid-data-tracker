package com.raidtracker.filereadwriter;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class ProfileHashDisplay {
    private ProfileHashDisplay() {
    }

    public static String format(String hashValue) {
        if (hashValue == null || hashValue.trim().isEmpty() || FileReadWriter.ALL_PROFILE_HASHES.equals(hashValue)) {
            return hashValue;
        }

        try {
            long parsed = Long.parseLong(hashValue);
            return String.format(Locale.ROOT, "0x%04x", parsed);
        } catch (NumberFormatException ignored) {
            return hashValue;
        }
    }

    public static String resolveSelectedHash(String selectedLabel, List<String> profileHashes, Function<String, String> displayLabelFactory) {
        if (selectedLabel == null || FileReadWriter.ALL_PROFILE_HASHES.equals(selectedLabel)) {
            return FileReadWriter.ALL_PROFILE_HASHES;
        }

        for (String profileHash : profileHashes) {
            if (displayLabelFactory.apply(profileHash).equals(selectedLabel)) {
                return profileHash;
            }
        }

        return selectedLabel;
    }
}
