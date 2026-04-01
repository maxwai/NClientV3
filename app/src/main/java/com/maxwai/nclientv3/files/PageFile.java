package com.maxwai.nclientv3.files;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

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
    private final int page;

    public PageFile(File file, int page) {
        super(file.getAbsolutePath());
        this.page = page;
    }

    protected PageFile(Parcel in) {
        super(Objects.requireNonNull(in.readString()));
        page = in.readInt();
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
    }


}
