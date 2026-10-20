package com.car.audioplayer.ui.mine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.car.audioplayer.R;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.car.audioplayer.data.local.entity.FavoritePlaylist;
import com.car.audioplayer.data.local.entity.FavoriteSong;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.model.Playlist;
import com.car.audioplayer.data.repository.FavoritesRepository;
import com.car.audioplayer.data.repository.UserProfileStore;
import com.car.audioplayer.databinding.FragmentMineBinding;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.PlaylistAdapter;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 我的（本地版）：头像昵称本地编辑 + 我喜欢的歌曲 + 收藏的歌单。
 * 无账号体系：头像从相册选图保存，昵称弹窗编辑，收藏全部存 Room。
 */
public class MineFragment extends Fragment {

    private FragmentMineBinding binding;
    private SongAdapter favSongAdapter;
    private PlaylistAdapter favPlaylistAdapter;
    private final List<OnlineSong> favSongList = new ArrayList<>();

    private ActivityResultLauncher<String> pickAvatarLauncher;

    /** 头像磁盘读写专用线程（避免主线程 IO） */
    private static final java.util.concurrent.ExecutorService IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    /** ViewBinding 充气“我的”页面布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMineBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 相册选图回调
        pickAvatarLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onAvatarPicked);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();
        PlaybackManager playback = PlaybackManager.get(requireContext());
        FavoritesRepository favorites = FavoritesRepository.get(requireContext());
        UserProfileStore profile = UserProfileStore.get(requireContext());

        binding.btnBack.setOnClickListener(v -> main.goBack());

        // 头像 / 昵称本地编辑
        binding.btnAvatar.setOnClickListener(v ->
                pickAvatarLauncher.launch("image/*"));
        binding.tvNickname.setOnClickListener(v -> showNicknameDialog(main, profile));

        refreshProfile(profile);

        // 我喜欢的歌曲
        binding.rvFavSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        favSongAdapter = new SongAdapter(new SongAdapter.Listener() {
            @Override
            public void onSongClick(OnlineSong song, int position) {
                main.playAndOpenPlayer(favSongList, position);
            }

            @Override
            public void onFavClick(OnlineSong song, int position) {
                favorites.toggleSongFavorite(song, fav -> main.showToast("已取消收藏"));
            }
        }, true);
        binding.rvFavSongs.setAdapter(favSongAdapter);

        // 收藏的歌单
        binding.rvFavPlaylists.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        favPlaylistAdapter = new PlaylistAdapter(main::openSongListDetail);
        binding.rvFavPlaylists.setAdapter(favPlaylistAdapter);

        // 收藏歌曲变化 → 刷新列表
        favorites.observeFavoriteSongs().observe(getViewLifecycleOwner(), favSongs -> {
            favSongList.clear();
            for (FavoriteSong f : favSongs) {
                favSongList.add(new OnlineSong(f.songMid, f.name, f.singer, f.singerMid,
                        f.album, f.albumMid, "", f.interval));
            }
            java.util.Set<String> mids = new java.util.HashSet<>();
            for (FavoriteSong f : favSongs) {
                mids.add(f.songMid);
            }
            favSongAdapter.setFavoriteMids(mids);
            favSongAdapter.setSongs(favSongList);
            binding.tvFavSongsTitle.setText(getString(R.string.fav_songs_title_fmt, favSongList.size()));
            binding.tvFavSongsEmpty.setVisibility(favSongList.isEmpty() ? View.VISIBLE : View.GONE);
            binding.rvFavSongs.setVisibility(favSongList.isEmpty() ? View.GONE : View.VISIBLE);
        });

        favorites.observeFavoritePlaylists().observe(getViewLifecycleOwner(), playlists -> {
            List<Playlist> list = new ArrayList<>();
            for (FavoritePlaylist f : playlists) {
                list.add(new Playlist(f.disstid, f.name, f.cover, 0));
            }
            favPlaylistAdapter.setPlaylists(list);
            binding.rvFavPlaylists.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        });

        // 换歌才发：不再随进度轮询空转
        playback.song().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                favSongAdapter.setCurrentMid(song.mid);
            }
        });
    }

    private void refreshProfile(UserProfileStore profile) {
        // 昵称：空时显示占位提示（提示文字不入库，避免被拼进保存值）
        String nickname = profile.getNickname();
        if (nickname.isEmpty()) {
            binding.tvNickname.setText(getString(R.string.set_nickname_hint));
            binding.tvNickname.setTextColor(requireContext().getColor(R.color.text_hint));
        } else {
            binding.tvNickname.setText(nickname);
            binding.tvNickname.setTextColor(requireContext().getColor(R.color.text_primary));
        }
        // 头像：磁盘读取 + 解码放后台，避免主线程 IO
        IO.execute(() -> {
            Bitmap avatar = com.car.audioplayer.player.ImageLoader.toCircle(profile.loadAvatar());
            if (avatar == null) {
                return;
            }
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                binding.ivAvatar.setImageBitmap(avatar);
                binding.ivAvatar.setPadding(0, 0, 0, 0);
                // 清除 XML 里的 tint：否则真实照片会被染成纯色灰块，看起来像空白
                binding.ivAvatar.setImageTintList(null);
                binding.ivAvatar.setColorFilter(null);
                binding.ivAvatar.setBackground(null);
                binding.ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            });
        });
    }

    private void onAvatarPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        // 解码 + 压缩写盘都在后台，完成后回主线程刷 UI
        MainActivity mainAct = (MainActivity) requireActivity();
        android.content.Context ctx = requireContext();
        IO.execute(() -> {
            boolean success;
            try {
                InputStream in = ctx.getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (in != null) {
                    in.close();
                }
                success = bitmap != null
                        && UserProfileStore.get(ctx).saveAvatar(bitmap) != null;
            } catch (Exception e) {
                success = false;
            }
            final boolean ok = success;
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                if (ok) {
                    refreshProfile(UserProfileStore.get(ctx));
                    // 侧栏头像同步刷新
                    mainAct.loadNavAvatar();
                    mainAct.showToast("头像已更新");
                } else {
                    mainAct.showToast("头像保存失败");
                }
            });
        });
    }

    /**
     * 昵称编辑弹窗：点昵称弹出输入框，保存后写入 UserProfileStore 并刷新显示。
     * 流程：预填旧昵称 → 保存做非空校验（空白昵称不入库，避免存成空格串）；
     * 取消则直接关闭不写库。用 FrameLayout 包裹补内边距，否则 EditText 会贴着弹窗边缘。
     */
    private void showNicknameDialog(MainActivity main, UserProfileStore profile) {
        EditText input = new EditText(requireContext());
        input.setText(profile.getNickname());
        input.setTextSize(18);
        input.setSelection(input.getText().length());
        FrameLayout container = new FrameLayout(requireContext());
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle("修改昵称")
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String nickname = input.getText().toString().trim();
                    if (!nickname.isEmpty()) {
                        profile.setNickname(nickname);
                        refreshProfile(profile);
                        main.showToast("昵称已更新");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}