package org.qp.android.view.stock.fragment.dialogs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public abstract class StockPatternDialogFrags extends DialogFragment {
    public interface StockPatternDialogList {
        void onDialogDestroy(DialogFragment dialog);
        void onDialogPositiveClick(DialogFragment dialog);
        void onDialogNeutralClick(DialogFragment dialog);
        void onDialogListClick(DialogFragment dialog, int which);
    }

    public StockPatternDialogFrags.StockPatternDialogList listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (StockPatternDialogFrags.StockPatternDialogList) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context
                    + " must implement PatternDialogListener");
        }
    }
}
