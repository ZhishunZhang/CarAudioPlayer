package com.car.audioplayer.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.data.model.Album;
import com.car.audioplayer.databinding.ItemAlbumCardBinding;
import com.car.audioplayer.player.ImageLoader;

import java.util.ArrayList;
import java.util.List;

import static com.car.audioplayer.data.remote.QQMusicApi.coverUrl;

/**
 * 专辑卡片 Adapter（歌手页专辑横向流）。
 * 封面没有独立 URL，统一由 albumMid 经 QQMusicApi.coverUrl 拼出。
 */
public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.VH> {

    /** 卡片点击回调：宿主凭 Album.albumMid 进专辑详情 */
    public interface Listener {
        void onAlbumClick(Album album);
    }

    private final List<Album> items = new ArrayList<>();
    private final Listener listener;

    public AlbumAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 全量替换数据并刷新 */
    public void setAlbums(List<Album> albums) {
        items.clear();
        if (albums != null) {
            items.addAll(albums);
        }
        notifyDataSetChanged();
    }

    /** ViewBinding 填充卡片布局 */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlbumCardBinding binding = ItemAlbumCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    /** 卡片绑定：专辑名 + 发行时间 + 封面（coverUrl 由 albumMid 拼接） */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Album album = items.get(position);
        holder.binding.tvName.setText(album.name);
        holder.binding.tvSub.setText(album.pubTime);
        ImageLoader.get().load(holder.binding.ivCover, coverUrl(album.albumMid));
        holder.itemView.setOnClickListener(v -> listener.onAlbumClick(album));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 行容器：ViewBinding 持有卡片控件引用 */
    static class VH extends RecyclerView.ViewHolder {
        final ItemAlbumCardBinding binding;

        VH(ItemAlbumCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}