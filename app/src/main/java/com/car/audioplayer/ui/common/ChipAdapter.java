package com.car.audioplayer.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.databinding.ItemChipBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用标签 Adapter：歌单分类 / 热搜词横条共用。
 * 选中态由 selected 下标驱动：选中/未选中两套背景与文字色，setSelected 后整表刷新。
 */
public class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.VH> {

    /** 标签点击回调：text 为标签文本，position 为其下标 */
    public interface Listener {
        void onChipClick(String text, int position);
    }

    private final List<String> items = new ArrayList<>();
    private final Listener listener;
    /** 当前选中下标（默认选中第 0 项） */
    private int selected = 0;

    public ChipAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 全量替换标签并把选中态重置回首项（换一栏分类后原选中位置已无意义） */
    public void setItems(List<String> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        selected = 0;
        notifyDataSetChanged();
    }

    /**
     * 切换选中项：相同下标或越界直接忽略，不做无谓刷新。
     * 选中/未选中的背景与文字色在 onBindViewHolder 按 position==selected 套用。
     */
    public void setSelected(int position) {
        if (position != selected && position >= 0 && position < items.size()) {
            selected = position;
            notifyDataSetChanged();
        }
    }

    /** ViewBinding 填充标签布局 */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChipBinding binding = ItemChipBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    /** 标签绑定：按选中态切换背景与文字色（点击位置的坑见下方行内注释） */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final String text = items.get(position);
        holder.binding.tvChip.setText(text);
        boolean isSelected = position == selected;
        holder.binding.tvChip.setBackgroundResource(isSelected
                ? com.car.audioplayer.R.drawable.bg_chip_selected
                : com.car.audioplayer.R.drawable.bg_chip);
        holder.binding.tvChip.setTextColor(holder.itemView.getContext().getColor(isSelected
                ? com.car.audioplayer.R.color.black
                : com.car.audioplayer.R.color.text_primary));
        // 绑定时捕获文本与位置：setSelected 会触发 notifyDataSetChanged，
        // 点击时再读 getBindingAdapterPosition() 会拿到 -1 导致越界崩溃
        holder.itemView.setOnClickListener(v -> listener.onChipClick(text, position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 行容器：ViewBinding 持有标签控件引用 */
    static class VH extends RecyclerView.ViewHolder {
        final ItemChipBinding binding;

        VH(ItemChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}