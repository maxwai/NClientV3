package com.maxwai.nclientv3.api.components;

import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.enums.ImageType;
import com.maxwai.nclientv3.components.classes.Size;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.Objects;

public class Page implements Parcelable {
    public static final Creator<Page> CREATOR = new Creator<>() {
        @Override
        public Page createFromParcel(Parcel in) {
            return new Page(in);
        }

        @Override
        public Page[] newArray(int size) {
            return new Page[size];
        }
    };
    private final int page;
    private final ImageType imageType;
    private Uri path;
    @Nullable
    private Uri thumbPath;
    private Size size = new Size(0, 0);

    Page() {
        this.imageType = ImageType.PAGE;
        this.page = 0;
    }

    public Page(ImageType type, JsonReader reader) throws IOException {
        this(type, reader, 0);
    }

    public Page(ImageType type, Uri path) {
        this(type, path, null, 0);
    }

    public Page(ImageType type, Uri path, @Nullable Uri thumbPath, int page) {
        this.imageType = type;
        this.path = path;
        this.thumbPath = thumbPath;
        this.page = page;
    }

    public Page(ImageType type, JsonReader reader, int page) throws IOException {
        this.imageType = type;
        this.page = page;
        reader.beginObject();
        while (reader.peek() != JsonToken.END_OBJECT) {
            switch (reader.nextName()) {
                case "path":
                    String prefix = imageType == ImageType.PAGE ? "i1" : "t1";
                    path = Uri.parse("https://" + prefix + "." + Utility.getHost() + "/" + reader.nextString());
                    break;
                case "thumbnail":
                    thumbPath = Uri.parse("https://t1." + Utility.getHost() + "/" + reader.nextString());
                    break;
                case "width":
                    size.setWidth(reader.nextInt());
                    break;
                case "height":
                    size.setHeight(reader.nextInt());
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
    }

    protected Page(Parcel in) {
        page = in.readInt();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            size = in.readParcelable(Size.class.getClassLoader(), Size.class);
        } else {
            size = in.readParcelable(Size.class.getClassLoader());
        }
        path = Uri.parse(in.readString());
        String thumbString = in.readString();
        thumbPath = Objects.requireNonNull(thumbString).isEmpty() ? null : Uri.parse(thumbString);
        imageType = ImageType.values()[in.readByte()];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(page);
        dest.writeParcelable(size, flags);
        dest.writeString(path.toString());
        dest.writeString(thumbPath == null ? "" : thumbPath.toString());
        dest.writeByte((byte) (imageType == null ? ImageType.PAGE.ordinal() : imageType.ordinal()));
    }

    public Uri getImagePath() {
        return path;
    }

    public void setImagePath(Uri path) {
        this.path = path;
    }

    @NonNull
    public Uri getThumbnailPath() {
        return thumbPath == null ? path : thumbPath;
    }

    public Size getSize() {
        return size;
    }

    @NonNull
    @Override
    public String toString() {
        return "Page{" +
            "page=" + page +
            ", path=" + path +
            ", thumbPath=" + thumbPath +
            ", imageType=" + imageType +
            ", size=" + size +
            '}';
    }
}
