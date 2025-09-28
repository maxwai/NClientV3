package com.maxwai.nclientv3.settings;

import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;

public class Favorites {


    public static void addFavorite(Gallery gallery) {
        Queries.FavoriteTable.addFavorite(gallery);
    }

    public static void removeFavorite(GenericGallery gallery) {
        Queries.FavoriteTable.removeFavorite(gallery.getId());
    }

    public static boolean isFavorite(GenericGallery gallery) {
        if (gallery == null || !gallery.isValid()) return false;
        return Queries.FavoriteTable.isFavorite(gallery.getId());
    }


}
