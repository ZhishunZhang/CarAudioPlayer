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
import com.car.audioplayer.data.model.Album;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.databinding.FragmentDetailBinding;
import com.car.audioplayer.player.ImageLoader;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

import static com.car.audioplayer.data.remote.QQMusicApi.coverUrl;

/**
 * 专辑详情（双栏）：左专辑歌曲 + 右专辑信息/播放全部/歌手入口。
 */
public class AlbumDetailFragment extends Fragment {

    private static final String ARG_MID = "album_mid";
    private static final String ARG_NAME = "name";
    private static final String ARG_SINGER = "singer";
    private static final String ARG_SINGER_MID = "singer_mid";

    private FragmentDetailBinding binding;
    private SongAdapter adapter;
    private final List<OnlineSong> songs = new ArrayList<>();

    protected String albumMid = "";
    protected String singerMid = "";
    protected String singerName = "";
    protected String albumName = "";

    /** 工厂方法：专辑 id/名称/歌手信息经 Bundle 传递，抗 Fragment 重建。 */
    public static AlbumDetailFragment newInstance(Album album) {
        AlbumDetailFragment fragment = new AlbumDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MID, album.albumMid);
        args.putString(ARG_NAME, album.name);
        args.putString(ARG_SINGER, album.singerName);
        args.putString(ARG_SINGER_MID, album.singerMid);
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
        albumMid = args == null ? "" : args.getString(ARG_MID, "");
        albumName = args == null ? "" : args.getString(ARG_NAME, "专辑");
        singerName = args == null ? "" : args.getString(ARG_SINGER, "");
        singerMid = args == null ? "" : args.getString(ARG_SINGER_MID, "");

        binding.tvTitle.setText(albumName);
        binding.tvName.setText(albumName);
        binding.btnBack.setOnClickListener(v -> main.goBack());
        binding.btnFav.setVisibility(View.GONE);
        ImageLoader.get().load(binding.ivCover, coverUrl(albumMid));
        binding.tvDesc.setText(getString(R.string.album_desc_fmt,
                singerName.isEmpty() ? getString(R.string.unknown_singer) : singerName));

        // 歌手入口：点击跳歌手页
        if (!singerMid.isEmpty()) {
            binding.tvExtra1.setVisibility(View.VISIBLE);
            binding.tvExtra1.setText(getString(R.string.album_singer_fmt, singerName));
            binding.tvExtra1.setOnClickListener(v -> main.openSingerDetail(singerMid, singerName));
        } else {
            binding.tvExtra1.setVisibility(View.GONE);
        }

        binding.rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SongAdapter((song, position) -> main.playAndOpenPlayer(songs, position), false);
        binding.rvSongs.setAdapter(adapter);

        binding.btnPlayAll.setOnClickListener(v -> {
            if (songs.isEmpty()) {
                main.showToast("专辑暂无歌曲");
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
        viewModel.getAlbumSongs().observe(getViewLifecycleOwner(), list -> {
            songs.clear();
            songs.addAll(list);
            adapter.setSongs(songs);
        });
        viewModel.getHint().observe(getViewLifecycleOwner(),
                msg -> binding.tvHint.setText(msg == null ? "" : msg));
        viewModel.loadAlbumSongs(albumMid);
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}