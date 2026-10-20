package com.car.audioplayer.ui.player;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.LyricLine;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌词列表适配器：当前行放大加粗高亮，其余行弱化。
 * 行点击回调给宿主（浏览态=seek 跳转到该行，跟随态=切回封面模式）。
 * 浏览/跟随两态与 6 秒自动回滚的状态机由 PlayerFragment 驱动，本类只负责展示与点击上报。
 */
public class LyricAdapter extends RecyclerView.Adapter<LyricAdapter.LineHolder> {

    /** 歌词行点击 */
    public interface Listener {
        void onLineClick(int position);
    }

    /** 歌词行数据（刷新靠手动 notify 管理，未用 DiffUtil） */
    private final List<LyricLine> lines = new ArrayList<>();
    private final Listener listener;
    /** 当前高亮行下标，-1 表示尚无高亮（切歌/重载后归位） */
    private int currentLine = -1;

    public LyricAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 替换整表歌词（切歌/加载完成），高亮交由宿主用 setCurrentLine 驱动 */
    public void submitLines(List<LyricLine> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        currentLine = -1;
        notifyDataSetChanged();
    }

    /** 更新当前高亮行（增量刷新新旧两行，避免整表刷新闪烁） */
    public void setCurrentLine(int position) {
        if (position == currentLine) {
            return;
        }
        int old = currentLine;
        currentLine = position;
        if (old >= 0 && old < lines.size()) {
            notifyItemChanged(old);
        }
        if (currentLine >= 0 && currentLine < lines.size()) {
            notifyItemChanged(currentLine);
        }
    }

    /** 填充单行歌词布局 */
    @NonNull
    @Override
    public LineHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new LineHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lyric, parent, false));
    }

    /**
     * 行绑定：当前行 26sp 加粗 + 高亮色，其余行 20sp 常规 + 弱化灰，形成"当前行放大"观感。
     * 高亮切换只经 setCurrentLine 增量刷新新旧两行，其余行不重绑，避免整表闪烁。
     * 空文本行用省略号占位。
     */
    @Override
    public void onBindViewHolder(@NonNull LineHolder holder, int position) {
        LyricLine line = lines.get(position);
        boolean current = position == currentLine;
        TextView text = holder.text;
        text.setText(line.text == null || line.text.isEmpty() ? "…" : line.text);
        text.setTextSize(current ? 26f : 20f);
        text.setTypeface(Typeface.DEFAULT, current ? Typeface.BOLD : Typeface.NORMAL);
        text.setTextColor(ContextCompat.getColor(text.getContext(),
                current ? R.color.playing_tint : R.color.text_hint));
        // 与 SongAdapter 的"绑定时捕获位置"不同：这里点击瞬间实时取 position，
        // 再用 NO_POSITION 过滤刷新间隙的失效点击
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onLineClick(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    /** 行容器：单 TextView，构造时 findViewById 一次后复用 */
    static class LineHolder extends RecyclerView.ViewHolder {
        final TextView text;

        LineHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tv_lyric_line);
        }
    }
}