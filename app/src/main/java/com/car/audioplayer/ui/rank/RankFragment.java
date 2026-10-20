package com.car.audioplayer.ui.rank;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.car.audioplayer.databinding.FragmentRankBinding;
import com.car.audioplayer.ui.common.RankAdapter;
import com.car.audioplayer.ui.main.MainActivity;

/**
 * 榜单页：展示全部榜单卡片。
 */
public class RankFragment extends Fragment {

    private FragmentRankBinding binding;
    private RankAdapter adapter;

    /** ViewBinding 充气榜单页布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRankBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();

        binding.rvRanks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RankAdapter(main::openRankDetail);
        binding.rvRanks.setAdapter(adapter);

        RankViewModel viewModel = new ViewModelProvider(this).get(RankViewModel.class);
        viewModel.loadTopLists();
        viewModel.getTopLists().observe(getViewLifecycleOwner(), adapter::setRanks);
        // hint 为字符串资源 ID（ViewModel 无 Context），此处解析成文字
        viewModel.getHint().observe(getViewLifecycleOwner(),
                resId -> binding.tvHint.setText(resId == null || resId == 0 ? "" : getString(resId)));
    }

    /** 视图销毁：置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}