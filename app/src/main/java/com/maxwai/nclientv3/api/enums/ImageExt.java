package com.maxwai.nclientv3.api.enums;

public enum ImageExt {
    JPG("jpg"),
    PNG("png"),
    GIF("gif"),
    WEBP("webp"),
    GIF_WEBP("gif.webp"),
    JPG_WEBP("jpg.webp"),
    PNG_WEBP("png.webp"),
    WEBP_WEBP("webp.webp");

    private final String name;

    ImageExt(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
