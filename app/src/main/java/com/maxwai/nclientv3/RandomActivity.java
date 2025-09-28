package com.maxwai.nclientv3;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ImageViewCompat;

import com.maxwai.nclientv3.api.RandomLoader;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.settings.Favorites;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.ImageDownloadUtility;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

public class RandomActivity extends GeneralActivity {
    public static Gallery loadedGallery = null;
    private TextView language;
    private ImageButton thumbnail;
    private ImageButton favorite;
    private TextView title;
    private TextView page;
    private View censor;
    private RandomLoader loader = null;
    private boolean isFavorite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Global.initActivity(this);
        setContentView(R.layout.activity_random);
        loader = new RandomLoader(this);


        //init components id
        Toolbar toolbar = findViewById(R.id.toolbar);
        FloatingActionButton shuffle = findViewById(R.id.shuffle);
        ImageButton share = findViewById(R.id.share);
        censor = findViewById(R.id.censor);
        language = findViewById(R.id.language);
        thumbnail = findViewById(R.id.thumbnail);
        favorite = findViewById(R.id.favorite);
        title = findViewById(R.id.title);
        page = findViewById(R.id.pages);

        //init toolbar
        setSupportActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.random_manga);


        if (loadedGallery != null) loadGallery(loadedGallery);

        shuffle.setOnClickListener(v -> loader.requestGallery());

        thumbnail.setOnClickListener(v -> {
            if (loadedGallery != null) {
                Intent intent = new Intent(RandomActivity.this, GalleryActivity.class);
                intent.putExtra(RandomActivity.this.getPackageName() + ".GALLERY", loadedGallery);
                RandomActivity.this.startActivity(intent);
            }
        });
        share.setOnClickListener(v -> {
            if (loadedGallery != null) Global.shareGallery(RandomActivity.this, loadedGallery);
        });
        censor.setOnClickListener(v -> censor.setVisibility(View.GONE));

        favorite.setOnClickListener(v -> {
            if (loadedGallery != null) {
                if (isFavorite) {
                    Favorites.removeFavorite(loadedGallery);
                    isFavorite = false;
                } else {
                    Favorites.addFavorite(loadedGallery);
                    isFavorite = true;
                }
            }
            favoriteUpdateButton();
        });

        ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.tint_light));

        ImageViewCompat.setImageTintList(shuffle, colorStateList);
        ImageViewCompat.setImageTintList(share, colorStateList);
        ImageViewCompat.setImageTintList(favorite, colorStateList);

        Global.setTint(this, shuffle.getContentBackground());
        Global.setTint(this, share.getDrawable());
        Global.setTint(this, favorite.getDrawable());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                loadedGallery = null;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void loadGallery(Gallery gallery) {
        loadedGallery = gallery;
        if (Global.isDestroyed(this)) return;
        ImageDownloadUtility.loadImage(this, gallery.getCover(), thumbnail);
        language.setText(Global.getLanguageFlag(gallery.getLanguage()));
        isFavorite = Favorites.isFavorite(loadedGallery);
        favoriteUpdateButton();
        title.setText(gallery.getTitle());
        page.setText(getString(R.string.page_count_format, gallery.getPageCount()));
        censor.setVisibility(gallery.hasIgnoredTags() ? View.VISIBLE : View.GONE);
    }

    private void favoriteUpdateButton() {
        runOnUiThread(() -> {
            ImageDownloadUtility.loadImage(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border, favorite);
            Global.setTint(this, favorite.getDrawable());
        });
    }
}
