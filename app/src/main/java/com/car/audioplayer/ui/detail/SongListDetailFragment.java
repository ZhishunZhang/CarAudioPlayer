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

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.model.Playlist;
import com.car.audioplayer.data.repository.FavoritesRepository;
import com.car.audioplayer.databinding.FragmentDetailBinding;
import com.car.audioplayer.player.ImageLoader;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌单详情（双栏）：左歌曲列表 + 右封面/信息/播放全部/收藏歌单。
 */
public class SongListDetailFragment extends Fragment {

    private static final String ARG_DISSTID = "disstid";
    private static final String ARG_NAME = "name";
    private static final String ARG_COVER = "cover";

    private FragmentDetailBinding binding;
    private SongAdapter adapter;
    private final List<OnlineSong> songs = new ArrayList<>();
    private Playlist playlist;
    private boolean favorite = false;

    /**
     * 工厂方法：只经 Bundle 传 id/name/cover 三个轻量字段，抗 Fragment 重建；
     * 歌单全量歌曲在详情页内重新请求。
     */
    public static SongListDetailFragment newInstance(Playlist playlist) {
        SongListDetailFragment fragment = new SongListDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DISSTID, playlist.disstid);
        args.putString(ARG_NAME, playlist.name);
        args.putString(ARG_COVER, playlist.cover);
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
        FavoritesRepository favorites = FavoritesRepository.get(requireContext());

        Bundle args = getArguments();
        String disstid = args == null ? "" : args.getString(ARG_DISSTID, "");
        playlist = new Playlist(disstid,
                args == null ? "" : args.getString(ARG_NAME, ""),
                args == null ? "" : args.getString(ARG_COVER, ""), 0);

        binding.tvTitle.setText(playlist.name.isEmpty() ? "歌单详情" : playlist.name);
        if (!playlist.cover.isEmpty()) {
            ImageLoader.get().load(binding.ivCover, playlist.cover);
        }
        binding.btnBack.setOnClickListener(v -> main.goBack());
        binding.tvExtra1.setVisibility(View.GONE);

        // 收藏歌单（本地，异步读/写库）
        binding.btnFav.setVisibility(View.VISIBLE);
        favorites.isPlaylistFavorite(disstid, fav -> {
            if (binding == null) {
                return;
            }
            favorite = fav;
            updateFavIcon(favorite);
        });
        binding.btnFav.setOnClickListener(v -> favorites.togglePlaylistFavorite(playlist, fav -> {
            if (binding == null) {
                return;
            }
            favorite = fav;
            updateFavIcon(favorite);
            main.showToast(favorite ? "已收藏歌单" : "已取消收藏");
        }));

        binding.rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SongAdapter((song, position) -> main.playAndOpenPlayer(songs, position), false);
        binding.rvSongs.setAdapter(adapter);

        binding.btnPlayAll.setOnClickListener(v -> {
            if (songs.isEmpty()) {
                main.showToast("歌单暂无歌曲");
                return;
            }
            playback.playQueue(songs, 0);
            main.showToast("开始播放：" + playlist.name);
        });

        // 换歌才发：不再随进度轮询空转
        playback.song().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                adapter.setCurrentMid(song.mid);
            }
        });

        DetailViewModel viewModel = new ViewModelProvider(this).get(DetailViewModel.class);
        viewModel.getSongListDetail().observe(getViewLifecycleOwner(), detail -> {
            songs.clear();
            songs.addAll(detail.songs);
            adapter.setSongs(songs);
            if (playlist.name.isEmpty() && !detail.name.isEmpty()) {
                playlist = new Playlist(disstid, detail.name, detail.cover == null ? "" : detail.cover, 0);
                binding.tvTitle.setText(detail.name);
            }
            if (!detail.cover.isEmpty()) {
                ImageLoader.get().load(binding.ivCover, detail.cover);
            }
            binding.tvDesc.setText(detail.desc.isEmpty() ? "暂无简介" : detail.desc);
        });
        viewModel.getHint().observe(getViewLifecycleOwner(),
                msg -> binding.tvHint.setText(msg == null ? "" : msg));
        viewModel.loadSongListDetail(disstid);
    }

    /** 更新“收藏歌单”按钮态：已收藏红色实心图标，未收藏灰色空心图标。 */
    private void updateFavIcon(boolean fav) {
        binding.btnFav.setImageResource(fav ? R.drawable.ic_fav : R.drawable.ic_fav_border);
        binding.btnFav.setColorFilter(fav
                ? requireContext().getColor(R.color.color_red)
                : requireContext().getColor(R.color.text_secondary));
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}