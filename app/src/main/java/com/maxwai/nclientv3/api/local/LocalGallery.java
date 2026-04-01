package com.maxwai.nclientv3.api.local;

import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Parcel;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.components.GalleryData;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.enums.SpecialTagIds;
import com.maxwai.nclientv3.components.classes.Size;
import com.maxwai.nclientv3.files.GalleryFolder;
import com.maxwai.nclientv3.files.PageFile;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;
import java.io.FileReader;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalGallery extends GenericGallery {
    public static final Creator<LocalGallery> CREATOR = new Creator<>() {
        @Override
        public LocalGallery createFromParcel(Parcel in) {
            return new LocalGallery(in);
        }

        @Override
        public LocalGallery[] newArray(int size) {
            return new LocalGallery[size];
        }
    };
    private static final Pattern DUP_PATTERN = Pattern.compile("^(.*)\\.DUP\\d+$");
    private final GalleryFolder folder;
    @NonNull
    private final GalleryData galleryData;
    private final String title, trueTitle;
    private final boolean valid;
    private boolean hasAdvancedData = true;
    @NonNull
    private Size maxSize = new Size(0, 0), minSize = new Size(Integer.MAX_VALUE, Integer.MAX_VALUE);

    public LocalGallery(@NonNull File file, boolean jumpDataRetrieve) {
        GalleryFolder folder1;
        try {
            folder1 = new GalleryFolder(file);
        } catch (IllegalArgumentException ignore) {
            folder1 = null;

        }
        folder = folder1;
        trueTitle = file.getName();
        title = createTitle(file);
        if (jumpDataRetrieve) {
            galleryData = GalleryData.fakeData();
        } else {
            galleryData = readGalleryData();
            if (galleryData.getId() == SpecialTagIds.INVALID_ID)
                galleryData.setId(getId());
        }
        //Start search pages
        //Find page with max number
        if (folder != null)
            galleryData.setPageInfo(folder);
        valid = folder != null && folder.getPageCount() > 0;
    }

    public LocalGallery(@NonNull File file) {
        this(file, false);
    }

    private LocalGallery(Parcel in) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            galleryData = Objects.requireNonNull(in.readParcelable(GalleryData.class.getClassLoader(), GalleryData.class));
            maxSize = Objects.requireNonNull(in.readParcelable(Size.class.getClassLoader(), Size.class));
            minSize = Objects.requireNonNull(in.readParcelable(Size.class.getClassLoader(), Size.class));
        } else {
            galleryData = Objects.requireNonNull(in.readParcelable(GalleryData.class.getClassLoader()));
            maxSize = Objects.requireNonNull(in.readParcelable(Size.class.getClassLoader()));
            minSize = Objects.requireNonNull(in.readParcelable(Size.class.getClassLoader()));
        }
        trueTitle = in.readString();
        title = in.readString();
        hasAdvancedData = in.readByte() == 1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            folder = in.readParcelable(GalleryFolder.class.getClassLoader(), GalleryFolder.class);
        } else {
            folder = in.readParcelable(GalleryFolder.class.getClassLoader());
        }
        valid = true;
    }

    private static String createTitle(File file) {
        String name = file.getName();
        Matcher matcher = DUP_PATTERN.matcher(name);
        if (!matcher.matches()) return name;
        String title = matcher.group(1);
        return title == null ? name : title;
    }

    @Override
    public GalleryFolder getGalleryFolder() {
        return folder;
    }

    @NonNull
    private GalleryData readGalleryData() {
        if (folder == null) return GalleryData.fakeData();
        File nomedia = folder.getGalleryDataFile();
        try (JsonReader reader = new JsonReader(new FileReader(nomedia))) {
            return new GalleryData(reader);
        } catch (Exception ignore) {
        }
        hasAdvancedData = false;
        return GalleryData.fakeData();
    }

    public void calculateSizes() {
        for (PageFile f : folder)
            checkSize(f);
    }

    private void checkSize(File f) {
        LogUtility.d("Decoding: " + f);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), options);
        if (options.outWidth > maxSize.getWidth()) maxSize.setWidth(options.outWidth);
        if (options.outWidth < minSize.getWidth()) minSize.setWidth(options.outWidth);
        if (options.outHeight > maxSize.getHeight()) maxSize.setHeight(options.outHeight);
        if (options.outHeight < minSize.getHeight()) minSize.setHeight(options.outHeight);
    }

    @NonNull
    @Override
    public Size getMaxSize() {
        return maxSize;
    }

    @NonNull
    @Override
    public Size getMinSize() {
        return minSize;
    }

    public String getTrueTitle() {
        return trueTitle;
    }

    @Override
    public boolean hasGalleryData() {
        return hasAdvancedData;
    }

    @Override
    @NonNull
    public GalleryData getGalleryData() {
        return galleryData;
    }

    @Override
    public Type getType() {
        return Type.LOCAL;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public int getId() {
        return folder == null ? SpecialTagIds.INVALID_ID : folder.getId();
    }

    @Override
    public int getPageCount() {
        return galleryData.getPageCount();
    }

    @Override
    @NonNull
    public String getTitle() {
        return title;
    }

    public int getMin() {
        return folder.getMin();
    }

    @NonNull
    public File getDirectory() {
        return folder.getFolder();
    }

    @Nullable
    public File getPage(int index) {
        return folder.getPage(index);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(galleryData, flags);
        dest.writeParcelable(maxSize, flags);
        dest.writeParcelable(minSize, flags);
        dest.writeString(trueTitle);
        dest.writeString(title);
        dest.writeByte((byte) (hasAdvancedData ? 1 : 0));
        dest.writeParcelable(folder, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocalGallery gallery = (LocalGallery) o;

        return folder.equals(gallery.folder);
    }

    @Override
    public int hashCode() {
        return folder.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "LocalGallery{" +
            "galleryData=" + galleryData +
            ", title='" + title + '\'' +
            ", folder=" + folder +
            ", valid=" + valid +
            ", maxSize=" + maxSize +
            ", minSize=" + minSize +
            '}';
    }
}
