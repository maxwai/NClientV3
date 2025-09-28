package com.maxwai.nclientv3.api.enums;

import com.maxwai.nclientv3.R;

import java.util.Arrays;

public enum Language {
    ENGLISH(R.string.only_english),
    CHINESE(R.string.only_chinese),
    JAPANESE(R.string.only_japanese),
    ALL(R.string.all_languages),
    UNKNOWN(R.string.unknown_language);


    private final int nameResId;

    Language(int nameResId) {
        this.nameResId = nameResId;
    }

    public int getNameResId() {
        return nameResId;
    }


    /**
     * @return Array without the UNKNOWN value
     */
    public static Language[] getFilteredValuesArray() {
        return Arrays.stream(Language.values())
            .filter(lang -> lang != Language.UNKNOWN)
            .toArray(Language[]::new);
    }
}
