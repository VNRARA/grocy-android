package xyz.zedler.patrick.grocy.fragment.bottomSheetDialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import xyz.zedler.patrick.grocy.MainActivity;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.fragment.MasterProductEditSimpleFragment;
import xyz.zedler.patrick.grocy.fragment.MasterProductsFragment;
import xyz.zedler.patrick.grocy.model.Location;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductGroup;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.util.Constants;

public class MasterDeleteBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private final static boolean DEBUG = false;
    private final static String TAG = "MasterDeleteBottomSheet";

    private MainActivity activity;

    private Product product;
    private Location location;
    // TODO: private Store store;
    private QuantityUnit quantityUnit;
    private ProductGroup productGroup;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), R.style.Theme_Grocy_BottomSheetDialog);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(
                R.layout.fragment_bottomsheet_master_delete, container, false
        );

        activity = (MainActivity) getActivity();
        assert activity != null;

        String textType = "";
        String textName = "";

        Bundle bundle = getArguments();
        if(bundle != null) {
            String type = bundle.getString(Constants.ARGUMENT.TYPE, Constants.ARGUMENT.PRODUCT);
            switch (type) {
                case Constants.ARGUMENT.LOCATION:
                    location = bundle.getParcelable(Constants.ARGUMENT.LOCATION);
                    if(location != null) {
                        textType = activity.getString(R.string.type_location);
                        textName = location.getName();
                    }
                    break;
                case Constants.ARGUMENT.QUANTITY_UNIT:
                    // TODO
                    quantityUnit = bundle.getParcelable(Constants.ARGUMENT.QUANTITY_UNIT);
                    break;
                case Constants.ARGUMENT.PRODUCT_GROUP:
                    productGroup = bundle.getParcelable(Constants.ARGUMENT.PRODUCT_GROUP);
                    break;
                default:
                    product = bundle.getParcelable(Constants.ARGUMENT.PRODUCT);
                    if(product != null) {
                        textType = activity.getString(R.string.type_product);
                        textName = product.getName();
                    }
            }
        }

        TextView textView = view.findViewById(R.id.text_master_delete_question);
        textView.setText(
                activity.getString(
                        R.string.msg_master_delete,
                        textType,
                        textName
                )
        );

        view.findViewById(R.id.button_master_delete_delete).setOnClickListener(v -> {
            Fragment current = activity.getCurrentFragment();
            if(current.getClass() == MasterProductsFragment.class) {
                ((MasterProductsFragment) current).deleteProduct(product);
            } else if(current.getClass() == MasterProductEditSimpleFragment.class) {
                ((MasterProductEditSimpleFragment) current).deleteProduct(product);
            }
            dismiss();
        });

        view.findViewById(R.id.button_master_delete_cancel).setOnClickListener(v -> dismiss());

        return view;
    }

    @NonNull
    @Override
    public String toString() {
        return TAG;
    }
}
