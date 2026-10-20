package com.car.audioplayer.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.data.model.Rank;
import com.car.audioplayer.databinding.ItemRankCardBinding;
import com.car.audioplayer.player.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 榜单大卡片 Adapter（榜单页 / 首页榜单入口共用）。
 * 卡片只展示 榜单名/说明/封面；点击后凭 topId 拉取榜内歌曲（接口语义见 Rank 模型）。
 */
public class RankAdapter extends RecyclerView.Adapter<RankAdapter.VH> {

    /** 卡片点击回调：宿主凭 Rank.topId 进榜单详情 */
    public interface Listener {
        void onRankClick(Rank rank);
    }

    private final List<Rank> items = new ArrayList<>();
    private final Listener listener;

    public RankAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 全量替换数据并刷新（榜单数量固定且少） */
    public void setRanks(List<Rank> ranks) {
        items.clear();
        if (ranks != null) {
            items.addAll(ranks);
        }
        notifyDataSetChanged();
    }

    /** ViewBinding 填充卡片布局 */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRankCardBinding binding = ItemRankCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    /**
     * 卡片绑定：榜单名 + 说明 + 封面。
     * desc 为空回退默认文案"QQ 音乐官方榜"（官方榜接口常无简介）。
     */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Rank rank = items.get(position);
        holder.binding.tvName.setText(rank.name);
        holder.binding.tvDesc.setText(rank.desc == null || rank.desc.isEmpty() ? "QQ 音乐官方榜" : rank.desc);
        ImageLoader.get().load(holder.binding.ivCover, rank.pic);
        holder.itemView.setOnClickListener(v -> listener.onRankClick(rank));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 行容器：ViewBinding 持有卡片控件引用 */
    static class VH extends RecyclerView.ViewHolder {
        final ItemRankCardBinding binding;

        VH(ItemRankCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}