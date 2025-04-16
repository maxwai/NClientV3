package com.maxwai.nclientv3.api.components;

import android.database.Cursor;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.api.enums.ImageExt;
import com.maxwai.nclientv3.api.enums.ImageType;
import com.maxwai.nclientv3.api.enums.SpecialTagIds;
import com.maxwai.nclientv3.api.enums.TitleType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

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

    private GalleryData() {
    }

    public GalleryData(JsonReader jr) throws IOException {
        parseJSON(jr);
    }

    public GalleryData(Cursor cursor, @NonNull TagList tagList) throws IOException {
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
            tags = Objects.requireNonNull(in.readParcelable(TagList.class.getClassLoader(), TagList.class));
            cover = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader(), Page.class));
            thumbnail = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader(), Page.class));
        } else {
            tags = Objects.requireNonNull(in.readParcelable(TagList.class.getClassLoader()));
            cover = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader()));
            thumbnail = Objects.requireNonNull(in.readParcelable(Page.class.getClassLoader()));
        }
        pages = Objects.requireNonNull(in.createTypedArrayList(Page.CREATOR));
        valid = in.readByte() != 0;
    }

    public static GalleryData fakeData() {
        GalleryData galleryData = new GalleryData();
        galleryData.id = SpecialTagIds.INVALID_ID;
        galleryData.favoriteCount = -1;
        galleryData.pageCount = -1;
        galleryData.mediaId = SpecialTagIds.INVALID_ID;
        galleryData.pages.trimToSize();
        galleryData.valid = false;
        return galleryData;
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
                    mediaId = jr.nextInt();
                    break;
                case "id":
                    id = jr.nextInt();
                    break;
                case "images":
                    readImages(jr);
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

    private void readImages(JsonReader jr) throws IOException {
        int actualPage = 0;
        jr.beginObject();
        while (jr.peek() != JsonToken.END_OBJECT) {
            switch (jr.nextName()) {
                case "cover":
                    cover = new Page(ImageType.COVER, jr);
                    break;
                case "thumbnail":
                    thumbnail = new Page(ImageType.THUMBNAIL, jr);
                    break;
                case "pages":
                    jr.beginArray();
                    while (jr.hasNext())
                        pages.add(new Page(ImageType.PAGE, jr, actualPage++));
                    jr.endArray();
                    break;
                default:
                    jr.skipValue();
                    break;
            }
        }
        jr.endObject();
        pages.trimToSize();
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

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
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
        dest.writeParcelable(tags, flags);
        dest.writeParcelable(cover, flags);
        dest.writeParcelable(thumbnail, flags);
        dest.writeTypedList(pages);
        dest.writeByte((byte) (valid ? 1 : 0));
    }

    private void writeInterval(StringWriter writer, int intervalLen, ImageExt referencePage) {
        writer.write(Integer.toString(intervalLen));
        writer.write(";");
        writer.write(referencePage.getName());
        writer.write(";");
    }

    public String createPagePath() {
        StringWriter writer = new StringWriter();
        writer.write(Integer.toString(pages.size()));
        writer.write(";");
        writer.write(cover.extToString());
        writer.write(";");
        writer.write(thumbnail.extToString());
        writer.write(";");
        if (pages.isEmpty()) return writer.toString();
        ImageExt referencePage = pages.get(0).getImageExt(), actualPage;
        int intervalLen = 1;
        for (int i = 1; i < pages.size(); i++) {
            actualPage = pages.get(i).getImageExt();
            if (actualPage != referencePage) {
                writeInterval(writer, intervalLen, referencePage);
                referencePage = actualPage;
                intervalLen = 1;
            } else intervalLen++;
        }
        writeInterval(writer, intervalLen, referencePage);
        return writer.toString();
    }

    private void readPagePathNew(String path) {
        System.out.println(path);
        String[] parts = path.split(";");
        cover = new Page(ImageType.COVER, Page.stringToExt(parts[1]));
        thumbnail = new Page(ImageType.THUMBNAIL, Page.stringToExt(parts[2]));
        int absolutePage = 0;
        for (int i = 3; i < parts.length; i += 2) {
            for (int j = 0; j < Integer.parseInt(parts[i]); j++) {//add pageOfType time a page of actualChar
                pages.add(new Page(ImageType.PAGE, Page.stringToExt(parts[i+1]), absolutePage++));
            }
        }
    }

    private void readPagePath(String path) throws IOException {
        if (path.contains(";")) {
            readPagePathNew(path);
            return;
        }
        System.out.println(path);
        StringReader reader = new StringReader(path + "e");//flag for the end
        int absolutePage = 0;
        int actualChar;
        int pageOfType = 0;
        boolean specialImages = true;//compability variable
        String extension = null;
        while ((actualChar = reader.read()) != 'e') {
            switch (actualChar) {
                case 'p':
                    extension = "png";
                case 'j':
                    if (extension == null)
                        extension = "jpg";
                case 'g':
                    if (extension == null)
                        extension = "gif";
                case 'w':
                    if (extension == null)
                        extension = "webp";
                    if (specialImages) {
                        cover = new Page(ImageType.COVER, Page.stringToExt(extension));
                        thumbnail = new Page(ImageType.THUMBNAIL, Page.stringToExt(extension));
                        specialImages = false;
                    } else {
                        for (int j = 0; j < pageOfType; j++) {//add pageOfType time a page of actualChar
                            pages.add(new Page(ImageType.PAGE, Page.stringToExt(extension), absolutePage++));
                        }
                    }
                    pageOfType = 0;//reset digits
                    extension = null;
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    pageOfType *= 10;
                    pageOfType += actualChar - '0';
                    break;
                default:
                    break;
            }
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
