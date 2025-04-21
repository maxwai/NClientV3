package com.maxwai.nclientv3.api.enums;

import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;

import java.util.Arrays;

public enum Language {
    ENGLISH(R.string.only_english, "ENGLISH"),
    CHINESE(R.string.only_chinese, "CHINESE"),
    JAPANESE(R.string.only_japanese, "JAPANESE"),
    ALL(R.string.all_languages, "ALL"),
    UNKNOWN(R.string.unknown_language, "UNKNOWN");


    private final int nameResId;
    @Nullable
    private final String language;

    Language(int nameResId, @Nullable String languageCode) {
        this.nameResId = nameResId;
        this.language = languageCode;
    }

    public int getNameResId() {
        return nameResId;
    }

    @Nullable
    public String getLanguage() {
        return language;
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
