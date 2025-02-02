package org.qp.android.view.game.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.qp.android.viewModel.GameViewModel;

public class GameVarsFragment extends GamePatternFragment {
    private WebView varsDescView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater ,
                             @Nullable ViewGroup container ,
                             @Nullable Bundle savedInstanceState) {
        var viewModel = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        varsDescView = new WebView(requireContext());
        varsDescView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        varsDescView.setBackgroundColor(viewModel.getBackgroundColor());
        varsDescView.setWebViewClient(viewModel.getWebViewClient());
        viewModel.getControllerObserver().observe(getViewLifecycleOwner() , settingsController -> {
            varsDescView.setBackgroundColor(viewModel.getBackgroundColor());
            varsDescView.refreshDrawableState();
        });
        viewModel.getVarsDescObserver().observe(getViewLifecycleOwner() , desc ->
                varsDescView.loadDataWithBaseURL(
                        "file:///" ,
                        desc ,
                        "text/html" ,
                        "UTF-8" ,
                        null));
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        layout.setGravity(Gravity.CENTER);
        layout.addView(varsDescView);
        return layout;
    }
}
