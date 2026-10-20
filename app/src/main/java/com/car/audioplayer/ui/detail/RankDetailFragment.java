package com.car.audioplayer.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.model.Rank;
import com.car.audioplayer.databinding.FragmentDetailBinding;
import com.car.audioplayer.player.ImageLoader;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 榜单详情（双栏）：左榜内歌曲 + 右榜单信息/播放全部。
 */
public class RankDetailFragment extends Fragment {

    private static final String ARG_TOP_ID = "top_id";
    private static final String ARG_NAME = "name";
    private static final String ARG_PIC = "pic";
    private static final String ARG_DESC = "desc";

    private FragmentDetailBinding binding;
    private SongAdapter adapter;
    private final List<OnlineSong> songs = new ArrayList<>();

    /** 工厂方法：榜单 id/名称/封面/描述经 Bundle 传递，抗 Fragment 重建。 */
    public static RankDetailFragment newInstance(Rank rank) {
        RankDetailFragment fragment = new RankDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TOP_ID, rank.topId);
        args.putString(ARG_NAME, rank.name);
        args.putString(ARG_PIC, rank.pic);
        args.putString(ARG_DESC, rank.desc);
        fragment.setArguments(args);
        return fragment;
    }

    /** ViewBinding 充气详情布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();
        PlaybackManager playback = PlaybackManager.get(requireContext());

        Bundle args = getArguments();
        String topId = args == null ? "" : args.getString(ARG_TOP_ID, "");
        String name = args == null ? "" : args.getString(ARG_NAME, "榜单");
        String pic = args == null ? "" : args.getString(ARG_PIC, "");
        String desc = args == null ? "" : args.getString(ARG_DESC, "");

        binding.tvTitle.setText(name);
        binding.tvName.setText(name);
        binding.tvDesc.setText(desc.isEmpty() ? "QQ 音乐官方榜" : desc);
        if (!pic.isEmpty()) {
            ImageLoader.get().load(binding.ivCover, pic);
        }
        binding.btnBack.setOnClickListener(v -> main.goBack());
        binding.btnFav.setVisibility(View.GONE);
        binding.tvExtra1.setVisibility(View.GONE);

        binding.rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SongAdapter((song, position) -> main.playAndOpenPlayer(songs, position), false);
        binding.rvSongs.setAdapter(adapter);

        binding.btnPlayAll.setOnClickListener(v -> {
            if (songs.isEmpty()) {
                main.showToast("榜单暂无歌曲");
                return;
            }
            playback.playQueue(songs, 0);
        });

        // 换歌才发：不再随进度轮询空转
        playback.song().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                adapter.setCurrentMid(song.mid);
            }
        });

        DetailViewModel viewModel = new ViewModelProvider(this).get(DetailViewModel.class);
        viewModel.loadRank(topId);
        viewModel.getRankSongs().observe(getViewLifecycleOwner(), list -> {
            songs.clear();
            songs.addAll(list);
            adapter.setSongs(songs);
        });
        viewModel.getHint().observe(getViewLifecycleOwner(),
                msg -> binding.tvHint.setText(msg == null ? "" : msg));
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
