package com.maxwai.nclientv3.api;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.components.Ranges;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.api.enums.Language;
import com.maxwai.nclientv3.api.enums.SortType;
import com.maxwai.nclientv3.api.enums.SpecialTagIds;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.api.local.LocalGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import okhttp3.Request;
import okhttp3.Response;

public class InspectorV3 extends Thread implements Parcelable {
    public static final Creator<InspectorV3> CREATOR = new Creator<>() {
        @Override
        public InspectorV3 createFromParcel(Parcel in) {
            return new InspectorV3(in);
        }

        @Override
        public InspectorV3[] newArray(int size) {
            return new InspectorV3[size];
        }
    };
    private SortType sortType;
    private boolean custom;
    private int page, pageCount = -1, id;
    private String query, url;
    private ApiRequestType requestType;
    private Set<Tag> tags;
    private ArrayList<GenericGallery> galleries = null;
    private Ranges ranges = null;
    private InspectorResponse response;
    private WeakReference<Context> context;
    private String jsonResponse;

    protected InspectorV3(Parcel in) {
        sortType = SortType.values()[in.readByte()];
        custom = in.readByte() != 0;
        page = in.readInt();
        pageCount = in.readInt();
        id = in.readInt();
        query = in.readString();
        url = in.readString();
        requestType = ApiRequestType.values[in.readByte()];
        List<? extends GenericGallery> tmpList = null;
        switch (GenericGallery.Type.values()[in.readByte()]) {
            case LOCAL:
                tmpList = in.createTypedArrayList(LocalGallery.CREATOR);
                break;
            case SIMPLE:
                tmpList = in.createTypedArrayList(SimpleGallery.CREATOR);
                break;
            case COMPLETE:
                tmpList = in.createTypedArrayList(Gallery.CREATOR);
                break;
        }
        if (tmpList != null)
        {
            galleries = new ArrayList<>();
            galleries.addAll(tmpList);
        }
        tags = new HashSet<>(Objects.requireNonNull(in.createTypedArrayList(Tag.CREATOR)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ranges = in.readParcelable(Ranges.class.getClassLoader(), Ranges.class);
        } else {
            ranges = in.readParcelable(Ranges.class.getClassLoader());
        }
    }

    private InspectorV3(Context context, InspectorResponse response) {
        initialize(context, response);
    }

    /**
     * This method will not run, but a WebView inside MainActivity will do it in its place
     */
    public static InspectorV3 favoriteInspector(Context context, String query, int page, InspectorResponse response) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.page = page;
        inspector.pageCount = 0;
        inspector.query = query == null ? "" : query;
        inspector.requestType = ApiRequestType.FAVORITE;
        inspector.tags = new HashSet<>(1);
        inspector.createUrl();
        return inspector;
    }

    /**
     * @param favorite true if random online favorite, false for general random manga
     */
    public static InspectorV3 randomInspector(Context context, InspectorResponse response, boolean favorite) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.requestType = favorite ? ApiRequestType.RANDOM_FAVORITE : ApiRequestType.RANDOM;
        inspector.createUrl();
        return inspector;
    }

    public static InspectorV3 galleryInspector(Context context, int id, InspectorResponse response) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.id = id;
        inspector.requestType = ApiRequestType.BYSINGLE;
        inspector.createUrl();
        return inspector;
    }

    public static InspectorV3 basicInspector(Context context, int page, InspectorResponse response) {
        return searchInspector(context, null, null, page, Global.getSortType(), null, response);
    }

    public static InspectorV3 tagInspector(Context context, Tag tag, int page, SortType sortType, InspectorResponse response) {
        Collection<Tag> tags;
        if (!Global.isOnlyTag()) {
            tags = getDefaultTags();
            tags.add(tag);
        } else {
            tags = Collections.singleton(tag);
        }
        return searchInspector(context, null, tags, page, sortType, null, response);
    }

    public static InspectorV3 searchInspector(Context context, String query, Collection<Tag> tags, int page, SortType sortType, @Nullable Ranges ranges, InspectorResponse response) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.custom = tags != null;
        inspector.tags = inspector.custom ? new HashSet<>(tags) : getDefaultTags();
        inspector.tags.addAll(getLanguageTags(Global.getOnlyLanguage()));
        inspector.page = page;
        inspector.pageCount = 0;
        inspector.ranges = ranges;
        inspector.query = query == null ? "" : query;
        inspector.sortType = sortType;
        if (inspector.query.isEmpty() && (ranges == null || ranges.isDefault())) {
            switch (inspector.tags.size()) {
                case 0:
                    inspector.requestType = ApiRequestType.BYALL;
                    inspector.tryByAllPopular();
                    break;
                case 1:
                    inspector.requestType = ApiRequestType.BYTAG;
                    //else by search for the negative tag
                    if (inspector.getTag().getStatus() != TagStatus.AVOIDED)
                        break;
                default:
                    inspector.requestType = ApiRequestType.BYSEARCH;
                    break;
            }
        } else inspector.requestType = ApiRequestType.BYSEARCH;
        inspector.createUrl();
        return inspector;
    }

    @NonNull
    private static HashSet<Tag> getDefaultTags() {
        HashSet<Tag> tags = new HashSet<>(Queries.TagTable.getAllStatus(TagStatus.ACCEPTED));
        tags.addAll(getLanguageTags(Global.getOnlyLanguage()));
        if (Global.removeAvoidedGalleries())
            tags.addAll(Queries.TagTable.getAllStatus(TagStatus.AVOIDED));
        tags.addAll(Queries.TagTable.getAllOnlineBlacklisted());
        return tags;
    }

    private static Set<Tag> getLanguageTags(Language onlyLanguage) {
        Set<Tag> tags = new HashSet<>();
        if (onlyLanguage == null) return tags;
        switch (onlyLanguage) {
            case ENGLISH:
                tags.add(Queries.TagTable.getTagById(SpecialTagIds.LANGUAGE_ENGLISH));
                break;
            case JAPANESE:
                tags.add(Queries.TagTable.getTagById(SpecialTagIds.LANGUAGE_JAPANESE));
                break;
            case CHINESE:
                tags.add(Queries.TagTable.getTagById(SpecialTagIds.LANGUAGE_CHINESE));
                break;
        }
        return tags;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (Objects.requireNonNullElse(sortType, SortType.RECENT_ALL_TIME).ordinal()));
        dest.writeByte((byte) (custom ? 1 : 0));
        dest.writeInt(page);
        dest.writeInt(pageCount);
        dest.writeInt(id);
        dest.writeString(query);
        dest.writeString(url);
        dest.writeByte(requestType.ordinal());
        if (galleries == null || galleries.isEmpty())
            dest.writeByte((byte) GenericGallery.Type.SIMPLE.ordinal());
        else dest.writeByte((byte) galleries.get(0).getType().ordinal());
        dest.writeTypedList(galleries);
        dest.writeTypedList(new ArrayList<>(tags));
        dest.writeParcelable(ranges, flags);
    }

    public String getSearchTitle() {
        //triggered only when in searchMode
        if (!query.isEmpty()) return query;
        String searchQuery = Uri.parse(url).getQueryParameter("query");
        return searchQuery == null ? "" : searchQuery;
    }

    public void initialize(Context context, InspectorResponse response) {
        this.response = response;
        this.context = new WeakReference<>(context);
    }

    public InspectorResponse getResponse() {
        return response;
    }

    public InspectorV3 cloneInspector(Context context, InspectorResponse response) {
        InspectorV3 inspectorV3 = new InspectorV3(context, response);
        inspectorV3.query = query;
        inspectorV3.url = url;
        inspectorV3.tags = tags;
        inspectorV3.requestType = requestType;
        inspectorV3.sortType = sortType;
        inspectorV3.pageCount = pageCount;
        inspectorV3.page = page;
        inspectorV3.id = id;
        inspectorV3.custom = custom;
        inspectorV3.ranges = ranges;
        return inspectorV3;
    }

    private void tryByAllPopular() {
        if (sortType != SortType.RECENT_ALL_TIME) {
            requestType = ApiRequestType.BYSEARCH;
            query = "-nclientv3";
        }
    }

    private void createUrl() {
        String query;
        try {
            query = this.query == null ? null : URLEncoder.encode(this.query, Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException ignore) {
            query = this.query;
        }
        StringBuilder builder = new StringBuilder(Utility.getBaseUrl()).append("api/v2/");
        if (requestType == ApiRequestType.BYALL) {
            builder.append("galleries?page=").append(page);
        } else if (requestType == ApiRequestType.RANDOM) {
            builder.append("galleries/random");
        } else if (requestType == ApiRequestType.RANDOM_FAVORITE) {
            builder.append("favorites/random");
        } else if (requestType == ApiRequestType.BYSINGLE) {
            builder.append("galleries/").append(id).append("?include=related,favorite");
        } else if (requestType == ApiRequestType.FAVORITE) {
            builder.append("favorites?page=").append(page);
            if (query != null && !query.isEmpty())
                builder.append("&q=").append(query);
        } else if (requestType == ApiRequestType.BYSEARCH || requestType == ApiRequestType.BYTAG) {
            url = ApiEndpoints.SEARCH.buildUrl(this.query, tags, ranges, page, sortType);
            LogUtility.d("WWW: " + getBookmarkURL());
            return;
        }
        url = builder.toString().replace(' ', '+');
        LogUtility.d("WWW: " + getBookmarkURL());
    }

    private String getBookmarkURL() {
        if (page < 2) return url;
        else return url.substring(0, url.lastIndexOf('=') + 1);
    }

    public boolean createDocument() throws IOException, ApiRateLimiter.RateLimitException {
        if (jsonResponse != null) return true;
        try (Response response = executeApiRequest(url)) {
            jsonResponse = Objects.requireNonNull(response.body()).string();
            return response.code() == HttpURLConnection.HTTP_OK;
        }
    }

    @NonNull
    private Response executeApiRequest(@NonNull String url) throws IOException, ApiRateLimiter.RateLimitException {
        Context context = Objects.requireNonNull(this.context.get());
        Request request = new Request.Builder().url(url).build();
        if (isSearchRequest()) return ApiEndpoints.SEARCH.execute(context, Global.getClient(context), query, tags, ranges, page, sortType);
        return executeDirectApiRequest(context, request);
    }

    @NonNull
    private Response executeDirectApiRequest(@NonNull Context context, @NonNull Request request) throws IOException {
        return Global.getClient(context).newCall(request).execute();
    }

    private boolean isSearchRequest() {
        return requestType == ApiRequestType.BYSEARCH || requestType == ApiRequestType.BYTAG;
    }

    public void parseDocument() throws IOException, InvalidResponseException {
        try {
            if (requestType.isSingle()) doSingleV2();
            else doSearchV2();
        } catch (JSONException e) {
            LogUtility.e("JSON parse error: " + e.getMessage(), e);
            throw new InvalidResponseException();
        }
        jsonResponse = null;
    }

    @Override
    public synchronized void start() {
        if (getState() != State.NEW) return;
        if (response.shouldStart(this))
            super.start();
    }

    @Override
    public void run() {
        LogUtility.d("Starting download: " + url);
        if (response != null) response.onStart();
        try {
            createDocument();
            parseDocument();
            if (response != null) {
                response.onSuccess(galleries);
            }
        } catch (Exception e) {
            if (response != null) response.onFailure(e);
        }
        if (response != null) response.onEnd();
        LogUtility.d("Finished download: " + url);
    }

    private void filterDocumentTags() {
        if (galleries == null || tags == null) return;
        ArrayList<SimpleGallery> galleryTag = new ArrayList<>(galleries.size());
        for (GenericGallery gal : galleries) {
            assert gal instanceof SimpleGallery;
            SimpleGallery gallery = (SimpleGallery) gal;
            if (gallery.hasTags(tags)) {
                galleryTag.add(gallery);
            }
        }
        galleries.clear();
        galleries.addAll(galleryTag);
    }

    /**
     * Parse single gallery from API v2 response.
     * For RANDOM, the response is just {"id": N}, so we fetch the full detail.
     */
    private void doSingleV2() throws IOException, JSONException {
        galleries = new ArrayList<>(1);
        JSONObject v2 = new JSONObject(jsonResponse);
        if (v2.has("error")) return;

        // Random endpoint returns only {"id": N} — fetch full gallery detail
        if (!v2.has("title") && v2.has("id")) {
            int galleryId = v2.getInt("id");
            String detailUrl = Utility.getBaseUrl() + "api/v2/galleries/" + galleryId + "?include=related,favorite";
            Context context = Objects.requireNonNull(this.context.get());
            try (Response resp = executeDirectApiRequest(context, new Request.Builder().url(detailUrl).build())) {
                String body = Objects.requireNonNull(resp.body()).string();
                if (resp.code() != HttpURLConnection.HTTP_OK) return;
                v2 = new JSONObject(body);
            }
        }

        // Handle related galleries
        List<SimpleGallery> relatedList = new ArrayList<>();
        JSONArray relatedArr = v2.optJSONArray("related");
        if (relatedArr != null) {
            for (int i = 0; i < relatedArr.length(); i++) {
                relatedList.add(SimpleGallery.fromV2ListItem(context.get(), relatedArr.getJSONObject(i)));
            }
        }

        boolean isFavorite = v2.optBoolean("is_favorited", false);

        Gallery gallery = new Gallery(context.get(), v2.toString(), relatedList, isFavorite);
        galleries.add(gallery);
    }

    /**
     * Parse search/list results from API v2 response.
     * v2 list items have: id, media_id, thumbnail, english_title, japanese_title, tag_ids, num_pages
     */
    private void doSearchV2() throws InvalidResponseException, JSONException {
        JSONObject json = new JSONObject(jsonResponse);
        if (!json.has("result"))
            throw new InvalidResponseException();
        JSONArray results = json.getJSONArray("result");
        galleries = new ArrayList<>(results.length());
        for (int i = 0; i < results.length(); i++) {
            galleries.add(SimpleGallery.fromV2ListItem(context.get(), results.getJSONObject(i)));
        }
        pageCount = json.optInt("num_pages", Math.max(1, page));
        if (Global.isExactTagMatch())
            filterDocumentTags();
    }

    public void setSortType(SortType sortType) {
        this.sortType = sortType;
        if (this.requestType == ApiRequestType.BYALL)
            tryByAllPopular();
        createUrl();
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
        createUrl();
    }

    public List<GenericGallery> getGalleries() {
        return galleries;
    }

    public String getUrl() {
        return url;
    }

    public ApiRequestType getRequestType() {
        return requestType;
    }

    public int getPageCount() {
        return pageCount;
    }

    public Tag getTag() {
        Tag t = null;
        if (tags == null) return null;
        for (Tag tt : tags) {
            if (tt.getType() != TagType.LANGUAGE)
                return tt;
            t = tt;
        }
        return t;
    }

    public static class InvalidResponseException extends Exception {
        public InvalidResponseException() {
            super();
        }
    }

    public interface InspectorResponse {
        boolean shouldStart(InspectorV3 inspector);

        void onSuccess(List<GenericGallery> galleries);

        void onFailure(Exception e);

        void onStart();

        void onEnd();
    }

    public static abstract class DefaultInspectorResponse implements InspectorResponse {
        @Override
        public boolean shouldStart(InspectorV3 inspector) {
            return true;
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onEnd() {
        }

        @Override
        public void onSuccess(List<GenericGallery> galleries) {
        }

        @Override
        public void onFailure(Exception e) {
            LogUtility.e(e.getLocalizedMessage(), e);
        }
    }
}
