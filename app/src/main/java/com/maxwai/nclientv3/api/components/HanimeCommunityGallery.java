package com.maxwai.nclientv3.api.components;

import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.components.classes.Size;
import com.maxwai.nclientv3.files.GalleryFolder;

import org.json.JSONObject;

import java.util.Locale;

public class HanimeCommunityGallery extends GenericGallery {
    public static final Creator<HanimeCommunityGallery> CREATOR = new Creator<>() {
        @Override
        public HanimeCommunityGallery createFromParcel(Parcel in) {
            return new HanimeCommunityGallery(in);
        }

        @Override
        public HanimeCommunityGallery[] newArray(int size) {
            return new HanimeCommunityGallery[size];
        }
    };

    private final int id;
    private final String imageUrl;
    private final String channelName;
    private final String uploader;
    private final int width;
    private final int height;

    public HanimeCommunityGallery(JSONObject json) throws org.json.JSONException {
        id = json.getInt("id");
        imageUrl = json.optString("url", "");
        channelName = json.optString("channel_name", "");
        uploader = json.optString("username", "");
        width = json.optInt("width", 1000);
        height = json.optInt("height", 1400);
    }

    private HanimeCommunityGallery(Parcel in) {
        id = in.readInt();
        imageUrl = in.readString();
        channelName = in.readString();
        uploader = in.readString();
        width = in.readInt();
        height = in.readInt();
    }

    public Uri getImageUri() {
        return Uri.parse(imageUrl);
    }

    public String getImageUrl() {
        return imageUrl;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Type getType() {
        return Type.HANIME_COMMUNITY;
    }

    @Override
    public int getPageCount() {
        return 1;
    }

    @Override
    public boolean isValid() {
        return id > 0 && imageUrl != null && !imageUrl.isEmpty() && imageUrl.startsWith("http");
    }

    @Override
    @NonNull
    public String getTitle() {
        String channel = channelName.isEmpty() ? "community" : channelName;
        String user = uploader.isEmpty() ? "" : " · " + uploader;
        return String.format(Locale.US, "%s%s", channel, user);
    }

    @Override
    public Size getMaxSize() {
        return new Size(width, height);
    }

    @Override
    public Size getMinSize() {
        return getMaxSize();
    }

    @Override
    public GalleryFolder getGalleryFolder() {
        return null;
    }

    @Override
    public boolean hasGalleryData() {
        return false;
    }

    @Override
    public GalleryData getGalleryData() {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(imageUrl);
        dest.writeString(channelName);
        dest.writeString(uploader);
        dest.writeInt(width);
        dest.writeInt(height);
    }
}
