package com.car.audioplayer.ui.search;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.databinding.FragmentSearchBinding;
import com.car.audioplayer.ui.common.ChipAdapter;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索页：热搜词（默认态）+ 输入联想（有封面，走搜索接口）+ 提交跳搜索结果页。
 */
public class SearchFragment extends Fragment {

    /** 联想防抖间隔：输入停顿后才发请求，避免每敲一个字都打一次搜索接口 */
    private static final long SUGGEST_DEBOUNCE_MS = 300;

    private FragmentSearchBinding binding;
    private ChipAdapter hotkeyAdapter;
    private SongAdapter suggestAdapter;
    private final List<OnlineSong> suggestList = new ArrayList<>();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSuggest;

    /** ViewBinding 充气搜索页布局，binding 引用留作后续 UI 操作。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();
        SearchInputViewModel viewModel =
                new ViewModelProvider(this).get(SearchInputViewModel.class);

        // 热搜词
        binding.rvHotkeys.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        hotkeyAdapter = new ChipAdapter((text, position) -> goSearch(main, text));
        binding.rvHotkeys.setAdapter(hotkeyAdapter);

        // 联想列表（点击直接播放）
        binding.rvSuggest.setLayoutManager(new LinearLayoutManager(requireContext()));
        suggestAdapter = new SongAdapter((song, position) ->
                main.playAndOpenPlayer(suggestList, position), false);
        binding.rvSuggest.setAdapter(suggestAdapter);

        viewModel.getHotkeys().observe(getViewLifecycleOwner(), hotkeyAdapter::setItems);
        viewModel.getSuggests().observe(getViewLifecycleOwner(), songs -> {
            if (songs == null || songs.isEmpty()) {
                return; // 空结果保持热词态
            }
            suggestList.clear();
            suggestList.addAll(songs);
            suggestAdapter.setSongs(suggestList);
            binding.tvSectionTitle.setText(getString(R.string.search_suggest_section));
            binding.rvHotkeys.setVisibility(View.GONE);
            binding.rvSuggest.setVisibility(View.VISIBLE);
        });

        binding.btnSearch.setOnClickListener(v ->
                goSearch(main, binding.etKeyword.getText().toString().trim()));
        binding.etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                goSearch(main, binding.etKeyword.getText().toString().trim());
                return true;
            }
            return false;
        });
        binding.btnVoice.setOnClickListener(v ->
                main.showToast("语音搜索待接入（毕设预留入口）"));

        binding.etKeyword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 接口要求，未用到
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 接口要求，未用到
            }

            @Override
            public void afterTextChanged(Editable s) {
                String key = s.toString().trim();
                // 防抖核心：先取消上一次输入遗留的延迟任务
                debounceHandler.removeCallbacks(pendingSuggest);
                if (key.isEmpty()) {
                    // 关键词清空：立即回热搜词默认态，不留延迟请求
                    showHotkeyMode();
                    return;
                }
                // 停顿 300ms 才发请求；连续输入不断顺延，实际只请求最后一次的关键词
                pendingSuggest = () -> viewModel.loadSuggest(key);
                debounceHandler.postDelayed(pendingSuggest, SUGGEST_DEBOUNCE_MS);
            }
        });

        viewModel.loadHotkeys();
    }

    /** 切回热搜词默认态：关键词清空或联想结果为空时。 */
    private void showHotkeyMode() {
        binding.tvSectionTitle.setText(getString(R.string.hot_search_section));
        binding.rvHotkeys.setVisibility(View.VISIBLE);
        binding.rvSuggest.setVisibility(View.GONE);
    }

    /** 提交搜索：非空校验后跳搜索结果页；先清输入框焦点，避免结果页还弹输入法。 */
    private void goSearch(MainActivity main, String keyword) {
        if (keyword.isEmpty()) {
            main.showToast("请输入搜索关键词");
            return;
        }
        binding.etKeyword.clearFocus();
        main.openSearchResult(keyword);
    }

    /** 视图销毁：顺带清理防抖挂起任务，防止延迟回调作用于已销毁视图；置空 binding 防泄漏。 */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        debounceHandler.removeCallbacks(pendingSuggest);
        binding = null;
    }
}