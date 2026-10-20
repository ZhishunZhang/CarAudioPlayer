package com.car.audioplayer.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;

import com.car.audioplayer.data.model.Banner;
import com.car.audioplayer.data.model.Playlist;
import com.car.audioplayer.data.model.Rank;
import com.car.audioplayer.databinding.FragmentHomeBinding;
import com.car.audioplayer.ui.common.BannerAdapter;
import com.car.audioplayer.ui.common.ChipAdapter;
import com.car.audioplayer.ui.common.PlaylistAdapter;
import com.car.audioplayer.ui.common.RankAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页：banner 轮播 + 推荐歌单 + 榜单快捷入口 + 热搜词。
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private BannerAdapter bannerAdapter;
    private PlaylistAdapter playlistAdapter;
    private RankAdapter rankAdapter;
    private ChipAdapter hotkeyAdapter;
    private boolean contentShown = false;

    /** ViewBinding 充气首页布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();

        // ===== Banner 轮播 =====
        binding.rvBanner.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        new PagerSnapHelper().attachToRecyclerView(binding.rvBanner);
        bannerAdapter = new BannerAdapter(this::onBannerClick);
        binding.rvBanner.setAdapter(bannerAdapter);

        // ===== 推荐歌单横滑 =====
        binding.rvPlaylists.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        playlistAdapter = new PlaylistAdapter(main::openSongListDetail);
        binding.rvPlaylists.setAdapter(playlistAdapter);

        // ===== 榜单入口 =====
        binding.rvRanks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rankAdapter = new RankAdapter(main::openRankDetail);
        binding.rvRanks.setAdapter(rankAdapter);

        // ===== 热搜词 =====
        binding.rvHotkeys.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        hotkeyAdapter = new ChipAdapter((text, position) -> main.openSearchResult(text));
        binding.rvHotkeys.setAdapter(hotkeyAdapter);

        HomeViewModel viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        viewModel.loadRecommend();
        viewModel.loadBanners();
        viewModel.loadHotkeys();

        // 推荐（歌单 + 榜单）
        viewModel.getRecommend().observe(getViewLifecycleOwner(), result -> {
            showContentOnce();
            playlistAdapter.setPlaylists(result.playlists);
            binding.rvPlaylists.setVisibility(result.playlists.isEmpty() ? View.GONE : View.VISIBLE);

            // 只展示前 4 个榜单入口，避免首页过长
            List<Rank> top = new ArrayList<>();
            int count = Math.min(4, result.ranks.size());
            for (int i = 0; i < count; i++) {
                top.add(result.ranks.get(i));
            }
            rankAdapter.setRanks(top);
            binding.rvRanks.setVisibility(top.isEmpty() ? View.GONE : View.VISIBLE);
            binding.tvHint.setVisibility(View.GONE);
        });

        // Banner
        viewModel.getBanners().observe(getViewLifecycleOwner(), banners -> {
            bannerAdapter.setBanners(banners);
            binding.rvBanner.setVisibility(banners.isEmpty() ? View.GONE : View.VISIBLE);
        });

        // 热搜词
        viewModel.getHotkeys().observe(getViewLifecycleOwner(), keys -> {
            hotkeyAdapter.setItems(keys);
            binding.rvHotkeys.setVisibility(keys.isEmpty() ? View.GONE : View.VISIBLE);
        });

        // hint 为字符串资源 ID（ViewModel 无 Context），此处解析成文字
        viewModel.getHint().observe(getViewLifecycleOwner(), resId -> {
            if (resId != null && resId != 0) {
                showContentOnce();
            }
            binding.tvHint.setText(resId == null || resId == 0 ? "" : getString(resId));
        });
    }

    /** 骨架屏 → 内容：首次数据到达（或加载失败）时切换，之后不再重复 */
    private void showContentOnce() {
        if (contentShown || binding == null) {
            return;
        }
        contentShown = true;
        binding.homeSkeleton.setVisibility(View.GONE);
        binding.homeContent.setVisibility(View.VISIBLE);
    }

    private void onBannerClick(Banner banner) {
        MainActivity main = (MainActivity) requireActivity();
        if (banner.jumpType == 1 && banner.jumpId != null && !banner.jumpId.isEmpty()) {
            // banner 跳转目标是歌单 ID
            main.openSongListDetail(new Playlist(banner.jumpId, banner.title, banner.cover, 0));
        }
    }

    /** 视图销毁：及时置空 binding，防止字段引用泄漏整棵视图树。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}