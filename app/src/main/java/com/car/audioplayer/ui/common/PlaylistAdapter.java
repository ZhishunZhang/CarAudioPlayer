package com.car.audioplayer.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.data.model.Playlist;
import com.car.audioplayer.databinding.ItemPlaylistCardBinding;
import com.car.audioplayer.player.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌单大卡片 Adapter：歌单广场网格 / 首页推荐横滑共用。
 * 数据流：VM 拉取歌单列表 → setPlaylists 全量刷新（卡片数量少，未用 DiffUtil）。
 */
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {

    /** 卡片点击回调：宿主凭 Playlist.disstid 跳歌单详情 */
    public interface Listener {
        void onPlaylistClick(Playlist playlist);
    }

    private final List<Playlist> items = new ArrayList<>();
    private final Listener listener;

    public PlaylistAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 全量替换数据并 notifyDataSetChanged（列表短，无需 DiffUtil） */
    public void setPlaylists(List<Playlist> playlists) {
        items.clear();
        if (playlists != null) {
            items.addAll(playlists);
        }
        notifyDataSetChanged();
    }

    /** ViewBinding 填充卡片布局 */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlaylistCardBinding binding = ItemPlaylistCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    /**
     * 卡片绑定：歌名 + 播放量 + 封面。
     * 播放量 listenCount>0 才展示，formatCount 归一为 x.x亿/x.x万 的中文习惯格式。
     */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Playlist playlist = items.get(position);
        holder.binding.tvName.setText(playlist.name);
        holder.binding.tvCount.setText(playlist.listenCount > 0
                ? "播放 " + formatCount(playlist.listenCount) : "");
        ImageLoader.get().load(holder.binding.ivCover, playlist.cover);
        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 播放量格式化：1.2亿 / 320万 */
    public static String formatCount(long count) {
        if (count >= 100000000) {
            return String.format(java.util.Locale.CHINA, "%.1f亿", count / 100000000.0);
        }
        if (count >= 10000) {
            return String.format(java.util.Locale.CHINA, "%.1f万", count / 10000.0);
        }
        return String.valueOf(count);
    }

    /** 行容器：ViewBinding 持有卡片控件引用 */
    static class VH extends RecyclerView.ViewHolder {
        final ItemPlaylistCardBinding binding;

        VH(ItemPlaylistCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}