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
import com.car.audioplayer.databinding.FragmentSingerDetailBinding;
import com.car.audioplayer.player.ImageLoader;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.AlbumAdapter;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

import static com.car.audioplayer.data.remote.QQMusicApi.singerAvatarUrl;

/**
 * 歌手详情（双栏）：左热门歌曲 + 右头像/简介/专辑流/播放热门。
 */
public class SingerDetailFragment extends Fragment {

    private static final String ARG_MID = "singer_mid";
    private static final String ARG_NAME = "singer_name";

    private FragmentSingerDetailBinding binding;
    private SongAdapter songAdapter;
    private AlbumAdapter albumAdapter;
    private final List<OnlineSong> songs = new ArrayList<>();
    private String singerMid = "";
    private String singerName = "";

    /** 工厂方法：歌手 mid/名称经 Bundle 传递，抗 Fragment 重建。 */
    public static SingerDetailFragment newInstance(String singerMid, String singerName) {
        SingerDetailFragment fragment = new SingerDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MID, singerMid);
        args.putString(ARG_NAME, singerName);
        fragment.setArguments(args);
        return fragment;
    }

    /** ViewBinding 充气歌手详情布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSingerDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();
        PlaybackManager playback = PlaybackManager.get(requireContext());

        Bundle args = getArguments();
        singerMid = args == null ? "" : args.getString(ARG_MID, "");
        singerName = args == null ? "" : args.getString(ARG_NAME, "歌手");

        binding.tvTitle.setText(singerName);
        binding.tvName.setText(singerName);
        binding.btnBack.setOnClickListener(v -> main.goBack());
        // 头像：XML 的 android:tint（给灰色占位人形用的）会连后来加载的真人照片一起染灰，
        // 加载前先清 tint 和 padding（占位图由 ImageLoader 自己提供，不需要 tint）
        if (!singerMid.isEmpty()) {
            binding.ivAvatar.setImageTintList(null);
            binding.ivAvatar.setPadding(0, 0, 0, 0);
        }
        ImageLoader.get().load(binding.ivAvatar, singerAvatarUrl(singerMid));

        binding.rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        songAdapter = new SongAdapter((song, position) -> main.playAndOpenPlayer(songs, position), false);
        binding.rvSongs.setAdapter(songAdapter);

        binding.rvAlbums.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        albumAdapter = new AlbumAdapter(main::openAlbumDetail);
        binding.rvAlbums.setAdapter(albumAdapter);

        binding.btnPlayAll.setOnClickListener(v -> {
            if (songs.isEmpty()) {
                main.showToast("暂无热门歌曲数据");
                return;
            }
            playback.playQueue(songs, 0);
        });

        // 换歌才发：不再随进度轮询空转
        playback.song().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                songAdapter.setCurrentMid(song.mid);
            }
        });

        DetailViewModel viewModel = new ViewModelProvider(this).get(DetailViewModel.class);

        // 热门歌曲
        viewModel.getSingerSongs().observe(getViewLifecycleOwner(), list -> {
            songs.clear();
            songs.addAll(list);
            songAdapter.setSongs(songs);
        });

        // 专辑流
        viewModel.getSingerAlbums().observe(getViewLifecycleOwner(), albums -> {
            albumAdapter.setAlbums(albums);
            binding.rvAlbums.setVisibility(albums.isEmpty() ? View.GONE : View.VISIBLE);
        });

        // 简介
        viewModel.getSingerDesc().observe(getViewLifecycleOwner(),
                desc -> binding.tvDesc.setText(desc == null || desc.isEmpty() ? "暂无歌手简介" : desc));

        viewModel.getHint().observe(getViewLifecycleOwner(),
                msg -> binding.tvHint.setText(msg == null ? "" : msg));

        viewModel.loadSingerHotsong(singerMid);
        viewModel.loadSingerAlbums(singerMid);
        viewModel.loadSingerDesc(singerMid);
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}