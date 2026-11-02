package com.maxwai.nclientv3.adapters;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.HanimeCommunityGallery;
import com.maxwai.nclientv3.utility.ImageDownloadUtility;

import java.util.ArrayList;
import java.util.List;

public class HanimeCommunityAdapter extends RecyclerView.Adapter<HanimeCommunityAdapter.ViewHolder> {
    private final Context context;
    private final List<HanimeCommunityGallery> items = new ArrayList<>();

    public HanimeCommunityAdapter(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.entry_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HanimeCommunityGallery item = items.get(position);
        ImageDownloadUtility.loadImage(context, item.getImageUri(), holder.image);
        holder.title.setText(item.getTitle());
        holder.pages.setVisibility(View.GONE);
        holder.flag.setVisibility(View.GONE);
        holder.overlay.setVisibility(View.GONE);
        holder.layout.setOnClickListener(v -> showActions(item));
    }

    private void showActions(HanimeCommunityGallery item) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(item.getTitle())
            .setItems(new CharSequence[]{context.getString(R.string.open_image), context.getString(R.string.download_image), context.getString(R.string.copy_image_link)}, (dialog, which) -> {
                if (which == 0) {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, item.getImageUri()));
                } else if (which == 1) {
                    download(item);
                } else {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Hanime image", item.getImageUrl()));
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .show();
    }

    private void download(HanimeCommunityGallery item) {
        String fileName = "hanime_community_" + item.getId() + ".webp";
        DownloadManager.Request request = new DownloadManager.Request(item.getImageUri())
            .setTitle(fileName)
            .setDescription(context.getString(R.string.download_image))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "NClientV3/" + fileName);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void add(List<HanimeCommunityGallery> galleries) {
        int start = items.size();
        items.addAll(galleries);
        notifyItemRangeInserted(start, galleries.size());
    }

    public void reset(List<HanimeCommunityGallery> galleries) {
        items.clear();
        items.addAll(galleries);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, pages, flag;
        final View layout, overlay;

        ViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
            title = itemView.findViewById(R.id.title);
            pages = itemView.findViewById(R.id.pages);
            flag = itemView.findViewById(R.id.flag);
            layout = itemView.findViewById(R.id.master_layout);
            overlay = itemView.findViewById(R.id.overlay);
        }
    }
}
