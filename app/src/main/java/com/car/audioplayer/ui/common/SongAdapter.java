package com.car.audioplayer.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.databinding.ItemSongRichBinding;
import com.car.audioplayer.player.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.car.audioplayer.data.remote.QQMusicApi.coverUrl;

/**
 * 通用歌曲行 Adapter：搜索/歌单/榜单/专辑/歌手/收藏列表共用。
 * 支持序号、收藏按钮（含已收藏状态）与当前播放高亮。
 * ListAdapter + DiffUtil：setSongs 走异步 diff 局部刷新，不再全量刷新。
 */
public class SongAdapter extends ListAdapter<OnlineSong, SongAdapter.VH> {

    /** 行交互回调：onSongClick 整行点击播放；onFavClick 收藏星标；onSingerClick 歌手名跳转（带默认实现，按需覆写） */
    public interface Listener {
        /** 整行点击：position 为绑定时捕获的位置，宿主用它确定播放起点 */
        void onSongClick(OnlineSong song, int position);

        /** 收藏星标点击（默认空实现，无收藏按钮的列表无需关心） */
        default void onFavClick(OnlineSong song, int position) {
        }

        /** 点击歌手名跳歌手页（默认不响应） */
        default void onSingerClick(OnlineSong song) {
        }
    }

    private final Listener listener;
    private final boolean showFav;
    private final boolean singerClickable;
    /** 当前播放歌曲 MID：与行 mid 相等时歌名高亮，空串表示无播放 */
    private String currentMid = "";
    /** 行首序号基数：榜单分页时从上一页末尾接续编号 */
    private int startIndex = 1;

    /** 已收藏歌曲 mid 集合（外部一次性批量注入，绑定时零查库） */
    private Set<String> favoriteMids = Collections.emptySet();

    /** 简化构造：歌手名不可点击（搜索页以外的常规列表用） */
    public SongAdapter(Listener listener, boolean showFav) {
        this(listener, showFav, false);
    }

    /** singerClickable=true 时歌手名可点击跳歌手页（搜索结果页用） */
    public SongAdapter(Listener listener, boolean showFav, boolean singerClickable) {
        super(new DiffUtil.ItemCallback<OnlineSong>() {
            @Override
            public boolean areItemsTheSame(@NonNull OnlineSong a, @NonNull OnlineSong b) {
                // 榜单两段式加载时 mid 可能暂为空，回退比 name+singer
                if (a.mid != null && !a.mid.isEmpty() && b.mid != null && !b.mid.isEmpty()) {
                    return a.mid.equals(b.mid);
                }
                return java.util.Objects.equals(a.name, b.name)
                        && java.util.Objects.equals(a.singer, b.singer);
            }

            // 内容级比对：只比影响行内展示的字段（mid/歌名/歌手/封面/时长/VIP），全同则免重绑
            @Override
            public boolean areContentsTheSame(@NonNull OnlineSong a, @NonNull OnlineSong b) {
                return java.util.Objects.equals(a.mid, b.mid)
                        && java.util.Objects.equals(a.name, b.name)
                        && java.util.Objects.equals(a.singer, b.singer)
                        && java.util.Objects.equals(a.albumMid, b.albumMid)
                        && a.interval == b.interval
                        && a.payPlay == b.payPlay;
            }
        });
        this.listener = listener;
        this.showFav = showFav;
        this.singerClickable = singerClickable;
    }

    /** 提交新列表：拷贝后交 ListAdapter 异步 diff，仅变化的行局部刷新 */
    public void setSongs(List<OnlineSong> songs) {
        // 必须拷贝：调用方会复用并原地修改同一个 list 实例，不拷贝 DiffUtil 检测不到变化
        submitList(songs == null ? Collections.emptyList() : new ArrayList<>(songs));
    }

    /** 批量注入已收藏 mid 集合，替代绑定时逐行查库 */
    public void setFavoriteMids(Set<String> mids) {
        this.favoriteMids = mids == null ? Collections.emptySet() : mids;
    }

    /**
     * 更新当前播放歌曲，刷新歌名高亮。
     * mid 去重：与当前值相同则什么都不做，避免同一首歌反复触发全表重绘；
     * null 先归一为空串再比较，杜绝空指针。
     * 高亮行位置不可预知，故直接 notifyDataSetChanged（列表通常不满一屏，代价可接受）。
     */
    public void setCurrentMid(String mid) {
        if (mid == null) {
            mid = "";
        }
        if (!mid.equals(currentMid)) {
            currentMid = mid;
            notifyDataSetChanged();
        }
    }

    /** 列表从第几首开始编号（榜单常用 1 起） */
    public void setStartIndex(int start) {
        this.startIndex = start;
    }

    /** ViewBinding 填充行布局，单 viewType 无分支 */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongRichBinding binding = ItemSongRichBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    /**
     * 行绑定：从 ListAdapter 快照取数据，依次填序号/歌名/歌手/时长/封面/收藏星标。
     * 歌名按 currentMid 判定高亮；歌手名仅 singerClickable 时着色并挂点击。
     * 收藏态查内存 Set（favoriteMids），绑定时零查库。
     */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        OnlineSong song = getItem(position);
        holder.binding.tvIndex.setText(String.valueOf(position + startIndex));
        holder.binding.tvName.setText(song.name);
        holder.binding.tvSinger.setText(song.singer == null || song.singer.isEmpty()
                ? holder.itemView.getContext().getString(R.string.unknown_singer) : song.singer);
        if (singerClickable && song.hasSinger()) {
            holder.binding.tvSinger.setTextColor(
                    holder.itemView.getContext().getColor(R.color.playing_tint));
            holder.binding.tvSinger.setOnClickListener(v -> listener.onSingerClick(song));
        } else {
            holder.binding.tvSinger.setOnClickListener(null);
        }
        holder.binding.tvDuration.setText(song.interval > 0 ? formatTime(song.interval) : "");
        // 当前播放行歌名高亮，其余恢复默认色
        holder.binding.tvName.setTextColor(
                song.mid.equals(currentMid)
                        ? holder.itemView.getContext().getColor(R.color.playing_tint)
                        : holder.itemView.getContext().getColor(R.color.text_primary));

        String cover = coverUrl(song.albumMid);
        // 统一封面加载：内存/磁盘/网络三级缓存 + 降采样 + tag 防滚动错位 + 淡入
        ImageLoader.get().load(holder.binding.ivCover, cover);

        // VIP 角标：仅付费歌曲显示（榜单数据源无 pay 字段，不显示，点击时 Toast 兜底）
        holder.binding.tvVip.setVisibility(song.payPlay ? android.view.View.VISIBLE : android.view.View.GONE);

        // 绑定时捕获位置：列表刷新后 getBindingAdapterPosition() 可能为 -1，
        // 在点击回调里再读会越界或播错歌
        final int pos = position;
        if (showFav) {
            holder.binding.ivFav.setVisibility(android.view.View.VISIBLE);
            boolean fav = favoriteMids.contains(song.mid);
            holder.binding.ivFav.setImageResource(fav ? R.drawable.ic_fav : R.drawable.ic_fav_border);
            holder.binding.ivFav.setColorFilter(holder.itemView.getContext().getColor(
                    fav ? R.color.color_red : R.color.text_secondary));
            holder.binding.ivFav.setOnClickListener(v -> listener.onFavClick(song, pos));
        } else {
            holder.binding.ivFav.setVisibility(android.view.View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onSongClick(song, pos));
    }

    /** 秒数格式化为 "mm:ss"（全 App 共用的时长展示格式） */
    public static String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format(Locale.CHINA, "%02d:%02d", m, s);
    }

    /** 行容器：ViewBinding 持有全部控件引用，复用时免 findViewById */
    static class VH extends RecyclerView.ViewHolder {
        final ItemSongRichBinding binding;

        VH(ItemSongRichBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}