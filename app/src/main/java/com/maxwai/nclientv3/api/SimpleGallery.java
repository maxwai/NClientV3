package com.maxwai.nclientv3.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GalleryData;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.components.TagList;
import com.maxwai.nclientv3.api.enums.Language;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.classes.Size;
import com.maxwai.nclientv3.files.GalleryFolder;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;

public class SimpleGallery extends GenericGallery {
    public static final Creator<SimpleGallery> CREATOR = new Creator<>() {
        @Override
        public SimpleGallery createFromParcel(Parcel in) {
            return new SimpleGallery(in);
        }

        @Override
        public SimpleGallery[] newArray(int size) {
            return new SimpleGallery[size];
        }
    };
    private final String title;
    private final Uri thumbnail;
    private final int id, mediaId;
    private Language language = Language.UNKNOWN;
    private TagList tags;

    public SimpleGallery(Parcel in) {
        title = in.readString();
        id = in.readInt();
        mediaId = in.readInt();
        thumbnail = Uri.parse(in.readString());
        language = Language.values()[in.readByte()];
    }

    @SuppressLint("Range")
    public SimpleGallery(Cursor c) {
        title = c.getString(c.getColumnIndex(Queries.HistoryTable.TITLE));
        id = c.getInt(c.getColumnIndex(Queries.HistoryTable.ID));
        mediaId = c.getInt(c.getColumnIndex(Queries.HistoryTable.MEDIAID));
        thumbnail = Uri.parse(c.getString(c.getColumnIndex(Queries.HistoryTable.THUMB)));
    }

    private SimpleGallery(String title, int id, int mediaId, Uri thumbnail, Language language, TagList tags) {
        this.title = title;
        this.id = id;
        this.mediaId = mediaId;
        this.thumbnail = thumbnail;
        this.language = language;
        this.tags = tags;
    }

    public SimpleGallery(Gallery gallery) {
        title = gallery.getTitle();
        mediaId = gallery.getMediaId();
        id = gallery.getId();
        thumbnail = gallery.getThumbnail();
    }

    /**
     * Create a SimpleGallery from API v2 GalleryListItem JSON.
     * v2 list items have: id, media_id(string), thumbnail(path string),
     * english_title, japanese_title, tag_ids(int array), num_pages
     */
    public static SimpleGallery fromV2ListItem(Context context, JSONObject json) throws JSONException {
        int id = json.getInt("id");
        int mediaId;
        try {
            mediaId = Integer.parseInt(json.getString("media_id"));
        } catch (NumberFormatException e) {
            mediaId = 0;
        }
        // Title: v2 list items use english_title/japanese_title directly
        String englishTitle = json.optString("english_title", "");
        String japaneseTitle = json.optString("japanese_title", "");
        // But v2 detail/related items may use a title object
        JSONObject titleObj = json.optJSONObject("title");
        String title;
        if (titleObj != null) {
            String pretty = titleObj.optString("pretty", "");
            String english = titleObj.optString("english", "");
            String japanese = titleObj.optString("japanese", "");
            title = !pretty.isEmpty() ? pretty : (!english.isEmpty() ? english : japanese);
        } else {
            title = !englishTitle.isEmpty() ? englishTitle : japaneseTitle;
        }
        String thumbPath = json.optString("thumbnail", "");
        // Tags: v2 list items only have tag_ids, look them up from local DB
        JSONArray tagIdsArr = json.optJSONArray("tag_ids");
        TagList tags;
        Language language = Language.UNKNOWN;
        if (tagIdsArr != null && tagIdsArr.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tagIdsArr.length(); i++) {
                if (i > 0) sb.append(',');
                sb.append(tagIdsArr.getInt(i));
            }
            tags = Queries.TagTable.getTagsFromListOfInt(sb.toString());
            language = Gallery.loadLanguage(tags);
        } else {
            // v2 detail related items may have full tag objects
            JSONArray tagsArray = json.optJSONArray("tags");
            if (tagsArray != null && tagsArray.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < tagsArray.length(); i++) {
                    JSONObject tagObj = tagsArray.getJSONObject(i);
                    Tag tag = new Tag(
                        tagObj.getString("name"),
                        tagObj.optInt("count", 0),
                        tagObj.getInt("id"),
                        TagType.typeByName(tagObj.getString("type")),
                        TagStatus.DEFAULT
                    );
                    Queries.TagTable.insert(tag);
                    if (i > 0) sb.append(',');
                    sb.append(tag.getId());
                }
                tags = Queries.TagTable.getTagsFromListOfInt(sb.toString());
                language = Gallery.loadLanguage(tags);
            } else {
                tags = new TagList();
            }
        }
        if (context != null && id > Global.getMaxId()) Global.updateMaxId(context, id);
        return new SimpleGallery(title, id, mediaId, Uri.parse("https://t1." + Utility.getHost() + "/" + thumbPath), language, tags);
    }

    public boolean hasTags(Collection<Tag> tags) {
        return this.tags.hasTags(tags);
    }

    public Language getLanguage() {
        return language;
    }

    public boolean hasIgnoredTags(String s) {
        if (tags == null) return false;
        for (Tag t : tags.getAllTagsList())
            if (s.contains(t.toQueryTag(TagStatus.AVOIDED))) {
                LogUtility.d("Found: " + s + ",," + t.toQueryTag());
                return true;
            }
        return false;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Type getType() {
        return Type.SIMPLE;
    }

    @Override
    public int getPageCount() {
        return 0;
    }

    @Override
    public boolean isValid() {
        return id > 0;
    }

    @Override
    @NonNull
    public String getTitle() {
        return title;
    }

    @Override
    public Size getMaxSize() {
        return null;
    }

    @Override
    public Size getMinSize() {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeInt(id);
        dest.writeInt(mediaId);
        dest.writeString(thumbnail.toString());
        dest.writeByte((byte) language.ordinal());
        //TAGS AREN'T WRITTEN
    }

    public Uri getThumbnail() {
        return thumbnail;
    }

    public int getMediaId() {
        return mediaId;
    }

    @Override
    public GalleryFolder getGalleryFolder() {
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return "SimpleGallery{" +
            "language=" + language +
            ", title='" + title + '\'' +
            ", thumbnail=" + thumbnail +
            ", id=" + id +
            ", mediaId=" + mediaId +
            '}';
    }

    @Override
    public boolean hasGalleryData() {
        return false;
    }

    @Override
    public GalleryData getGalleryData() {
        return null;
    }
}
