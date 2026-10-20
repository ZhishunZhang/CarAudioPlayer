package com.car.audioplayer.ui.songlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.car.audioplayer.data.model.Category;
import com.car.audioplayer.databinding.FragmentSonglistSquareBinding;
import com.car.audioplayer.ui.common.ChipAdapter;
import com.car.audioplayer.ui.common.PlaylistAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌单广场：分类标签 + 歌单卡片网格。
 */
public class SongListSquareFragment extends Fragment {

    private FragmentSonglistSquareBinding binding;
    private PlaylistAdapter adapter;
    private ChipAdapter categoryAdapter;
    private SongListSquareViewModel viewModel;
    private final List<Category> categories = new ArrayList<>();
    private long currentCategoryId = 10000000L;

    /** ViewBinding 充气歌单广场布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSonglistSquareBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();

        viewModel = new ViewModelProvider(this).get(SongListSquareViewModel.class);

        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new ChipAdapter((text, position) -> {
            if (position < categories.size()) {
                currentCategoryId = categories.get(position).id;
                // 选中态跟随点击移动（原来一直停在"全部"）
                categoryAdapter.setSelected(position);
                viewModel.loadPlaylists(currentCategoryId, 5, 0);
            }
        });
        binding.rvCategories.setAdapter(categoryAdapter);

        binding.rvPlaylists.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        adapter = new PlaylistAdapter(main::openSongListDetail);
        binding.rvPlaylists.setAdapter(adapter);

        viewModel.getCategories().observe(getViewLifecycleOwner(), list -> {
            categories.clear();
            categories.add(new Category("全部", 10000000L));
            if (list != null) {
                for (Category c : list) {
                    if ("全部".equals(c.name)) {
                        continue;
                    }
                    categories.add(c);
                }
            }
            List<String> labels = new ArrayList<>();
            for (Category c : categories) {
                labels.add(c.name);
            }
            categoryAdapter.setItems(labels);
            binding.rvCategories.setVisibility(View.VISIBLE);
        });

        viewModel.getPlaylists().observe(getViewLifecycleOwner(), list -> {
            adapter.setPlaylists(list);
            // 切分类后从新列表顶部看起（原来停在旧分类的滚动位置）
            binding.rvPlaylists.scrollToPosition(0);
        });
        // hint 为字符串资源 ID（ViewModel 无 Context），此处解析成文字
        viewModel.getHint().observe(getViewLifecycleOwner(),
                resId -> binding.tvHint.setText(resId == null || resId == 0 ? "" : getString(resId)));

        viewModel.loadCategories();
        viewModel.loadPlaylists(currentCategoryId, 5, 0);
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}