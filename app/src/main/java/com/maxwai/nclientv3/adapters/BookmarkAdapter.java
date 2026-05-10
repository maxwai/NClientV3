package com.maxwai.nclientv3.adapters;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.BookmarkActivity;
import com.maxwai.nclientv3.MainActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.classes.Bookmark;
import com.maxwai.nclientv3.utility.IntentUtility;

import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {
    private static final int LAYOUT = R.layout.bookmark_layout;

    private final List<Bookmark> bookmarks;
    private final BookmarkActivity bookmarkActivity;

    public BookmarkAdapter(BookmarkActivity bookmarkActivity) {
        this.bookmarkActivity = bookmarkActivity;
        this.bookmarks = Queries.BookmarkTable.getBookmarks();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(LAYOUT, parent, false));
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
        final int position = holder.getBindingAdapterPosition();
        Bookmark bookmark = bookmarks.get(position);

        holder.queryText.setText(bookmark.toString());
        holder.pageLabel.setText(bookmarkActivity.getString(R.string.bookmark_page_format, bookmark.page));

        holder.deleteButton.setOnClickListener(v -> removeBookmarkAtPosition(position));
        holder.subscriptionButton.setVisibility(bookmark.isSubscribable() ? View.VISIBLE : View.GONE);
        holder.subscriptionButton.setImageResource(bookmark.isSubscribed() ? R.drawable.ic_star : R.drawable.ic_star_border);
        holder.subscriptionButton.setOnClickListener(v -> {
            bookmark.setSubscribed(!bookmark.isSubscribed());
            notifyItemChanged(position);
        });

        holder.rootLayout.setOnClickListener(v -> loadBookmark(bookmark));
    }

    /**
     * Start an {@link MainActivity} with <code>bookmark</code> as query and page
     *
     * @param bookmark bookmark to load
     */
    private void loadBookmark(Bookmark bookmark) {
        Intent i = new Intent(bookmarkActivity, MainActivity.class);
        i.putExtra(bookmarkActivity.getPackageName() + ".BYBOOKMARK", true);
        i.putExtra(bookmarkActivity.getPackageName() + ".INSPECTOR", bookmark.createInspector(bookmarkActivity, null));
        IntentUtility.startAnotherActivity(bookmarkActivity, i);
    }

    /**
     * remove bookmark from the adapter at <code>position</code>
     *
     * @param position index to delete
     */
    private void removeBookmarkAtPosition(int position) {
        if (position >= bookmarks.size()) return;
        Bookmark bookmark = bookmarks.get(position);
        bookmark.deleteBookmark();
        bookmarks.remove(bookmark);
        bookmarkActivity.runOnUiThread(() -> notifyItemRemoved(position));
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final AppCompatImageButton deleteButton;
        final AppCompatImageButton subscriptionButton;
        final TextView queryText;
        final TextView pageLabel;
        final ConstraintLayout rootLayout;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            deleteButton = itemView.findViewById(R.id.remove_button);
            subscriptionButton = itemView.findViewById(R.id.subscription_button);
            pageLabel = itemView.findViewById(R.id.page);
            queryText = itemView.findViewById(R.id.title);
            rootLayout = itemView.findViewById(R.id.master_layout);
        }
    }

}
