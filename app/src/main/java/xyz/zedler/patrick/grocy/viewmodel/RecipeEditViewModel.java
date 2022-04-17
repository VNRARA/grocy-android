/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2020-2022 by Patrick Zedler and Dominic Zedler
 */

package xyz.zedler.patrick.grocy.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.android.volley.VolleyError;

import org.json.JSONObject;

import java.util.List;

import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.api.GrocyApi.ENTITY;
import xyz.zedler.patrick.grocy.fragment.RecipeEditFragmentArgs;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.InputProductBottomSheet;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.FormDataRecipeEdit;
import xyz.zedler.patrick.grocy.model.InfoFullscreen;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductBarcode;
import xyz.zedler.patrick.grocy.model.ProductDetails;
import xyz.zedler.patrick.grocy.model.Recipe;
import xyz.zedler.patrick.grocy.repository.RecipeEditRepository;
import xyz.zedler.patrick.grocy.util.Constants;
import xyz.zedler.patrick.grocy.util.Constants.ARGUMENT;
import xyz.zedler.patrick.grocy.util.Constants.PREF;
import xyz.zedler.patrick.grocy.util.GrocycodeUtil;
import xyz.zedler.patrick.grocy.util.PrefsUtil;

public class RecipeEditViewModel extends BaseViewModel {

  private static final String TAG = RecipeEditViewModel.class.getSimpleName();

  private final SharedPreferences sharedPrefs;
  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final RecipeEditRepository repository;
  private final FormDataRecipeEdit formData;
  private final RecipeEditFragmentArgs args;

  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<InfoFullscreen> infoFullscreenLive;
  private final MutableLiveData<Boolean> offlineLive;

  private List<Product> products;
  private List<ProductBarcode> productBarcodes;

  private final boolean debug;
  private final boolean isActionEdit;

  public RecipeEditViewModel(
      @NonNull Application application,
      @NonNull RecipeEditFragmentArgs startupArgs
  ) {
    super(application);

    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
    debug = PrefsUtil.isDebuggingEnabled(sharedPrefs);

    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue);
    grocyApi = new GrocyApi(getApplication());
    repository = new RecipeEditRepository(application);
    formData = new FormDataRecipeEdit(application, sharedPrefs, startupArgs);
    args = startupArgs;
    isActionEdit = startupArgs.getAction().equals(Constants.ACTION.EDIT);

    infoFullscreenLive = new MutableLiveData<>();
    offlineLive = new MutableLiveData<>(false);
  }

  public FormDataRecipeEdit getFormData() {
    return formData;
  }

  public void loadFromDatabase(boolean downloadAfterLoading) {
    repository.loadFromDatabase(data -> {
      this.products = data.getProducts();
      this.productBarcodes = data.getProductBarcodes();

      formData.getProductsLive().setValue(Product.getActiveProductsOnly(products));
      fillWithRecipeIfNecessary();
      if (downloadAfterLoading) {
        downloadData();
      }
    });
  }

  public void downloadData(@Nullable String dbChangedTime) {
    if (isOffline()) { // skip downloading
      isLoadingLive.setValue(false);
      return;
    }

    dlHelper.updateData(
            () -> loadFromDatabase(false),
            this::onDownloadError,
            Product.class,
            ProductBarcode.class
    );
  }

  public void downloadData() {
    downloadData(null);
  }

  public void downloadDataForceUpdate() {
    SharedPreferences.Editor editPrefs = sharedPrefs.edit();
    editPrefs.putString(PREF.DB_LAST_TIME_PRODUCTS, null);
    editPrefs.putString(PREF.DB_LAST_TIME_PRODUCT_BARCODES, null);
    editPrefs.apply();
    downloadData();
  }

  private void onDownloadError(@Nullable VolleyError error) {
    if (debug) {
      Log.e(TAG, "onError: VolleyError: " + error);
    }
    showMessage(getString(R.string.msg_no_connection));
    if (!isOffline()) {
      setOfflineLive(true);
    }
  }

  public void setProduct(int productId, ProductBarcode barcode, String stockEntryId) {
    DownloadHelper.OnQueueEmptyListener onQueueEmptyListener = () -> {
      ProductDetails productDetails = formData.getProductDetailsLive().getValue();
      assert productDetails != null;
      Product product = productDetails.getProduct();

      if (productDetails.getStockAmountAggregated() == 0) {
        String name = product.getName();
        showMessageAndContinueScanning(getApplication().getString(R.string.msg_not_in_stock, name));
        return;
      }

      formData.getProductDetailsLive().setValue(productDetails);
      formData.getProductNameLive().setValue(product.getName());

      formData.isFormValid();
    };

    dlHelper.newQueue(
            onQueueEmptyListener,
            error -> showMessageAndContinueScanning(getString(R.string.error_no_product_details))
    ).append(
            dlHelper.getProductDetails(
                    productId,
                    productDetails -> formData.getProductDetailsLive().setValue(productDetails)
            )
    ).start();
  }

  public void onBarcodeRecognized(String barcode) {
    if (formData.getProductDetailsLive().getValue() != null) {
      formData.getBarcodeLive().setValue(barcode);
      return;
    }
    Product product = null;
    String stockEntryId = null;
    GrocycodeUtil.Grocycode grocycode = GrocycodeUtil.getGrocycode(barcode);
    if (grocycode != null && grocycode.isProduct()) {
      product = Product.getProductFromId(products, grocycode.getObjectId());
      if (product == null) {
        showMessageAndContinueScanning(R.string.msg_not_found);
        return;
      }
      stockEntryId = grocycode.getProductStockEntryId();
    } else if (grocycode != null) {
      showMessageAndContinueScanning(R.string.error_wrong_grocycode_type);
      return;
    }
    ProductBarcode productBarcode = null;
    if (product == null) {
      for (ProductBarcode code : productBarcodes) {
        if (code.getBarcode().equals(barcode)) {
          productBarcode = code;
          product = Product.getProductFromId(products, code.getProductIdInt());
        }
      }
    }
    if (product != null) {
      setProduct(product.getId(), productBarcode, stockEntryId);
    } else {
      Bundle bundle = new Bundle();
      bundle.putString(ARGUMENT.BARCODE, barcode);
      sendEvent(Event.CHOOSE_PRODUCT, bundle);
    }
  }

  public void checkProductInput() {
    formData.isProductNameValid();
    String input = formData.getProductNameLive().getValue();
    if (input == null || input.isEmpty()) {
      return;
    }
    Product product = Product.getProductFromName(products, input);

    GrocycodeUtil.Grocycode grocycode = GrocycodeUtil.getGrocycode(input.trim());
    if (grocycode != null && grocycode.isProduct()) {
      product = Product.getProductFromId(products, grocycode.getObjectId());
      if (product == null) {
        showMessageAndContinueScanning(R.string.msg_not_found);
        return;
      }
    } else if (grocycode != null) {
      showMessageAndContinueScanning(R.string.error_wrong_grocycode_type);
      return;
    }
    if (product == null) {
      ProductBarcode productBarcode = null;
      for (ProductBarcode code : productBarcodes) {
        if (code.getBarcode().equals(input.trim())) {
          productBarcode = code;
          product = Product.getProductFromId(products, code.getProductIdInt());
        }
      }
      if (product != null) {
        setProduct(product.getId(), productBarcode, null);
        return;
      }
    }

    ProductDetails currentProductDetails = formData.getProductDetailsLive().getValue();
    Product currentProduct = currentProductDetails != null
            ? currentProductDetails.getProduct() : null;
    if (currentProduct != null && product != null && currentProduct.getId() == product.getId()) {
      return;
    }

    if (product != null) {
      setProduct(product.getId(), null, null);
    } else {
      showInputProductBottomSheet(input);
    }
  }

  public void saveEntry() {
    if (!formData.isFormValid()) {
      showMessage(R.string.error_missing_information);
      return;
    }

    Recipe recipe = null;
    if (isActionEdit) {
      recipe = args.getRecipe();
    }
    recipe = formData.fillRecipe(recipe);
    JSONObject jsonObject = Recipe.getJsonFromRecipe(recipe, debug, TAG);

    if (isActionEdit) {
      dlHelper.put(
          grocyApi.getObject(ENTITY.RECIPES, recipe.getId()),
          jsonObject,
          response -> navigateUp(),
          error -> {
            showErrorMessage(error);
            if (debug) {
              Log.e(TAG, "saveEntry: " + error);
            }
          }
      );
    } else {
      dlHelper.post(
          grocyApi.getObjects(ENTITY.RECIPES),
          jsonObject,
          response -> navigateUp(),
          error -> {
            showErrorMessage(error);
            if (debug) {
              Log.e(TAG, "saveEntry: " + error);
            }
          }
      );
    }
  }

  private void fillWithRecipeIfNecessary() {
    if (!isActionEdit || formData.isFilledWithRecipe()) {
      return;
    }

    Recipe entry = args.getRecipe();
    assert entry != null;

    formData.getNameLive().setValue(entry.getName());
    formData.getBaseServingsLive().setValue(String.valueOf(entry.getBaseServings()));
    formData.getNotCheckShoppingListLive().setValue(entry.isNotCheckShoppingList());
    formData.getProductsLive().setValue(Product.getActiveProductsOnly(products));
    formData.getDescriptionLive().setValue(entry.getDescription());

    formData.setFilledWithRecipe(true);
  }

  public void deleteEntry() {
    if (!isActionEdit()) {
      return;
    }
    Recipe recipe = args.getRecipe();
    assert recipe != null;
    dlHelper.delete(
        grocyApi.getObject(
            ENTITY.RECIPES,
            recipe.getId()
        ),
        response -> navigateUp(),
        this::showErrorMessage
    );
  }

  public void showInputProductBottomSheet(@NonNull String input) {
    Bundle bundle = new Bundle();
    bundle.putString(Constants.ARGUMENT.PRODUCT_INPUT, input);
    showBottomSheet(new InputProductBottomSheet(), bundle);
  }

  private void showMessageAndContinueScanning(String msg) {
    formData.clearForm();
    showMessage(msg);
    sendEvent(Event.CONTINUE_SCANNING);
  }

  private void showMessageAndContinueScanning(@StringRes int msg) {
    showMessageAndContinueScanning(getString(msg));
  }

  public boolean isActionEdit() {
    return isActionEdit;
  }

  @NonNull
  public MutableLiveData<Boolean> getOfflineLive() {
    return offlineLive;
  }

  public Boolean isOffline() {
    return offlineLive.getValue();
  }

  public void setOfflineLive(boolean isOffline) {
    offlineLive.setValue(isOffline);
  }

  @NonNull
  public MutableLiveData<Boolean> getIsLoadingLive() {
    return isLoadingLive;
  }

  @NonNull
  public MutableLiveData<InfoFullscreen> getInfoFullscreenLive() {
    return infoFullscreenLive;
  }

  public boolean isFeatureEnabled(String pref) {
    if (pref == null) {
      return true;
    }
    return sharedPrefs.getBoolean(pref, true);
  }

  @Override
  protected void onCleared() {
    dlHelper.destroy();
    super.onCleared();
  }

  public static class RecipeEditViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;
    private final RecipeEditFragmentArgs args;

    public RecipeEditViewModelFactory(
        Application application,
        RecipeEditFragmentArgs args
    ) {
      this.application = application;
      this.args = args;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new RecipeEditViewModel(application, args);
    }
  }
}
