package com.maxwai.nclientv3.api.components;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.enums.ImageType;
import com.maxwai.nclientv3.api.enums.SpecialTagIds;
import com.maxwai.nclientv3.api.enums.TitleType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.files.GalleryFolder;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import okhttp3.Request;
import okhttp3.Response;

public class GalleryData implements Parcelable {
    public static final Creator<GalleryData> CREATOR = new Creator<>() {
        @Override
        public GalleryData createFromParcel(Parcel in) {
            return new GalleryData(in);
        }

        @Override
        public GalleryData[] newArray(int size) {
            return new GalleryData[size];
        }
    };
    @NonNull
    private Date uploadDate = new Date(0);
    private int favoriteCount, id, pageCount, mediaId;
    @NonNull
    private String[] titles = new String[]{"", "", ""};
    @NonNull
    private TagList tags = new TagList();
    @NonNull
    private Page cover = new Page(), thumbnail = new Page();
    @NonNull
    private ArrayList<Page> pages = new ArrayList<>();
    private boolean valid = true;
    private boolean checkedExt = false;
    @Nullable
    private final Context context;
    private boolean changedInfo = false;
    private boolean isDeleted = false;

    private GalleryData(@Nullable Context context) {
        this.context = context;
    }

    public GalleryData(JsonReader jr) throws IOException {
        this((Context) null);
        parseJSON(jr);
    }

    public GalleryData(@NonNull Context context, Cursor cursor, @NonNull TagList tagList) {
        this(context);
        id = cursor.getInt(Queries.getColumnFromName(cursor, Queries.GalleryTable.IDGALLERY));
        mediaId = cursor.getInt(Queries.getColumnFromName(cursor, Queries.GalleryTable.MEDIAID));
        favoriteCount = cursor.getInt(Queries.getColumnFromName(cursor, Queries.GalleryTable.FAVORITE_COUNT));

        titles[TitleType.JAPANESE.ordinal()] = cursor.getString(Queries.getColumnFromName(cursor, Queries.GalleryTable.TITLE_JP));
        titles[TitleType.PRETTY.ordinal()] = cursor.getString(Queries.getColumnFromName(cursor, Queries.GalleryTable.TITLE_PRETTY));
        titles[TitleType.ENGLISH.ordinal()] = cursor.getString(Queries.getColumnFromName(cursor, Queries.GalleryTable.TITLE_ENG));

        uploadDate = new Date(cursor.getLong(Queries.getColumnFromName(cursor, Queries.GalleryTable.UPLOAD)));
        readPagePath(cursor.getString(Queries.getColumnFromName(cursor, Queries.GalleryTable.PAGES)));
        pageCount = pages.size();
        this.tags = tagList;
    }

    protected GalleryData(Parcel in) {
        uploadDate = new Date(in.readLong());
        favoriteCount = in.readInt();
        id = in.readInt();
        pageCount = in.readInt();
        mediaId = in.readInt();
        titles = Objects.requireNonNull(in.createStringArray());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context = in.readParcelable(Context.class.getClassLoader(), Context.class);
            tags = Objects.requireNonNull(in.readParcelable(TagList.class.getClassLoader(), TagList.class));
            cover = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader(), Page.class));
            thumbnail = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader(), Page.class));
        } else {
            context = in.readParcelable(Context.class.getClassLoader());
            tags = Objects.requireNonNull(in.readParcelable(TagList.class.getClassLoader()));
            cover = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader()));
            thumbnail = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader()));
        }
        pages = Objects.requireNonNull(in.createTypedArrayList(Page.CREATOR));
        valid = in.readByte() != 0;
    }

    public static GalleryData fakeData() {
        GalleryData galleryData = new GalleryData((Context) null);
        galleryData.id = SpecialTagIds.INVALID_ID;
        galleryData.favoriteCount = -1;
        galleryData.pageCount = -1;
        galleryData.mediaId = SpecialTagIds.INVALID_ID;
        galleryData.pages.trimToSize();
        galleryData.valid = false;
        return galleryData;
    }

    public boolean hasUpdatedInfo() {
        return changedInfo;
    }


    public boolean isDeleted() {
        return isDeleted;
    }

    private void parseJSON(JsonReader jr) throws IOException {
        jr.beginObject();
        while (jr.peek() != JsonToken.END_OBJECT) {
            switch (jr.nextName()) {
                case "upload_date":
                    uploadDate = new Date(jr.nextLong() * 1000);
                    break;
                case "num_favorites":
                    favoriteCount = jr.nextInt();
                    break;
                case "num_pages":
                    pageCount = jr.nextInt();
                    break;
                case "media_id":
                    if (jr.peek() == JsonToken.STRING) {
                        mediaId = Integer.parseInt(jr.nextString());
                    } else {
                        mediaId = jr.nextInt();
                    }
                    break;
                case "id":
                    id = jr.nextInt();
                    break;
                case "cover":
                    cover = new Page(ImageType.COVER, jr);
                    break;
                case "thumbnail":
                    thumbnail = new Page(ImageType.THUMBNAIL, jr);
                    break;
                case "pages":
                    int actualPage = 0;
                    jr.beginArray();
                    while (jr.hasNext())
                        pages.add(new Page(ImageType.PAGE, jr, actualPage++));
                    jr.endArray();
                    pages.trimToSize();
                    break;
                case "title":
                    readTitles(jr);
                    break;
                case "tags":
                    readTags(jr);
                    break;
                case "error":
                    jr.skipValue();
                    valid = false;
                    break;
                default:
                    jr.skipValue();
                    break;
            }
        }
        jr.endObject();
    }

    private void setTitle(TitleType type, String title) {
        titles[type.ordinal()] = Utility.unescapeUnicodeString(title);
    }

    private void readTitles(JsonReader jr) throws IOException {
        jr.beginObject();
        while (jr.peek() != JsonToken.END_OBJECT) {
            switch (jr.nextName()) {
                case "japanese":
                    setTitle(TitleType.JAPANESE, jr.peek() != JsonToken.NULL ? jr.nextString() : "");
                    break;
                case "english":
                    setTitle(TitleType.ENGLISH, jr.peek() != JsonToken.NULL ? jr.nextString() : "");
                    break;
                case "pretty":
                    setTitle(TitleType.PRETTY, jr.peek() != JsonToken.NULL ? jr.nextString() : "");
                    break;
                default:
                    jr.skipValue();
                    break;
            }
            if (jr.peek() == JsonToken.NULL) jr.skipValue();
        }
        jr.endObject();
    }

    private void readTags(JsonReader jr) throws IOException {
        jr.beginArray();
        while (jr.hasNext()) {
            Tag createdTag = new Tag(jr);
            Queries.TagTable.insert(createdTag);
            tags.addTag(createdTag);
        }
        jr.endArray();
        tags.sort((o1, o2) -> o2.getCount() - o1.getCount());
    }

    @NonNull
    public Date getUploadDate() {
        return uploadDate;
    }

    public int getFavoriteCount() {
        return favoriteCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageInfo(GalleryFolder folder) {
        this.pageCount = folder.getPageCount();
        if (pageCount > 0) {
            Uri firstPage = folder.getFirstPage().toUri();
            this.cover.setImagePath(firstPage);
            this.thumbnail.setImagePath(firstPage);
        }
    }

    public void setCheckedExt() {
        checkedExt = true;
    }

    public int getMediaId() {
        return mediaId;
    }

    public String getTitle(TitleType type) {
        return titles[type.ordinal()];
    }

    @NonNull
    public TagList getTags() {
        return tags;
    }

    @NonNull
    public Page getCover() {
        return cover;
    }

    @NonNull
    public Page getThumbnail() {
        return thumbnail;
    }

    public Page getPage(int index) {
        return pages.get(index);
    }

    @NonNull
    public ArrayList<Page> getPages() {
        return pages;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean getCheckedExt() {
        return checkedExt;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(uploadDate.getTime());
        dest.writeInt(favoriteCount);
        dest.writeInt(id);
        dest.writeInt(pageCount);
        dest.writeInt(mediaId);
        dest.writeStringArray(titles);
        if (context instanceof Parcelable)
            dest.writeParcelable((Parcelable) context, flags);
        else
            dest.writeParcelable(null, flags);
        dest.writeParcelable(tags, flags);
        dest.writeParcelable(cover, flags);
        dest.writeParcelable(thumbnail, flags);
        dest.writeTypedList(pages);
        dest.writeByte((byte) (valid ? 1 : 0));
    }

    private String truncateUrl(Uri uri) {
        String output = uri.toString();
        return output.substring(output.lastIndexOf('/'));
    }

    public String createPagePath() {
        StringWriter writer = new StringWriter();
        writer.write(Integer.toString(pages.size()));
        writer.write(";");
        writer.write(truncateUrl(cover.getThumbnailPath()));
        writer.write(";");
        writer.write(truncateUrl(thumbnail.getThumbnailPath()));
        writer.write(";");
        if (pages.isEmpty()) return writer.toString();
        for (Page page : pages) {
            writer.write(truncateUrl(page.getImagePath()));
            writer.write(";");
        }
        return writer.toString();
    }

    private void readPagePathNew(String path) {
        LogUtility.d(path);
        String[] parts = path.split(";");
        if (parts[1].startsWith("http")) {
            changedInfo = true;
            cover = new Page(ImageType.COVER, Uri.parse(parts[1]));
            thumbnail = new Page(ImageType.THUMBNAIL, Uri.parse(parts[2]));
            int absolutePage = 0;
            for (int i = 3; i < parts.length; i++) {
                pages.add(new Page(ImageType.PAGE, Uri.parse(parts[i]), null, absolutePage++));
            }
            return;
        }
        cover = new Page(ImageType.COVER, Uri.parse("https://t1." + Utility.getHost() + "/galleries/" + mediaId + parts[1]));
        thumbnail = new Page(ImageType.THUMBNAIL, Uri.parse("https://t1." + Utility.getHost() + "/galleries/" + mediaId + parts[2]));
        int absolutePage = 0;
        for (int i = 3; i < parts.length; i++) {
            pages.add(new Page(ImageType.PAGE, Uri.parse("https://i1." + Utility.getHost() + "/galleries/" + mediaId + parts[i]), null, absolutePage++));
        }
    }

    private void readPagePath(String path) {
        if (path.contains(";") && path.contains("/")) {
            readPagePathNew(path);
            return;
        }
        Thread updateThread = new Thread(() -> {
            // Old entry, needs to be updated
            String detailUrl = Utility.getBaseUrl() + "api/v2/galleries/" + id;
            try (Response resp = Global.getClient(Objects.requireNonNull(context)).newCall(new Request.Builder().url(detailUrl).build()).execute()) {
                String body = resp.body().string();
                if (resp.code() == HttpURLConnection.HTTP_OK) {
                    JSONObject v2 = new JSONObject(body);
                    Gallery gallery = new Gallery(context, v2.toString(), null, false);
                    cover = new Page(ImageType.COVER, gallery.getCover());
                    thumbnail = new Page(ImageType.THUMBNAIL, gallery.getThumbnail());
                    for (int i = 0; i < gallery.getPageCount(); i++) {
                        pages.add(new Page(ImageType.PAGE, gallery.getHighPage(i), null, i));
                    }
                    valid = true;
                } else if (resp.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                    isDeleted = true;
                } else {
                    LogUtility.w("Got rate limit while updating favorites");
                    changedInfo = false;
                    valid = false;
                }
            } catch (IOException | JSONException e) {
                LogUtility.e(e);
            }
        });
        updateThread.start();
        try {
            changedInfo = true;
            updateThread.join();
        } catch (InterruptedException e) {
            LogUtility.w(e);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "GalleryData{" +
            "uploadDate=" + uploadDate +
            ", favoriteCount=" + favoriteCount +
            ", id=" + id +
            ", pageCount=" + pageCount +
            ", mediaId=" + mediaId +
            ", titles=" + Arrays.toString(titles) +
            ", tags=" + tags +
            ", cover=" + cover +
            ", thumbnail=" + thumbnail +
            ", pages=" + pages +
            ", valid=" + valid +
            '}';
    }
}
