package com.car.audioplayer.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.data.model.Banner;
import com.car.audioplayer.data.model.Album;
import com.car.audioplayer.databinding.ItemAlbumCardBinding;
import com.car.audioplayer.databinding.ItemBannerBinding;
import com.car.audioplayer.player.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 Banner 轮播 Adapter（配合 PagerSnapHelper 逐页吸附滚动）。
 * 点击后的跳转分发（jumpType=1 跳歌单）由宿主 HomeFragment 处理。
 */
public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

    /** 轮播项点击回调：宿主按 jumpType/jumpId 决定跳转目标 */
    public interface Listener {
        void onBannerClick(Banner banner);
    }

    private final List<Banner> items = new ArrayList<>();
    private final Listener listener;

    public BannerAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 全量替换数据并刷新（轮播位固定几张） */
    public void setBanners(List<Banner> banners) {
        items.clear();
        if (banners != null) {
            items.addAll(banners);
        }
        notifyDataSetChanged();
    }

    /** ViewBinding 填充轮播布局 */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBannerBinding binding = ItemBannerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    /** 轮播项绑定：封面 + 主标题 + 副标题 */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Banner banner = items.get(position);
        ImageLoader.get().load(holder.binding.ivCover, banner.cover);
        // 上游可能不给标题/副标题：都为空时隐藏整条信息条，避免底部空黑条
        boolean hasText = (banner.title != null && !banner.title.isEmpty())
                || (banner.subTitle != null && !banner.subTitle.isEmpty());
        holder.binding.lyBannerInfo.setVisibility(hasText ? View.VISIBLE : View.GONE);
        holder.binding.tvTitle.setText(banner.title);
        holder.binding.tvSubtitle.setText(banner.subTitle);
        holder.itemView.setOnClickListener(v -> listener.onBannerClick(banner));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 行容器：ViewBinding 持有轮播控件引用 */
    static class VH extends RecyclerView.ViewHolder {
        final ItemBannerBinding binding;

        VH(ItemBannerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}