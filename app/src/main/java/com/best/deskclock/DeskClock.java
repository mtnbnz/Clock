/*
 * Copyright (C) 2009 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING;
import static androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE;
import static androidx.viewpager.widget.ViewPager.SCROLL_STATE_SETTLING;
import static com.best.alarmclock.WidgetUtils.ACTION_NEXT_ALARM_LABEL_CHANGED;
import static com.best.deskclock.DeskClockApplication.getDefaultSharedPreferences;
import static com.best.deskclock.settings.PermissionsManagementActivity.PermissionsManagementFragment.areEssentialPermissionsNotGranted;
import static com.best.deskclock.settings.PreferencesDefaultValues.AMOLED_DARK_MODE;
import static com.best.deskclock.settings.PreferencesDefaultValues.DEFAULT_TAB_TITLE_VISIBILITY;
import static com.best.deskclock.settings.PreferencesDefaultValues.TAB_TITLE_VISIBILITY_NEVER;
import static com.best.deskclock.utils.AnimatorUtils.getScaleAnimator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

import com.best.deskclock.alarms.AlarmVolumeDialogFragment;
import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.DataModel.SilentSetting;
import com.best.deskclock.data.OnSilentSettingsListener;
import com.best.deskclock.data.SettingsDAO;
import com.best.deskclock.events.Events;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.settings.PermissionsManagementActivity;
import com.best.deskclock.settings.SettingsActivity;
import com.best.deskclock.stopwatch.StopwatchService;
import com.best.deskclock.timer.TimerService;
import com.best.deskclock.uidata.TabListener;
import com.best.deskclock.uidata.UiDataModel;
import com.best.deskclock.utils.InsetsUtils;
import com.best.deskclock.utils.ThemeUtils;
import com.best.deskclock.widget.toast.SnackbarManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.snackbar.Snackbar;

/**
 * The main activity of the application which displays 4 different tabs contains alarms, world
 * clocks, timers and stopwatch.
 */
public class DeskClock extends AppCompatActivity
        implements FabContainer, LabelDialogFragment.AlarmLabelDialogHandler,
        AutoSilenceDurationDialogFragment.AutoSilenceDurationDialogHandler,
        AlarmSnoozeDurationDialogFragment.SnoozeDurationDialogHandler,
        VolumeCrescendoDurationDialogFragment.VolumeCrescendoDurationDialogHandler,
        AlarmVolumeDialogFragment.VolumeValueDialogHandler {

    public static final int REQUEST_CHANGE_SETTINGS = 10;
    public static final int REQUEST_CHANGE_PERMISSIONS = 20;

    SharedPreferences mPrefs;

    /**
     * Shrinks the {@link #mFab}, {@link #mLeftButton} and {@link #mRightButton} to nothing.
     */
    private final AnimatorSet mHideAnimation = new AnimatorSet();

    /**
     * Grows the {@link #mFab}, {@link #mLeftButton} and {@link #mRightButton} to natural sizes.
     */
    private final AnimatorSet mShowAnimation = new AnimatorSet();

    /**
     * Hides, updates, and shows only the {@link #mFab}; the buttons are untouched.
     */
    private final AnimatorSet mUpdateFabOnlyAnimation = new AnimatorSet();

    /**
     * Hides, updates, and shows only the {@link #mLeftButton} and {@link #mRightButton}.
     */
    private final AnimatorSet mUpdateButtonsOnlyAnimation = new AnimatorSet();

    /**
     * Automatically starts the {@link #mShowAnimation} after {@link #mHideAnimation} ends.
     */
    private final AnimatorListenerAdapter mAutoStartShowListener = new AutoStartShowListener();

    /**
     * Updates the user interface to reflect the selected tab from the backing model.
     */
    private final TabListener mTabChangeWatcher = new TabChangeWatcher();

    /**
     * Shows/hides a snackbar explaining which setting is suppressing alarms from firing.
     */
    private final OnSilentSettingsListener mSilentSettingChangeWatcher = new SilentSettingChangeWatcher();

    /**
     * Displays a snackbar explaining why alarms may not fire or may fire silently.
     */
    private Runnable mShowSilentSettingSnackbarRunnable;

    /**
     * The main activity view.
     */
    private View mDeskClockRootView;

    /**
     * The view to which snackbar items are anchored.
     */
    private View mSnackbarAnchor;

    /**
     * The current display state of the {@link #mFab}.
     */
    private FabState mFabState = FabState.SHOWING;

    /**
     * The single floating-action button shared across all tabs in the user interface.
     */
    private ImageView mFab;

    /**
     * The button left of the {@link #mFab} shared across all tabs in the user interface.
     */
    private ImageView mLeftButton;

    /**
     * The button right of the {@link #mFab} shared across all tabs in the user interface.
     */
    private ImageView mRightButton;

    /**
     * The Toolbar to display the title of the different tabs.
     */
    private Toolbar mToolbar;

    /**
     * The ViewPager that pages through the fragments representing the content of the tabs.
     */
    private ViewPager mFragmentTabPager;

    /**
     * Generates the fragments that are displayed by the {@link #mFragmentTabPager}.
     */
    private FragmentTabPagerAdapter mFragmentTabPagerAdapter;

    /**
     * The bottom navigation bar
     */
    private BottomNavigationView mBottomNavigation;

    /**
     * {@code true} when a settings change necessitates recreating this activity.
     */
    private boolean mRecreateActivity;

    /**
     * Callback for getting the result from the Settings activity.
     */
    private final ActivityResultLauncher<Intent> getSettingsActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), (result) -> {
                if (result.getResultCode() != REQUEST_CHANGE_SETTINGS) {
                    return;
                }
                mRecreateActivity = true;
            });

    /**
     * Callback for getting the result from the Permission Management activity.
     */
    private final ActivityResultLauncher<Intent> getPermissionManagementActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), (result) -> {
                if (result.getResultCode() != REQUEST_CHANGE_PERMISSIONS) {
                    return;
                }
                mRecreateActivity = true;
            });

    @Override
    public void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);

        // Fragments may query the latest intent for information, so update the intent.
        setIntent(newIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getDefaultSharedPreferences(this);

        if (isFirstLaunch()) {
            return;
        }

        // To manually manage insets
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ThemeUtils.allowDisplayCutout(getWindow());

        setContentView(R.layout.desk_clock);

        mDeskClockRootView = findViewById(R.id.desk_clock_root_view);

        mSnackbarAnchor = findViewById(R.id.content);

        // Configure the toolbar.
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        // Configure the buttons shared by the tabs.
        final boolean isTablet = ThemeUtils.isTablet();
        final boolean isPortrait = ThemeUtils.isPortrait();
        final int fabSize = isTablet ? 90 : isPortrait ? 75 : 60;
        final int leftOrRightButtonSize = isTablet ? 70 : isPortrait ? 55 : 50;

        mFab = findViewById(R.id.fab);
        mFab.getLayoutParams().height = ThemeUtils.convertDpToPixels(fabSize, this);
        mFab.getLayoutParams().width = ThemeUtils.convertDpToPixels(fabSize, this);
        mFab.setScaleType(ImageView.ScaleType.CENTER);
        mFab.setOnClickListener(view -> getSelectedDeskClockFragment().onFabClick(mFab));

        mLeftButton = findViewById(R.id.left_button);
        mLeftButton.getLayoutParams().height = ThemeUtils.convertDpToPixels(leftOrRightButtonSize, this);
        mLeftButton.getLayoutParams().width = ThemeUtils.convertDpToPixels(leftOrRightButtonSize, this);
        mLeftButton.setScaleType(ImageView.ScaleType.CENTER);

        mRightButton = findViewById(R.id.right_button);
        mRightButton.getLayoutParams().height = ThemeUtils.convertDpToPixels(leftOrRightButtonSize, this);
        mRightButton.getLayoutParams().width = ThemeUtils.convertDpToPixels(leftOrRightButtonSize, this);
        mRightButton.setScaleType(ImageView.ScaleType.CENTER);

        final long duration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        final ValueAnimator hideFabAnimation = getScaleAnimator(mFab, 1f, 0f);
        final ValueAnimator showFabAnimation = getScaleAnimator(mFab, 0f, 1f);

        final ValueAnimator leftHideAnimation = getScaleAnimator(mLeftButton, 1f, 0f);
        final ValueAnimator rightHideAnimation = getScaleAnimator(mRightButton, 1f, 0f);
        final ValueAnimator leftShowAnimation = getScaleAnimator(mLeftButton, 0f, 1f);
        final ValueAnimator rightShowAnimation = getScaleAnimator(mRightButton, 0f, 1f);

        hideFabAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                getSelectedDeskClockFragment().onUpdateFab(mFab);
            }
        });

        leftHideAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                getSelectedDeskClockFragment().onUpdateFabButtons(mLeftButton, mRightButton);
            }
        });

        // Build the reusable animations that hide and show the fab and left/right buttons.
        // These may be used independently or be chained together.
        mHideAnimation
                .setDuration(duration)
                .play(hideFabAnimation)
                .with(leftHideAnimation)
                .with(rightHideAnimation);

        mShowAnimation
                .setDuration(duration)
                .play(showFabAnimation)
                .with(leftShowAnimation)
                .with(rightShowAnimation);

        // Build the reusable animation that hides and shows only the fab.
        mUpdateFabOnlyAnimation
                .setDuration(duration)
                .play(showFabAnimation)
                .after(hideFabAnimation);

        // Build the reusable animation that hides and shows only the buttons.
        mUpdateButtonsOnlyAnimation
                .setDuration(duration)
                .play(leftShowAnimation)
                .with(rightShowAnimation)
                .after(leftHideAnimation)
                .after(rightHideAnimation);

        // Customize the view pager.
        mFragmentTabPagerAdapter = new FragmentTabPagerAdapter(this);
        mFragmentTabPager = findViewById(R.id.desk_clock_pager);
        // Keep all four tabs to minimize jank.
        mFragmentTabPager.setOffscreenPageLimit(3);
        // Set Accessibility Delegate to null so view pager doesn't intercept movements and
        // prevent the fab from being selected.
        mFragmentTabPager.setAccessibilityDelegate(null);
        // Mirror changes made to the selected page of the view pager into UiDataModel.
        mFragmentTabPager.addOnPageChangeListener(new PageChangeWatcher());
        mFragmentTabPager.setAdapter(mFragmentTabPagerAdapter);

        // Mirror changes made to the selected tab into UiDataModel.
        final int primaryColor = MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorPrimary, Color.BLACK);
        final int surfaceColor = MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurface, Color.BLACK);
        final int onBackgroundColor = MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnBackground, Color.BLACK);

        mBottomNavigation = findViewById(R.id.bottom_view);
        mBottomNavigation.setOnItemSelectedListener(mNavigationListener);
        mBottomNavigation.setItemActiveIndicatorEnabled(SettingsDAO.isTabIndicatorDisplayed(mPrefs));

        if (SettingsDAO.getTabTitleVisibility(mPrefs).equals(DEFAULT_TAB_TITLE_VISIBILITY)) {
            mBottomNavigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        } else if (SettingsDAO.getTabTitleVisibility(mPrefs).equals(TAB_TITLE_VISIBILITY_NEVER)) {
            mBottomNavigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_UNLABELED);
        } else {
            mBottomNavigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_SELECTED);
        }

        mBottomNavigation.setItemIconTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_selected}, {android.R.attr.state_pressed}, {}},
                new int[]{primaryColor, primaryColor, onBackgroundColor}));

        if (ThemeUtils.isNight(getResources()) && SettingsDAO.getDarkMode(mPrefs).equals(AMOLED_DARK_MODE)) {
            mBottomNavigation.setBackgroundColor(Color.BLACK);
            mBottomNavigation.setItemTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_selected}, {android.R.attr.state_pressed}, {}},
                    new int[]{primaryColor, primaryColor, Color.WHITE}));
        } else {
            final boolean isCardBackgroundDisplayed = SettingsDAO.isCardBackgroundDisplayed(mPrefs);
            if (isCardBackgroundDisplayed) {
                mBottomNavigation.setBackgroundColor(surfaceColor);
            } else {
                mBottomNavigation.setBackgroundColor(Color.TRANSPARENT);
            }
            mBottomNavigation.setItemTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_selected}, {android.R.attr.state_pressed}, {}},
                    new int[]{primaryColor, primaryColor, onBackgroundColor}));
        }

        applyWindowInsets();

        // Honor changes to the selected tab from outside entities.
        UiDataModel.getUiDataModel().addTabListener(mTabChangeWatcher);
    }

    @Override
    protected void onStart() {
        DataModel.getDataModel().addSilentSettingsListener(mSilentSettingChangeWatcher);
        DataModel.getDataModel().setApplicationInForeground(true);

        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        showTabFromNotifications();

        // ViewPager does not save state; this honors the selected tab in the user interface.
        updateCurrentTab();

        updateKeepScreenOn(UiDataModel.getUiDataModel().getSelectedTab());
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (mRecreateActivity) {
            mRecreateActivity = false;

            // A runnable must be posted here or the new DeskClock activity will be recreated in a
            // paused state, even though it is the foreground activity.
            mFragmentTabPager.post(this::recreate);
        }
    }

    @Override
    protected void onStop() {
        DataModel.getDataModel().removeSilentSettingsListener(mSilentSettingChangeWatcher);
        if (!isChangingConfigurations()) {
            DataModel.getDataModel().setApplicationInForeground(false);
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        UiDataModel.getUiDataModel().removeTabListener(mTabChangeWatcher);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, Menu.NONE, 1, R.string.settings)
                .setIcon(R.drawable.ic_settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        if (areEssentialPermissionsNotGranted(this)) {
            final Drawable warningIcon = AppCompatResources.getDrawable(this, R.drawable.ic_error);
            if (warningIcon != null) {
                DrawableCompat.setTint(warningIcon, this.getColor(R.color.colorAlert));
            }
            menu.add(0, Menu.FIRST, 0, R.string.denied_permission_label)
                    .setIcon(warningIcon).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {
            final Intent settingIntent = new Intent(this, SettingsActivity.class);
            getSettingsActivity.launch(settingIntent);
            return true;
        }

        if (item.getItemId() == 1) {
            final Intent permissionManagementIntent = new Intent(this, PermissionsManagementActivity.class);
            getPermissionManagementActivity.launch(permissionManagementIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Called by the LabelDialogFormat class after the dialog is finished.
     */
    @Override
    public void onDialogLabelSet(Alarm alarm, String label, String tag) {
        final Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setLabel(alarm, label);
            // Update the alarm title in the “Next alarm” widget
            sendBroadcast(new Intent(ACTION_NEXT_ALARM_LABEL_CHANGED));
        }
    }

    /**
     * Called by the AutoSilenceDurationDialogFragment class after the dialog is finished.
     */
    @Override
    public void onDialogAutoSilenceDurationSet(Alarm alarm, int silenceAfterDuration, String tag) {
        final Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setAutoSilenceDuration(alarm, silenceAfterDuration);
        }
    }

    /**
     * Called by the AlarmSnoozeDurationDialogFragment class after the dialog is finished.
     */
    @Override
    public void onDialogSnoozeDurationSet(Alarm alarm, int snoozeDuration, String tag) {
        final Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setSnoozeDuration(alarm, snoozeDuration);
        }
    }

    /**
     * Called by the VolumeCrescendoDurationDialogFragment class after the dialog is finished.
     */
    @Override
    public void onDialogCrescendoDurationSet(Alarm alarm, int crescendoDuration, String tag) {
        final Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setCrescendoDuration(alarm, crescendoDuration);
        }
    }

    /**
     * Called by the AlarmVolumeDialogFragment class after the dialog is finished.
     */
    @Override
    public void onVolumeValueSet(Alarm alarm, int volumeValue, String tag) {
        final Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setAlarmVolume(alarm, volumeValue);
        }
    }

    /**
     * Listens for keyboard activity for the tab fragments to handle if necessary. A tab may want to
     * respond to key presses even if they are not currently focused.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return getSelectedDeskClockFragment().onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return getSelectedDeskClockFragment().onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    @Override
    public void updateFab(@UpdateFabFlag int updateType) {
        final DeskClockFragment f = getSelectedDeskClockFragment();

        switch (updateType & FAB_ANIMATION_MASK) {
            case FAB_SHRINK_AND_EXPAND -> mUpdateFabOnlyAnimation.start();
            case FAB_IMMEDIATE -> f.onUpdateFab(mFab);
            case FAB_MORPH -> f.onMorphFab(mFab);
        }
        if ((updateType & FAB_REQUEST_FOCUS_MASK) == FAB_REQUEST_FOCUS) {
            mFab.requestFocus();
        }
        switch (updateType & BUTTONS_ANIMATION_MASK) {
            case BUTTONS_IMMEDIATE -> f.onUpdateFabButtons(mLeftButton, mRightButton);
            case BUTTONS_SHRINK_AND_EXPAND -> mUpdateButtonsOnlyAnimation.start();
        }
        if ((updateType & BUTTONS_DISABLE_MASK) == BUTTONS_DISABLE) {
            mLeftButton.setClickable(false);
            mRightButton.setClickable(false);
        }
        switch (updateType & FAB_AND_BUTTONS_SHRINK_EXPAND_MASK) {
            case FAB_AND_BUTTONS_SHRINK -> mHideAnimation.start();
            case FAB_AND_BUTTONS_EXPAND -> mShowAnimation.start();
        }
    }

    /**
     * Check if this is the first time the application has been launched.
     */
    private boolean isFirstLaunch() {
        final boolean isFirstRun = mPrefs.getBoolean(FirstLaunch.KEY_IS_FIRST_LAUNCH, true);
        if (isFirstRun) {
            startActivity(new Intent(this, FirstLaunch.class));
            finish();
            return true;
        }
        return false;
    }

    private final NavigationBarView.OnItemSelectedListener mNavigationListener = item -> {
        UiDataModel.Tab tab = null;
        int itemId = item.getItemId();
        if (itemId == R.id.page_alarm) {
            tab = UiDataModel.Tab.ALARMS;
        } else if (itemId == R.id.page_clock) {
            tab = UiDataModel.Tab.CLOCKS;
        } else if (itemId == R.id.page_timer) {
            tab = UiDataModel.Tab.TIMERS;
        } else if (itemId == R.id.page_stopwatch) {
            tab = UiDataModel.Tab.STOPWATCH;
        }

        if (tab != null) {
            UiDataModel.getUiDataModel().setSelectedTab(tab);
            return true;
        }

        return false;
    };

    /**
     * This method adjusts the space occupied by system elements (such as the status bar,
     * navigation bar or screen notch) and adjust the display of the application interface
     * accordingly.
     */
    private void applyWindowInsets() {
        InsetsUtils.doOnApplyWindowInsets(mDeskClockRootView, (v, insets, initialPadding) -> {
            // Get the system bar and notch insets
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() |
                    WindowInsetsCompat.Type.displayCutout());

            v.setPadding(bars.left, bars.top, bars.right, 0);

            mBottomNavigation.setPadding(0, 0, 0, bars.bottom);
        });
    }

    /**
     * Displays the right tab if the application has been closed and then reopened from the notification.
     */
    private void showTabFromNotifications() {
        final Intent intent = getIntent();
        if (intent != null) {
            final String action = intent.getAction();
            if (action != null) {
                int label = intent.getIntExtra(Events.EXTRA_EVENT_LABEL, R.string.label_intent);
                switch (action) {
                    case TimerService.ACTION_SHOW_TIMER -> {
                        Events.sendTimerEvent(R.string.action_show, label);
                        UiDataModel.getUiDataModel().setSelectedTab(UiDataModel.Tab.TIMERS);
                    }
                    case StopwatchService.ACTION_SHOW_STOPWATCH -> {
                        Events.sendStopwatchEvent(R.string.action_show, label);
                        UiDataModel.getUiDataModel().setSelectedTab(UiDataModel.Tab.STOPWATCH);
                    }
                }
            }
        }
    }

    /**
     * Configure the {@link #mBottomNavigation} to display UiDataModel's selected tab.
     */
    @SuppressLint("ResourceType")
    private void updateCurrentTab() {
        // Fetch the selected tab from the source of truth: UiDataModel.
        final UiDataModel.Tab selectedTab = UiDataModel.getUiDataModel().getSelectedTab();
        // Update the selected tab in the mBottomNavigation if it does not agree with UiDataModel.
        mBottomNavigation.setSelectedItemId(selectedTab.getPageResId());

        // Update the selected fragment in the viewpager if it does not agree with UiDataModel.
        for (int i = 0; i < mFragmentTabPagerAdapter.getCount(); i++) {
            final DeskClockFragment fragment = mFragmentTabPagerAdapter.getDeskClockFragment(i);
            if (fragment.isTabSelected() && mFragmentTabPager.getCurrentItem() != i) {
                mFragmentTabPager.setCurrentItem(i);
                break;
            }
        }

        if (SettingsDAO.isToolbarTitleDisplayed(mPrefs)) {
            mToolbar.setTitle(selectedTab.getLabelResId());
        } else {
            mToolbar.setTitle(null);
        }
    }

    /**
     * Update screen state based on selected tab and user preferences.
     * <p>
     * If the active tab requires the screen to remain on (e.g. timer in progress, stopwatch active,
     * or user preference enabled), apply the {@code FLAG_KEEP_SCREEN_ON} flag.
     *
     * @param selectedTab The currently selected tab.
     */
    private void updateKeepScreenOn(UiDataModel.Tab selectedTab) {
        final boolean screenShouldStayOn;

        switch (selectedTab) {
            case ALARMS, CLOCKS ->
                screenShouldStayOn = SettingsDAO.shouldScreenRemainOn(mPrefs);

            case TIMERS ->
                screenShouldStayOn = DataModel.getDataModel().hasActiveTimer()
                        || SettingsDAO.shouldScreenRemainOn(mPrefs);

            case STOPWATCH ->
                screenShouldStayOn = DataModel.getDataModel().getStopwatch().isRunning()
                        || SettingsDAO.shouldScreenRemainOn(mPrefs);

            default -> screenShouldStayOn = false;

        }

        if (screenShouldStayOn) {
            ThemeUtils.keepScreenOn(this);
        } else {
            ThemeUtils.releaseKeepScreenOn(this);
        }
    }

    /**
     * @return the DeskClockFragment that is currently selected according to UiDataModel
     */
    private DeskClockFragment getSelectedDeskClockFragment() {
        for (int i = 0; i < mFragmentTabPagerAdapter.getCount(); i++) {
            final DeskClockFragment fragment = mFragmentTabPagerAdapter.getDeskClockFragment(i);
            if (fragment.isTabSelected()) {
                return fragment;
            }
        }
        final UiDataModel.Tab selectedTab = UiDataModel.getUiDataModel().getSelectedTab();
        throw new IllegalStateException("Unable to locate selected fragment (" + selectedTab + ")");
    }

    /**
     * @return a Snackbar that displays the message with the given id for 5 seconds
     */
    private Snackbar createSnackbar(@StringRes int messageId) {
        return Snackbar.make(mSnackbarAnchor, messageId, 5000);
    }

    /**
     * Models the interesting state of display the {@link #mFab} button may inhabit.
     */
    private enum FabState {SHOWING, HIDE_ARMED, HIDING}

    /**
     * As the view pager changes the selected page, update the model to record the new selected tab.
     */
    private final class PageChangeWatcher implements OnPageChangeListener {

        /**
         * The last reported page scroll state; used to detect exotic state changes.
         */
        private int mPriorState = SCROLL_STATE_IDLE;

        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // Only hide the fab when a non-zero drag distance is detected. This prevents
            // over-scrolling from needlessly hiding the fab.
            if (mFabState == FabState.HIDE_ARMED && positionOffsetPixels != 0) {
                mFabState = FabState.HIDING;
                mHideAnimation.start();
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (mPriorState == SCROLL_STATE_IDLE && state == SCROLL_STATE_SETTLING) {
                // The user has tapped a tab button; play the hide and show animations linearly.
                mHideAnimation.addListener(mAutoStartShowListener);
                mHideAnimation.start();
                mFabState = FabState.HIDING;
            } else if (mPriorState == SCROLL_STATE_SETTLING && state == SCROLL_STATE_DRAGGING) {
                // The user has interrupted settling on a tab and the fab button must be re-hidden.
                if (mShowAnimation.isStarted()) {
                    mShowAnimation.cancel();
                }
                if (mHideAnimation.isStarted()) {
                    // Let the hide animation finish naturally; don't auto show when it ends.
                    mHideAnimation.removeListener(mAutoStartShowListener);
                } else {
                    // Start and immediately end the hide animation to jump to the hidden state.
                    mHideAnimation.start();
                    mHideAnimation.end();
                }
                mFabState = FabState.HIDING;

            } else if (state != SCROLL_STATE_DRAGGING && mFabState == FabState.HIDING) {
                // The user has lifted their finger; show the buttons now or after hide ends.
                if (mHideAnimation.isStarted()) {
                    // Finish the hide animation and then start the show animation.
                    mHideAnimation.addListener(mAutoStartShowListener);
                } else {
                    updateFab(FAB_AND_BUTTONS_IMMEDIATE);
                    mShowAnimation.start();

                    // The animation to show the fab has begun; update the state to showing.
                    mFabState = FabState.SHOWING;
                }
            } else if (state == SCROLL_STATE_DRAGGING) {
                // The user has started a drag so arm the hide animation.
                mFabState = FabState.HIDE_ARMED;
            }

            // Update the last known state.
            mPriorState = state;
        }

        @Override
        public void onPageSelected(int position) {
            mFragmentTabPagerAdapter.getDeskClockFragment(position).selectTab();
        }
    }

    /**
     * If this listener is attached to {@link #mHideAnimation} when it ends, the corresponding
     * {@link #mShowAnimation} is automatically started.
     */
    private final class AutoStartShowListener extends AnimatorListenerAdapter {
        @Override
        public void onAnimationEnd(Animator animation) {
            // Prepare the hide animation for its next use; by default do not auto-show after hide.
            mHideAnimation.removeListener(mAutoStartShowListener);

            // Update the buttons now that they are no longer visible.
            updateFab(FAB_AND_BUTTONS_IMMEDIATE);

            // Automatically start the grow animation now that shrinking is complete.
            mShowAnimation.start();

            // The animation to show the fab has begun; update the state to showing.
            mFabState = FabState.SHOWING;
        }
    }

    /**
     * Shows/hides a snackbar as silencing settings are enabled/disabled.
     */
    private final class SilentSettingChangeWatcher implements OnSilentSettingsListener {
        @Override
        public void onSilentSettingsChange(SilentSetting after) {
            if (mShowSilentSettingSnackbarRunnable != null) {
                mSnackbarAnchor.removeCallbacks(mShowSilentSettingSnackbarRunnable);
                mShowSilentSettingSnackbarRunnable = null;
            }

            if (after == null) {
                SnackbarManager.dismiss();
            } else {
                mShowSilentSettingSnackbarRunnable = new ShowSilentSettingSnackbarRunnable(after);
                mSnackbarAnchor.postDelayed(mShowSilentSettingSnackbarRunnable, SECOND_IN_MILLIS);
            }
        }
    }

    /**
     * Displays a snackbar that indicates a system setting is currently silencing alarms.
     */
    private final class ShowSilentSettingSnackbarRunnable implements Runnable {

        private final SilentSetting mSilentSetting;

        private ShowSilentSettingSnackbarRunnable(SilentSetting silentSetting) {
            mSilentSetting = silentSetting;
        }

        public void run() {
            // Create a snackbar with a message explaining the setting that is silencing alarms.
            final Snackbar snackbar = createSnackbar(mSilentSetting.getLabelResId());

            // Set the associated corrective action if one exists.
            if (mSilentSetting.isActionEnabled(DeskClock.this)) {
                final int actionResId = mSilentSetting.getActionResId();
                snackbar.setAction(actionResId, mSilentSetting.getActionListener());
            }

            SnackbarManager.show(snackbar);
        }
    }

    /**
     * As the model reports changes to the selected tab, update the user interface.
     */
    private final class TabChangeWatcher implements TabListener {
        @Override
        public void selectedTabChanged(UiDataModel.Tab newSelectedTab) {
            // Update the view pager and tab layout to agree with the model.
            updateCurrentTab();

            // Avoid sending events for the initial tab selection on launch and re-selecting a tab
            // after a configuration change.
            if (DataModel.getDataModel().isApplicationInForeground()) {
                updateKeepScreenOn(newSelectedTab);

                switch (newSelectedTab) {
                    case ALARMS -> Events.sendAlarmEvent(R.string.action_show, R.string.label_deskclock);
                    case CLOCKS -> Events.sendClockEvent(R.string.action_show, R.string.label_deskclock);
                    case TIMERS -> Events.sendTimerEvent(R.string.action_show, R.string.label_deskclock);
                    case STOPWATCH -> Events.sendStopwatchEvent(R.string.action_show, R.string.label_deskclock);
                }
            }

            // If the hide animation has already completed, the buttons must be updated now when the
            // new tab is known. Otherwise they are updated at the end of the hide animation.
            if (!mHideAnimation.isStarted()) {
                updateFab(FAB_AND_BUTTONS_IMMEDIATE);
            }
        }
    }
}
