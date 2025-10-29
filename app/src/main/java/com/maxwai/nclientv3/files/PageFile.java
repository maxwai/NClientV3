package com.maxwai.nclientv3.files;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.maxwai.nclientv3.api.enums.ImageExt;

import java.io.File;
import java.util.Objects;

public class PageFile extends File implements Parcelable {
    public static final Creator<PageFile> CREATOR = new Creator<>() {
        @Override
        public PageFile createFromParcel(Parcel in) {
            return new PageFile(in);
        }

        @Override
        public PageFile[] newArray(int size) {
            return new PageFile[size];
        }
    };
    private final ImageExt ext;
    private final int page;

    public PageFile(ImageExt ext, File file, int page) {
        super(file.getAbsolutePath());
        this.ext = ext;
        this.page = page;
    }

    protected PageFile(Parcel in) {
        super(Objects.requireNonNull(in.readString()));
        page = in.readInt();
        ext = ImageExt.values()[in.readByte()];
    }

    public Uri toUri() {
        return Uri.fromFile(this);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.getAbsolutePath());
        dest.writeInt(page);
        dest.writeByte((byte) ext.ordinal());
    }


}
