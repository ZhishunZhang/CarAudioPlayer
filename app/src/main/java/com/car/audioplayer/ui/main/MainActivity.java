package com.car.audioplayer.ui.main;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.Album;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.model.Playlist;
import com.car.audioplayer.data.model.Rank;
import com.car.audioplayer.databinding.ActivityMainBinding;
import com.car.audioplayer.player.ImageLoader;
import com.car.audioplayer.data.repository.UserProfileStore;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.detail.AlbumDetailFragment;
import com.car.audioplayer.ui.detail.RankDetailFragment;
import com.car.audioplayer.ui.detail.SingerDetailFragment;
import com.car.audioplayer.ui.detail.SongListDetailFragment;
import com.car.audioplayer.ui.home.HomeFragment;
import com.car.audioplayer.ui.mine.MineFragment;
import com.car.audioplayer.ui.player.PlayerFragment;
import com.car.audioplayer.ui.rank.RankFragment;
import com.car.audioplayer.ui.search.SearchFragment;
import com.car.audioplayer.ui.search.SearchResultFragment;
import com.car.audioplayer.ui.songlist.SongListSquareFragment;

import java.util.List;

import static com.car.audioplayer.data.remote.QQMusicApi.coverUrl;

/**
 * 主容器：左侧 4 Tab 导航 + 内容区 Fragment 栈 + 底部常驻迷你播放条。
 * 一级 Tab 页用 add/hide 方式驻留（保状态）；二级页面 addToBackStack 压栈。
 * 全屏播放页走 player_overlay 覆盖层（不压栈），从播放条位置"长出"盖住整个右侧区。
 */
public class MainActivity extends AppCompatActivity {

    public static final String TAB_HOME = "home";
    public static final String TAB_PLAYLIST = "playlist";
    public static final String TAB_RANK = "rank";
    public static final String TAB_SEARCH = "search";

    /** 全屏播放页覆盖层标记（不进返回栈，独立于二级页面栈） */
    private static final String TAG_PLAYER = "player_overlay";

    /** 播放页长出/收起动画时长 */
    private static final long PLAYER_ANIM_MS = 280L;

    private ActivityMainBinding binding;
    private PlaybackManager playback;
    private String currentTab = TAB_HOME;

    /** 播放页覆盖层是否展开 */
    private boolean playerOpen = false;
    private ValueAnimator playerAnim = null;

    /** 头像读取专用线程（避免主线程磁盘 IO） */
    private static final java.util.concurrent.ExecutorService IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    /** 入口：ViewBinding 绑定 + 播放管理器初始化 + 导航/迷你条/返回键装配 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playback = PlaybackManager.get(this);

        initNavTabs();
        loadNavAvatar();
        showTab(TAB_HOME, true);
        observeMiniBar();
        restorePlayerOverlay();
        requestNotificationPermission();
    }

    // ================= 一级导航 =================

    /** 左侧 4 个导航项的点击绑定：点谁切谁（当前 Tab 重复点击不动作） */
    private void initNavTabs() {
        binding.navHome.setOnClickListener(v -> switchTab(TAB_HOME));
        binding.navPlaylist.setOnClickListener(v -> switchTab(TAB_PLAYLIST));
        binding.navRank.setOnClickListener(v -> switchTab(TAB_RANK));
        binding.navSearch.setOnClickListener(v -> switchTab(TAB_SEARCH));
        binding.navAvatar.setOnClickListener(v -> openMine());
    }

    /** Tab 切换：先收播放页覆盖层，再清空二级栈回到一级层 */
    public void switchTab(String tab) {
        if (playerOpen) {
            closePlayer(false);
        }
        if (tab.equals(currentTab)) {
            popDetails();
            return;
        }
        popDetails();
        showTab(tab, false);
    }

    /**
     * 一级 Tab 切换核心：add/hide 驻留式（非 replace）。
     * 首次点击 add 进容器，之后 hide 旧的 show 新的——Fragment 不销毁、
     * ViewModel 不清空，切回来秒回原状态（滚动位置/已加载数据都在）。
     */
    private void showTab(String tab, boolean force) {
        if (!force && tab.equals(currentTab)) {
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(tab);
        if (target == null) {
            target = createTabFragment(tab);
            fm.beginTransaction().add(R.id.container, target, tab).commitNow();
        }
        Fragment current = fm.findFragmentByTag(currentTab);
        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
        if (current != null) {
            tx.hide(current);
        }
        tx.show(target);
        tx.commitNow();
        currentTab = tab;
        updateTabHighlight(tab);
    }

    /** Tab tag → Fragment 实例的映射（新增一级页只需在此登记） */
    private Fragment createTabFragment(String tab) {
        switch (tab) {
            case TAB_PLAYLIST:
                return new SongListSquareFragment();
            case TAB_RANK:
                return new RankFragment();
            case TAB_SEARCH:
                return new SearchFragment();
            case TAB_HOME:
            default:
                return new HomeFragment();
        }
    }

    /** 4 个导航项的高亮态同步（图标选中/未选中两套资源切换） */
    private void updateTabHighlight(String tab) {
        setNavItem(binding.navHome, R.drawable.ic_nav_home, tab.equals(TAB_HOME));
        setNavItem(binding.navPlaylist, R.drawable.ic_nav_playlist, tab.equals(TAB_PLAYLIST));
        setNavItem(binding.navRank, R.drawable.ic_nav_rank, tab.equals(TAB_RANK));
        setNavItem(binding.navSearch, R.drawable.ic_nav_search, tab.equals(TAB_SEARCH));
    }

    private void setNavItem(LinearLayout item, int iconRes, boolean selected) {
        item.setBackground(selected ? ContextCompat.getDrawable(this, R.drawable.bg_tab_selected) : null);
        int tint = ContextCompat.getColor(this, selected ? R.color.playing_tint : R.color.text_secondary);
        ImageView icon = null;
        TextView label = null;
        for (int i = 0; i < item.getChildCount(); i++) {
            if (item.getChildAt(i) instanceof ImageView) {
                icon = (ImageView) item.getChildAt(i);
            } else if (item.getChildAt(i) instanceof TextView) {
                label = (TextView) item.getChildAt(i);
            }
        }
        if (icon != null) {
            icon.setColorFilter(tint);
        }
        if (label != null) {
            label.setTextColor(tint);
        }
    }

    /** 清空全部二级页面（不包含一级 Tab 页） */
    public void popDetails() {
        getSupportFragmentManager().popBackStackImmediate(null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    // ================= 公共导航入口（供各 Fragment 调用） =================

    /**
     * 二级页压栈（详情/搜索结果/我的）：add 叠加 + addToBackStack。
     * 与一级 Tab 的 add/hide 不同：压栈页叠加在 Tab 页之上、返回键逐层弹出。
     * 根布局已设 clickable 拦截点击穿透（纯展示区不漏点到下层 Tab 页）。
     */
    private void push(Fragment fragment, String tag) {
        if (playerOpen) {
            closePlayer(false);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, fragment, tag)
                .addToBackStack(tag)
                .commit();
    }

    /** 跳歌单详情（广场卡片点击） */
    public void openSongListDetail(Playlist playlist) {
        push(SongListDetailFragment.newInstance(playlist), "songlist_detail");
    }

    /** 跳榜单详情（榜单卡片点击） */
    public void openRankDetail(Rank rank) {
        push(RankDetailFragment.newInstance(rank), "rank_detail");
    }

    /** 跳专辑详情（歌手页专辑卡点击） */
    public void openAlbumDetail(Album album) {
        push(AlbumDetailFragment.newInstance(album), "album_detail");
    }

    /** 跳歌手详情（搜索结果歌手名点击） */
    public void openSingerDetail(String singerMid, String singerName) {
        push(SingerDetailFragment.newInstance(singerMid, singerName), "singer_detail");
    }

    /** 跳搜索结果页（搜索提交） */
    public void openSearchResult(String keyword) {
        push(SearchResultFragment.newInstance(keyword), "search_result");
    }

    /** 跳"我的"页（首页头像点击，作为二级页压栈） */
    public void openMine() {
        push(new MineFragment(), "mine");
    }

    /** 点歌：替换队列播放并立即展开全屏播放页 */
    public void playAndOpenPlayer(List<OnlineSong> songs, int index) {
        playback.playQueue(songs, index);
        openPlayer();
    }

    // ================= 全屏播放页覆盖层（从播放条长出） =================

    /**
     * 展开播放页：不压返回栈，直接盖在右侧区上层。
     * 动画：以播放条高度为起点、底部为轴心向上放大（translateY+scale 等效），同时播放条淡出。
     */
    public void openPlayer() {
        if (playerOpen) {
            return;
        }
        playerOpen = true;
        cancelPlayerAnim();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.player_overlay, new PlayerFragment(), TAG_PLAYER)
                .commitNow();
        Fragment player = getSupportFragmentManager().findFragmentByTag(TAG_PLAYER);
        View playerView = player == null ? null : player.getView();
        binding.playerOverlay.setVisibility(View.VISIBLE);
        float stageH = binding.playerStage.getHeight();
        float miniH = binding.miniBar.getHeight();
        if (playerView == null || stageH <= 0f || miniH <= 0f) {
            binding.miniBar.setAlpha(0f);
            return;
        }
        float fromScale = Math.max(0.05f, miniH / stageH);
        playerView.setPivotY(stageH);
        playerView.setScaleY(fromScale);
        playerView.setAlpha(0f);
        playerAnim = ValueAnimator.ofFloat(0f, 1f);
        playerAnim.setDuration(PLAYER_ANIM_MS);
        playerAnim.setInterpolator(new DecelerateInterpolator());
        playerAnim.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            playerView.setScaleY(fromScale + (1f - fromScale) * t);
            playerView.setAlpha(Math.min(1f, t * 2f));
            if (binding != null) {
                binding.miniBar.setAlpha(1f - t);
            }
        });
        playerAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (binding == null) {
                    return;
                }
                playerView.setScaleY(1f);
                playerView.setAlpha(1f);
                binding.miniBar.setAlpha(0f);
            }
        });
        playerAnim.start();
    }

    /** 收起播放页：反向缩回播放条并淡回播放条（播放不中断） */
    private void closePlayer(boolean animated) {
        if (!playerOpen) {
            return;
        }
        playerOpen = false;
        cancelPlayerAnim();
        Fragment player = getSupportFragmentManager().findFragmentByTag(TAG_PLAYER);
        View playerView = player == null ? null : player.getView();
        float stageH = binding.playerStage.getHeight();
        float miniH = binding.miniBar.getHeight();
        if (!animated || playerView == null || stageH <= 0f || miniH <= 0f) {
            removePlayerFragment();
            binding.miniBar.setAlpha(1f);
            return;
        }
        float toScale = Math.max(0.05f, miniH / stageH);
        playerView.setPivotY(stageH);
        playerAnim = ValueAnimator.ofFloat(0f, 1f);
        playerAnim.setDuration(PLAYER_ANIM_MS);
        playerAnim.setInterpolator(new DecelerateInterpolator());
        playerAnim.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            playerView.setScaleY(1f - (1f - toScale) * t);
            playerView.setAlpha(1f - t);
            if (binding != null) {
                binding.miniBar.setAlpha(t);
            }
        });
        playerAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (binding == null || playerOpen) {
                    return; // 收起过程中被重新展开，交给展开动画收尾
                }
                removePlayerFragment();
                binding.miniBar.setAlpha(1f);
            }
        });
        playerAnim.start();
    }

    private void removePlayerFragment() {
        Fragment player = getSupportFragmentManager().findFragmentByTag(TAG_PLAYER);
        if (player != null) {
            getSupportFragmentManager().beginTransaction().remove(player).commitNow();
        }
        binding.playerOverlay.setVisibility(View.GONE);
    }

    private void cancelPlayerAnim() {
        if (playerAnim != null) {
            playerAnim.cancel();
            playerAnim = null;
        }
    }

    /** 进程重建：覆盖层 Fragment 已随FragmentManager 恢复，直接呈现展开态 */
    private void restorePlayerOverlay() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_PLAYER) != null) {
            playerOpen = true;
            binding.playerOverlay.setVisibility(View.VISIBLE);
            binding.miniBar.setAlpha(0f);
        }
    }

    /** 返回：先收播放页覆盖层，再退二级栈（播放页关闭时播放不中断） */
    public void goBack() {
        if (playerOpen) {
            closePlayer(true);
            return;
        }
        if (!getSupportFragmentManager().popBackStackImmediate()) {
            finish();
        }
    }

    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 侧栏头像与"我的"页保持一致：有自定义头像显示照片，否则保持默认人形图标 */
    public void loadNavAvatar() {
        final android.content.Context appCtx = getApplicationContext();
        IO.execute(() -> {
            Bitmap avatar = ImageLoader.toCircle(UserProfileStore.get(appCtx).loadAvatar());
            runOnUiThread(() -> {
                if (binding == null || avatar == null) {
                    return;
                }
                binding.ivAvatar.setImageBitmap(avatar);
                binding.ivAvatar.setPadding(0, 0, 0, 0);
                binding.ivAvatar.setImageTintList(null);
                binding.ivAvatar.setBackground(null);
                binding.ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            });
        });
    }

    // ================= 输入法 =================

    /**
     * 全局收键盘：触摸落点在当前聚焦的输入框之外时收起软键盘。
     * 覆盖所有页面（目前只有搜索框），点空白/列表/按钮均收起，符合"点键盘外任意处即收起"的交互。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View focus = getCurrentFocus();
            if (focus instanceof EditText) {
                Rect rect = new Rect();
                focus.getGlobalVisibleRect(rect);
                if (!rect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    InputMethodManager imm =
                            (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                    }
                    focus.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ================= 迷你播放条 =================

    /** 迷你条当前歌曲 MID：封面只在换歌时加载一次，避免轮询刷新反复重置占位图 */
    private String miniCoverMid = "";

    private void observeMiniBar() {
        // 换歌才发：标题/歌手/封面各刷一次，不再随进度轮询每 500ms 重复 setText
        playback.song().observe(this, song -> {
            if (song == null) {
                binding.tvMiniName.setText(getString(R.string.not_playing));
                binding.tvMiniSinger.setText(getString(R.string.tap_to_play_song));
                if (!miniCoverMid.isEmpty()) {
                    miniCoverMid = "";
                    int pad = (int) (6 * getResources().getDisplayMetrics().density);
                    binding.ivMiniCover.setPadding(pad, pad, pad, pad);
                    binding.ivMiniCover.setImageResource(R.drawable.ic_music_note);
                }
                return;
            }
            binding.tvMiniName.setText(song.name);
            binding.tvMiniSinger.setText(song.singer);
            if (!song.mid.equals(miniCoverMid)) {
                miniCoverMid = song.mid;
                binding.ivMiniCover.setPadding(0, 0, 0, 0);
                ImageLoader.get().load(binding.ivMiniCover, coverUrl(song.albumMid));
            }
        });

        // 播放/暂停变化才刷图标
        playback.playing().observe(this, playing ->
                binding.ivMiniPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play));

        binding.btnMiniPlay.setOnClickListener(v -> playback.toggle());
        binding.btnMiniNext.setOnClickListener(v -> playback.next());
        binding.miniBar.setOnClickListener(v -> openPlayer());
    }

    // ================= 通知权限 =================

    /** Android 13+ 需要运行时通知权限，媒体通知才可见 */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 通知权限是否授予不影响核心播放
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPlayerAnim();
        binding = null;
    }
}