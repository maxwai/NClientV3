package com.maxwai.nclientv3.api.components;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.api.enums.ImageExt;
import com.maxwai.nclientv3.api.enums.ImageType;
import com.maxwai.nclientv3.components.classes.Size;

import java.io.IOException;

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
    private ImageExt imageExt;
    private Size size = new Size(0, 0);

    Page() {
        this.imageType = ImageType.PAGE;
        this.imageExt = ImageExt.JPG;
        this.page = 0;
    }

    public Page(ImageType type, JsonReader reader) throws IOException {
        this(type, reader, 0);
    }

    public Page(ImageType type, ImageExt ext) {
        this(type, ext, 0);
    }

    public Page(ImageType type, ImageExt ext, int page) {
        this.imageType = type;
        this.imageExt = ext;
        this.page = page;
    }

    public Page(ImageType type, JsonReader reader, int page) throws IOException {
        this.imageType = type;
        this.page = page;
        reader.beginObject();
        while (reader.peek() != JsonToken.END_OBJECT) {
            switch (reader.nextName()) {
                case "t":
                    imageExt = stringToExt(reader.nextString());
                    break;
                case "w":
                    size.setWidth(reader.nextInt());
                    break;
                case "h":
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
        imageExt = ImageExt.values()[in.readByte()];
        imageType = ImageType.values()[in.readByte()];
    }

    public static ImageExt stringToExt(String ext) {
        switch (ext.toLowerCase()) {
            case "gif":
            case "g":
                return ImageExt.GIF;
            case "png":
            case "p":
                return ImageExt.PNG;
            case "jpg":
            case "j":
                return ImageExt.JPG;
            case "webp":
            case "w":
                return ImageExt.WEBP;
            case "gif.webp":
                return ImageExt.GIF_WEBP;
            case "png.webp":
                return ImageExt.PNG_WEBP;
            case "jpg.webp":
                return ImageExt.JPG_WEBP;
            case "webp.webp":
                return ImageExt.WEBP_WEBP;
        }
        return null;
    }

    public String extToString() {
        return imageExt.getName();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(page);
        dest.writeParcelable(size, flags);
        dest.writeByte((byte) (imageExt == null ? ImageExt.JPG.ordinal() : imageExt.ordinal()));
        dest.writeByte((byte) (imageType == null ? ImageType.PAGE.ordinal() : imageType.ordinal()));
    }

    public ImageExt getImageExt() {
        return imageExt;
    }

    public void setImageExt(ImageExt imageExt) {
        this.imageExt = imageExt;
    }

    public Size getSize() {
        return size;
    }

    @NonNull
    @Override
    public String toString() {
        return "Page{" +
            "page=" + page +
            ", imageExt=" + imageExt +
            ", imageType=" + imageType +
            ", size=" + size +
            '}';
    }
}
