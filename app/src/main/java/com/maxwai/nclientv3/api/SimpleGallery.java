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
import com.maxwai.nclientv3.api.components.Page;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.components.TagList;
import com.maxwai.nclientv3.api.enums.ImageExt;
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
import org.jsoup.nodes.Element;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

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
    private final ImageExt thumbnail;
    private final int id, mediaId;
    private Language language = Language.UNKNOWN;
    private TagList tags;

    public SimpleGallery(Parcel in) {
        title = in.readString();
        id = in.readInt();
        mediaId = in.readInt();
        thumbnail = ImageExt.values()[in.readByte()];
        language = Language.values()[in.readByte()];
    }

    public boolean hasTags(Collection<Tag> tags) {
        return this.tags.hasTags(tags);
    }

    @SuppressLint("Range")
    public SimpleGallery(Cursor c) {
        title = c.getString(c.getColumnIndex(Queries.HistoryTable.TITLE));
        id = c.getInt(c.getColumnIndex(Queries.HistoryTable.ID));
        mediaId = c.getInt(c.getColumnIndex(Queries.HistoryTable.MEDIAID));
        thumbnail = ImageExt.values()[c.getInt(c.getColumnIndex(Queries.HistoryTable.THUMB))];
    }

    public SimpleGallery(Context context, Element e) {
        String temp;
        String tags = e.attr("data-tags").replace(' ', ',');
        this.tags = Queries.TagTable.getTagsFromListOfInt(tags);
        language = Gallery.loadLanguage(this.tags);
        Element a = e.getElementsByTag("a").first();
        temp = Objects.requireNonNull(a).attr("href");
        id = Integer.parseInt(temp.substring(3, temp.length() - 1));
        a = e.getElementsByTag("img").first();
        temp = Objects.requireNonNull(a).hasAttr("data-src") ? a.attr("data-src") : a.attr("src");
        mediaId = Integer.parseInt(temp.substring(temp.indexOf("galleries") + 10, temp.lastIndexOf('/')));
        String extension = temp.substring(temp.indexOf('.', temp.lastIndexOf('/')) + 1);
        thumbnail = Page.stringToExt(extension);
        title = Objects.requireNonNull(e.getElementsByTag("div").first()).text();
        if (context != null && id > Global.getMaxId()) Global.updateMaxId(context, id);
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
        // Thumbnail extension from path string like "galleries/123/thumb.jpg"
        String thumbPath = json.optString("thumbnail", "");
        ImageExt thumbExt = extFromPath(thumbPath);
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
        return new SimpleGallery(title, id, mediaId, thumbExt, language, tags);
    }

    private static ImageExt extFromPath(String path) {
        if (path == null || path.isEmpty()) return ImageExt.JPG;
        // Handle paths like "galleries/123/thumb.jpg" or "galleries/123/thumb.jpg.webp"
        String lower = path.toLowerCase();
        if (lower.endsWith(".gif.webp")) return ImageExt.GIF_WEBP;
        if (lower.endsWith(".png.webp")) return ImageExt.PNG_WEBP;
        if (lower.endsWith(".jpg.webp")) return ImageExt.JPG_WEBP;
        if (lower.endsWith(".webp.webp")) return ImageExt.WEBP_WEBP;
        if (lower.endsWith(".webp")) return ImageExt.WEBP;
        if (lower.endsWith(".gif")) return ImageExt.GIF;
        if (lower.endsWith(".png")) return ImageExt.PNG;
        return ImageExt.JPG;
    }

    private SimpleGallery(String title, int id, int mediaId, ImageExt thumbnail, Language language, TagList tags) {
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
        thumbnail = gallery.getThumb();
    }

    private static String extToString(ImageExt ext) {
        if (ext == null) {
            return null;
        }
        return ext.getName();
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
        dest.writeByte((byte) thumbnail.ordinal());
        dest.writeByte((byte) language.ordinal());
        //TAGS AREN'T WRITTEN
    }

    public Uri getThumbnail() {
        if (thumbnail == ImageExt.GIF) {
            return Uri.parse(String.format(Locale.US, "https://i1." + Utility.getHost() + "/galleries/%d/1.gif", mediaId));
        }
        return Uri.parse(String.format(Locale.US, "https://t1." + Utility.getHost() + "/galleries/%d/thumb.%s", mediaId, extToString(thumbnail)));
    }

    public int getMediaId() {
        return mediaId;
    }

    public ImageExt getThumb() {
        return thumbnail;
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
