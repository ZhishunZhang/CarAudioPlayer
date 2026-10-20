package com.car.audioplayer.ui.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.databinding.FragmentSearchResultBinding;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.List;

/**
 * 搜索结果页：展示关键词的歌曲结果，点击直接播放。
 */
public class SearchResultFragment extends Fragment {

    private static final String ARG_KEYWORD = "keyword";

    private FragmentSearchResultBinding binding;
    private SongAdapter adapter;

    /**
     * 工厂方法：关键词经 Bundle 保存，保证 Fragment 被系统重建（如进程恢复）时搜索词仍然可用。
     */
    public static SearchResultFragment newInstance(String keyword) {
        SearchResultFragment fragment = new SearchResultFragment();
        Bundle args = new Bundle();
        args.putString(ARG_KEYWORD, keyword);
        fragment.setArguments(args);
        return fragment;
    }

    /** ViewBinding 充气搜索结果页布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchResultBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();
        PlaybackManager playback = PlaybackManager.get(requireContext());

        String keyword = getArguments() == null ? "" : getArguments().getString(ARG_KEYWORD, "");
        binding.tvTitle.setText(getString(R.string.search_title_fmt, keyword));
        binding.btnBack.setOnClickListener(v -> main.goBack());

        binding.rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SongAdapter(new SongAdapter.Listener() {
            @Override
            public void onSongClick(OnlineSong song, int position) {
                main.playAndOpenPlayer(resultList, position);
            }

            @Override
            public void onSingerClick(OnlineSong song) {
                main.openSingerDetail(song.singerMid, song.singer);
            }
        }, false, true);
        binding.rvSongs.setAdapter(adapter);

        // 换歌才发：不再随进度轮询空转
        playback.song().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                adapter.setCurrentMid(song.mid);
            }
        });

        SearchResultViewModel viewModel = new ViewModelProvider(this).get(SearchResultViewModel.class);
        viewModel.search(keyword);
        viewModel.getResults().observe(getViewLifecycleOwner(), songs -> {
            resultList.clear();
            resultList.addAll(songs);
            adapter.setSongs(resultList);
        });
        viewModel.getHint().observe(getViewLifecycleOwner(),
                msg -> binding.tvHint.setText(msg == null ? "" : msg));
    }

    // 搜索结果缓存：稳定列表引用，适配器与 playAndOpenPlayer 共用
    private final List<OnlineSong> resultList = new java.util.ArrayList<>();

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
