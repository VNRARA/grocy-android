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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2023 by Patrick Zedler and Dominic Zedler
 */

package xyz.zedler.patrick.grocy.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import java.util.ArrayList;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.Constants.ACTION;
import xyz.zedler.patrick.grocy.Constants.ARGUMENT;
import xyz.zedler.patrick.grocy.Constants.PREF;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.MasterPlaceholderAdapter;
import xyz.zedler.patrick.grocy.adapter.RecipeEntryAdapter;
import xyz.zedler.patrick.grocy.behavior.AppBarBehavior;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentRecipesBinding;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.RecipeBottomSheet;
import xyz.zedler.patrick.grocy.helper.InfoFullscreenHelper;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.InfoFullscreen;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.model.QuantityUnitConversion;
import xyz.zedler.patrick.grocy.model.Recipe;
import xyz.zedler.patrick.grocy.model.RecipeFulfillment;
import xyz.zedler.patrick.grocy.model.RecipePosition;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.util.ClickUtil;
import xyz.zedler.patrick.grocy.util.ViewUtil;
import xyz.zedler.patrick.grocy.viewmodel.RecipesViewModel;

public class RecipesFragment extends BaseFragment implements
        RecipeEntryAdapter.RecipesItemAdapterListener {

  private final static String TAG = TasksFragment.class.getSimpleName();
  private final static String LAYOUT_LINEAR = "linear";
  private final static String LAYOUT_GRID = "grid";

  private MainActivity activity;
  private RecipesViewModel viewModel;
  private AppBarBehavior appBarBehavior;
  private ClickUtil clickUtil;
  private FragmentRecipesBinding binding;
  private InfoFullscreenHelper infoFullscreenHelper;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState
  ) {
    binding = FragmentRecipesBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    if (infoFullscreenHelper != null) {
      infoFullscreenHelper.destroyInstance();
      infoFullscreenHelper = null;
    }
    if (binding != null) {
      binding.recycler.animate().cancel();
      binding.recycler.setAdapter(null);
      binding = null;
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(RecipesViewModel.class);
    viewModel.setOfflineLive(!activity.isOnline());
    binding.setViewModel(viewModel);
    binding.setActivity(activity);
    binding.setFragment(this);
    binding.setLifecycleOwner(getViewLifecycleOwner());

    infoFullscreenHelper = new InfoFullscreenHelper(binding.frame);
    clickUtil = new ClickUtil();

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setContainer(binding.swipe);
    systemBarBehavior.setRecycler(binding.recycler);
    systemBarBehavior.applyAppBarInsetOnContainer(false);
    systemBarBehavior.applyStatusBarInsetOnContainer(false);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

    binding.toolbarDefault.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());

    // APP BAR BEHAVIOR

    appBarBehavior = new AppBarBehavior(
        activity,
        binding.appBarDefault,
        binding.appBarSearch,
        savedInstanceState
    );

    boolean isGrid = viewModel.getSharedPrefs()
        .getString(PREF.RECIPES_LIST_LAYOUT, LAYOUT_LINEAR).equals(LAYOUT_LINEAR);
    if (isGrid) {
      binding.recycler.setLayoutManager(
          new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
      );
    } else {
      binding.recycler.setLayoutManager(
          new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
      );
    }

    binding.recycler.setAdapter(new MasterPlaceholderAdapter());

    if (savedInstanceState == null) {
      binding.recycler.scrollToPosition(0);
      viewModel.resetSearch();
    }

    viewModel.getInfoFullscreenLive().observe(
        getViewLifecycleOwner(),
        infoFullscreen -> infoFullscreenHelper.setInfo(infoFullscreen)
    );

    viewModel.getFilteredRecipesLive().observe(getViewLifecycleOwner(), items -> {
      if (items == null) {
        return;
      }
      if (items.isEmpty()) {
        InfoFullscreen info;
        if (viewModel.isSearchActive()) {
          info = new InfoFullscreen(InfoFullscreen.INFO_NO_SEARCH_RESULTS);
        } else if (viewModel.getFilterChipLiveDataStatus().getData().isActive()) {
          info = new InfoFullscreen(InfoFullscreen.INFO_NO_FILTER_RESULTS);
        } else {
          info = new InfoFullscreen(InfoFullscreen.INFO_EMPTY_RECIPES);
        }
        viewModel.getInfoFullscreenLive().setValue(info);
      } else {
        viewModel.getInfoFullscreenLive().setValue(null);
      }
      if (binding.recycler.getAdapter() instanceof RecipeEntryAdapter) {
        ((RecipeEntryAdapter) binding.recycler.getAdapter()).updateData(
            items,
            viewModel.getRecipeFulfillments(),
            viewModel.getSortMode(),
            viewModel.isSortAscending(),
            viewModel.getExtraField()
        );
      } else {
        binding.recycler.setAdapter(
            new RecipeEntryAdapter(
                requireContext(),
                binding.recycler.getLayoutManager(),
                items,
                viewModel.getRecipeFulfillments(),
                this,
                viewModel.getSortMode(),
                viewModel.isSortAscending(),
                viewModel.getExtraField()
            )
        );
      }
    });

    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) {
        activity.showSnackbar(
            ((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain)
        );
      }
    });

    if (savedInstanceState == null) {
      viewModel.loadFromDatabase(true);
    }

    // UPDATE UI

    activity.getScrollBehavior().setNestedOverScrollFixEnabled(true);
    activity.getScrollBehavior().setUpScroll(
        binding.appBar, false, binding.recycler, true, true
    );
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(true, R.menu.menu_recipes, this::onMenuItemClick);
    activity.updateFab(
        R.drawable.ic_round_add_anim,
        R.string.title_recipe_create,
        Constants.FAB.TAG.ADD,
        savedInstanceState == null,
        () -> activity.navUtil.navigateFragment(
            RecipesFragmentDirections.actionRecipesFragmentToRecipeEditFragment(ACTION.CREATE)
        )
    );
  }

  private void toggleLayoutManager() {
    fadeOutRecyclerView(() -> {
      RecyclerView.LayoutManager layoutManager = binding.recycler.getLayoutManager();
      if (layoutManager instanceof StaggeredGridLayoutManager) {
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false);
        binding.recycler.setLayoutManager(linearLayoutManager);
        viewModel.getSharedPrefs().edit().putString(PREF.RECIPES_LIST_LAYOUT, LAYOUT_LINEAR).apply();
      } else {
        StaggeredGridLayoutManager staggeredGridLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        binding.recycler.setLayoutManager(staggeredGridLayoutManager);
        viewModel.getSharedPrefs().edit().putString(PREF.RECIPES_LIST_LAYOUT, LAYOUT_GRID).apply();
      }
      binding.recycler.setAdapter(
          new RecipeEntryAdapter(
              requireContext(),
              binding.recycler.getLayoutManager(),
              viewModel.getFilteredRecipesLive().getValue(),
              viewModel.getRecipeFulfillments(),
              this,
              viewModel.getSortMode(),
              viewModel.isSortAscending(),
              viewModel.getExtraField()
          )
      );
      binding.recycler.scheduleLayoutAnimation();
      fadeInRecyclerView();
    });
  }


  @Override
  public void consumeRecipe(int recipeId) {
    if (showOfflineError()) {
      return;
    }
    viewModel.consumeRecipe(recipeId);
  }

  @Override
  public void addNotFulfilledProductsToCartForRecipe(int recipeId, int[] excludedProductIds) {
    if (showOfflineError()) {
      return;
    }
    viewModel.addNotFulfilledProductsToCartForRecipe(recipeId, excludedProductIds);
  }

  @Override
  public void editRecipe(Recipe recipe) {
    if (showOfflineError()) {
      return;
    }
    activity.navUtil.navigateFragment(
        RecipesFragmentDirections.actionRecipesFragmentToRecipeEditFragment(ACTION.EDIT)
            .setRecipe(recipe)
    );
  }

  @Override
  public void copyRecipe(int recipeId) {
    if (showOfflineError()) {
      return;
    }
    viewModel.copyRecipe(recipeId);
  }

  @Override
  public void deleteRecipe(int recipeId) {
    if (showOfflineError()) {
      return;
    }
    viewModel.deleteRecipe(recipeId);
  }

  @Override
  public void updateData() {
    viewModel.downloadData();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    if (appBarBehavior != null) {
      appBarBehavior.saveInstanceState(outState);
    }
  }

  private boolean showOfflineError() {
    if (viewModel.isOffline()) {
      activity.showSnackbar(R.string.error_offline, false);
      return true;
    }
    return false;
  }

  private boolean onMenuItemClick(MenuItem item) {
    if (item.getItemId() == R.id.action_search) {
      ViewUtil.startIcon(item);
      setUpSearch();
      return true;
    } else if (item.getItemId() == R.id.action_layout) {
      toggleLayoutManager();
      return true;
    }
    return false;
  }

  @Override
  public void onItemRowClicked(Recipe recipe) {
    if (clickUtil.isDisabled()) {
      return;
    }
    if (recipe == null) {
      return;
    }

    RecipeFulfillment recipeFulfillment = RecipeFulfillment.getRecipeFulfillmentFromRecipeId(viewModel.getRecipeFulfillments(), recipe.getId());
    ArrayList<RecipePosition> recipePositions = (ArrayList<RecipePosition>) RecipePosition.getRecipePositionsFromRecipeId(viewModel.getRecipePositions(), recipe.getId());
    ArrayList<Product> products = Product.getProductsForRecipePositions(viewModel.getProducts(), recipePositions);
    ArrayList<QuantityUnit> quantityUnits = QuantityUnit.getQuantityUnitsForRecipePositions(viewModel.getQuantityUnits(), recipePositions);
    ArrayList<QuantityUnitConversion> quantityUnitConversions = QuantityUnitConversion.getQuantityUnitConversionsForRecipePositions(viewModel.getQuantityUnitConversions(), recipePositions);

    Bundle bundle = new Bundle();
    bundle.putParcelable(ARGUMENT.RECIPE, recipe);
    bundle.putParcelable(ARGUMENT.RECIPE_FULFILLMENT, recipeFulfillment);
    bundle.putParcelableArrayList(ARGUMENT.RECIPE_POSITIONS, recipePositions);
    bundle.putParcelableArrayList(ARGUMENT.PRODUCTS, products);
    bundle.putParcelableArrayList(ARGUMENT.QUANTITY_UNITS, quantityUnits);
    bundle.putParcelableArrayList(ARGUMENT.QUANTITY_UNIT_CONVERSIONS, quantityUnitConversions);
    activity.showBottomSheet(new RecipeBottomSheet(), bundle);
  }

  @Override
  public void updateConnectivity(boolean isOnline) {
    if (!isOnline == viewModel.isOffline()) {
      return;
    }
    viewModel.setOfflineLive(!isOnline);
    if (isOnline) {
      viewModel.downloadData();
    }
  }

  private void setUpSearch() {
    if (!viewModel.isSearchVisible()) {
      appBarBehavior.switchToSecondary();
      binding.editTextSearch.setText("");
    }
    binding.textInputSearch.requestFocus();
    activity.showKeyboard(binding.editTextSearch);

    viewModel.setIsSearchVisible(true);
  }

  @Override
  public boolean isSearchVisible() {
    return viewModel.isSearchVisible();
  }

  @Override
  public void dismissSearch() {
    appBarBehavior.switchToPrimary();
    activity.hideKeyboard();
    binding.editTextSearch.setText("");
    viewModel.setIsSearchVisible(false);
  }

  private void fadeOutRecyclerView(final Runnable onAnimationEnd) {
    binding.recycler.animate()
        .alpha(0f)
        .setDuration(150)
        .setListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (onAnimationEnd != null) {
              onAnimationEnd.run();
            }
          }
        });
  }

  private void fadeInRecyclerView() {
    binding.recycler.setAlpha(0f);
    binding.recycler.animate()
        .alpha(1f)
        .setDuration(150)
        .setListener(null);
  }

  @NonNull
  @Override
  public String toString() {
    return TAG;
  }
}