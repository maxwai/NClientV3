package com.maxwai.nclientv3.api.enums;

import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;


public enum OnlyLanguage {
    ENGLISH(R.string.only_english,"ENGLISH"),
    CHINESE(R.string.only_chinese,"CHINESE"),
    JAPANESE(R.string.only_japanese,"JAPANESE"),
//    UNKNOWN(R.string.sort_popular_day,"UNKNOWN"),
    ALL(R.string.all_languages,"ALL");


    private final int nameId;
    @Nullable
    private final String language;

    OnlyLanguage(int nameId,@Nullable String languageCode) {
        this.nameId = nameId;
        this.language = languageCode;
    }

    public static OnlyLanguage findFromLanguages(@Nullable Language langEnum) {
        if (langEnum == null)
            return OnlyLanguage.ALL;

        for (OnlyLanguage onlyLangItem : OnlyLanguage.values()) {
            String lang = onlyLangItem.getLanguage();
            if (lang != null && langEnum.name().contains(lang)) {
                return onlyLangItem;
            }
        }

        return OnlyLanguage.ALL;
    }

    public int getNameId() {
        return nameId;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }
}


