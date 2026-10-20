package com.car.audioplayer.ui.player;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.databinding.DialogQueuePanelBinding;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.SongAdapter;

import java.util.List;

/**
 * 播放队列面板：从播放页右侧滑出，点行切歌，支持清空。
 */
public class QueuePanelFragment extends DialogFragment {

    private DialogQueuePanelBinding binding;
    private PlaybackManager playback;
    private SongAdapter adapter;

    /** ViewBinding 充气播放队列面板布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogQueuePanelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        playback = PlaybackManager.get(requireContext());

        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnClear.setOnClickListener(v -> {
            playback.clearQueue();
            refreshQueue();
        });

        binding.rvQueue.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SongAdapter((song, position) -> {
            playback.playAt(position);
            dismiss();
        }, false);
        binding.rvQueue.setAdapter(adapter);

        refreshQueue();

        // 换歌才发：不再随进度轮询空转
        playback.song().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                adapter.setCurrentMid(song.mid);
            }
        });
    }

    /**
     * 拉取一次队列快照并刷新列表 + 当前歌曲高亮。
     * 全部读取是 PlaybackManager 同步快照（queue()/getValue()），不像 song() 流会自动刷新，
     * 因此清空队列、重新打开面板等动作后都要重新调用。
     */
    private void refreshQueue() {
        List<OnlineSong> queue = playback.queue();
        adapter.setSongs(queue);
        OnlineSong current = playback.song().getValue();
        if (current != null) {
            adapter.setCurrentMid(current.mid);
        }
    }

    /** 对话框展示阶段：撑满全屏并把窗口背景设为透明，让面板布局自身的背景露出（白底会挡住它）。 */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}