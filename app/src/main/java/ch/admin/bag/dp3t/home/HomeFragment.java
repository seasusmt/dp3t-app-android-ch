/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package ch.admin.bag.dp3t.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.dpppt.android.sdk.TracingStatus;
import org.dpppt.android.sdk.internal.logger.Logger;

import ch.admin.bag.dp3t.BuildConfig;
import ch.admin.bag.dp3t.R;
import ch.admin.bag.dp3t.contacts.ContactsFragment;
import ch.admin.bag.dp3t.home.model.InteroperabilityMode;
import ch.admin.bag.dp3t.home.model.NotificationState;
import ch.admin.bag.dp3t.home.model.NotificationStateError;
import ch.admin.bag.dp3t.home.model.TracingState;
import ch.admin.bag.dp3t.home.views.HeaderView;
import ch.admin.bag.dp3t.interoperability.InteroperabilityActivity;
import ch.admin.bag.dp3t.interoperability.InteroperabilityPromptActivity;
import ch.admin.bag.dp3t.reports.ReportsFragment;
import ch.admin.bag.dp3t.storage.SecureStorage;
import ch.admin.bag.dp3t.util.ENExceptionHelper;
import ch.admin.bag.dp3t.util.NotificationErrorStateHelper;
import ch.admin.bag.dp3t.util.NotificationStateHelper;
import ch.admin.bag.dp3t.util.NotificationUtil;
import ch.admin.bag.dp3t.util.TracingErrorStateHelper;
import ch.admin.bag.dp3t.util.UiUtils;
import ch.admin.bag.dp3t.util.UrlUtil;
import ch.admin.bag.dp3t.viewmodel.TracingViewModel;
import ch.admin.bag.dp3t.whattodo.WtdPositiveTestFragment;
import ch.admin.bag.dp3t.whattodo.WtdSymptomsFragment;

import static android.view.View.VISIBLE;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private TracingViewModel tracingViewModel;
    private HeaderView headerView;
    private ScrollView scrollView;

    private View updatesBox;
    private View informationCard;
    private View tracingCard;
    private View cardNotifications;
    private View interoperabilityCard;
    private View reportStatusBubble;
    private View reportStatusView;
    private View reportErrorView;
    private View cardSymptomsFrame;
    private View cardTestFrame;
    private View cardSymptoms;
    private View cardTest;
    private View loadingView;

    private SecureStorage secureStorage;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        secureStorage = SecureStorage.getInstance(getContext());

        tracingViewModel = new ViewModelProvider(requireActivity()).get(TracingViewModel.class);

        getChildFragmentManager()
                .beginTransaction()
                .add(R.id.status_container, TracingBoxFragment.newInstance(true))
                .commit();

        secureStorage.getConfigInteroperabilityPossibleLiveData().observe(this, configInteroperabilityPossible -> {
            configInteroperabilityPossible = configInteroperabilityPossible && secureStorage.getConfigInteroperabilityPossible();

            //Show Interoperability prompt if required
            if (secureStorage.getOnboardingCompleted() && secureStorage.getRequirePromptDisplay() && configInteroperabilityPossible && secureStorage.getConfigInteroperabilityMode() == InteroperabilityMode.LEGACY) {
                Intent intent = new Intent(this.getContext(), InteroperabilityPromptActivity.class);
                secureStorage.setRequirePromptDisplay(false);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        updatesBox = view.findViewById(R.id.card_infobox);
        informationCard = view.findViewById(R.id.card_information);
        tracingCard = view.findViewById(R.id.card_tracing);
        cardNotifications = view.findViewById(R.id.card_notifications);
        interoperabilityCard = view.findViewById(R.id.card_interoperability);
        reportStatusBubble = view.findViewById(R.id.report_status_bubble);
        reportStatusView = reportStatusBubble.findViewById(R.id.report_status);
        reportErrorView = reportStatusBubble.findViewById(R.id.report_errors);
        headerView = view.findViewById(R.id.home_header_view);
        scrollView = view.findViewById(R.id.home_scroll_view);
        cardSymptoms = view.findViewById(R.id.card_what_to_do_symptoms);
        cardSymptomsFrame = view.findViewById(R.id.frame_card_symptoms);
        cardTest = view.findViewById(R.id.card_what_to_do_test);
        cardTestFrame = view.findViewById(R.id.frame_card_test);
        loadingView = view.findViewById(R.id.loading_view);

        setupHeader();
        setupUpdatesBox();
        setupInformationView();
        setupTracingView();
        setupNotification();
        setupInteroperability();
        setupWhatToDo();
        setupNonProductionHint();
        setupScrollBehavior();
    }

    @Override
    public void onStart() {
        super.onStart();
        tracingViewModel.invalidateTracingStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        headerView.stopAnimation();
    }

    private void setupHeader() {
        tracingViewModel.getAppStatusLiveData().observe(getViewLifecycleOwner(), headerView::setState);
    }

    private void setupUpdatesBox() {
        secureStorage.getInfoBoxLiveData().observe(getViewLifecycleOwner(), hasInfobox -> {
            hasInfobox = hasInfobox && secureStorage.getHasInfobox();

            if (!hasInfobox) {
                updatesBox.setVisibility(View.GONE);
                return;
            }
            updatesBox.setVisibility(VISIBLE);

            String title = secureStorage.getInfoboxTitle();
            TextView titleView = updatesBox.findViewById(R.id.infobox_title);
            if (title != null) {
                titleView.setText(title);
                titleView.setVisibility(VISIBLE);
            } else {
                titleView.setVisibility(View.GONE);
            }

            String text = secureStorage.getInfoboxText();
            TextView textView = updatesBox.findViewById(R.id.infobox_text);
            if (text != null) {
                textView.setText(text);
                textView.setVisibility(VISIBLE);
            } else {
                textView.setVisibility(View.GONE);
            }

            String url = secureStorage.getInfoboxLinkUrl();
            String urlTitle = secureStorage.getInfoboxLinkTitle();
            View linkGroup = updatesBox.findViewById(R.id.infobox_link_group);
            TextView linkView = updatesBox.findViewById(R.id.infobox_link_text);
            if (url != null) {
                linkView.setText(urlTitle != null ? urlTitle : url);
                linkGroup.setOnClickListener(v -> UrlUtil.openUrl(v.getContext(), url));
                linkGroup.setVisibility(VISIBLE);
            } else {
                linkGroup.setVisibility(View.GONE);
            }

            boolean isDismissible = secureStorage.getInfoboxDismissible();
            View dismissButton = updatesBox.findViewById(R.id.dismiss_button);
            if (isDismissible) {
                dismissButton.setVisibility(VISIBLE);
                dismissButton.setOnClickListener(v -> secureStorage.setHasInfobox(false));
            } else dismissButton.setVisibility(View.GONE);
        });
    }

    private void setupInformationView() {

        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true);
        informationCard.setForeground(getContext().getDrawable(outValue.resourceId));
        informationCard.setOnClickListener(v -> UrlUtil.openUrl(v.getContext(), getString(R.string.information_url)));
    }

    private void setupTracingView() {
        TypedValue outValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true);
        tracingCard.setForeground(requireContext().getDrawable(outValue.resourceId));

        tracingViewModel.getAppStatusLiveData().observe(getViewLifecycleOwner(), tracingStatusInterface -> {
            if (tracingStatusInterface.isReportedAsInfected()) {
                cardSymptomsFrame.setVisibility(View.GONE);
                cardTestFrame.setVisibility(View.GONE);
                tracingCard.findViewById(R.id.contacs_chevron).setVisibility(View.GONE);
                tracingCard.setOnClickListener(null);
                tracingCard.setForeground(null);
                interoperabilityCard.setVisibility(View.GONE);
            } else {
                cardSymptomsFrame.setVisibility(VISIBLE);
                cardTestFrame.setVisibility(VISIBLE);
                tracingCard.findViewById(R.id.contacs_chevron).setVisibility(VISIBLE);
                tracingCard.setOnClickListener(v -> showContactsFragment());
                interoperabilityCard.setVisibility(VISIBLE);
            }
        });
    }

    private void showContactsFragment() {
        getActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
                .replace(R.id.main_fragment_container, ContactsFragment.newInstance())
                .addToBackStack(ContactsFragment.class.getCanonicalName())
                .commit();
    }

    private void showReportsFragment() {
        getActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
                .replace(R.id.main_fragment_container, ReportsFragment.newInstance())
                .addToBackStack(ReportsFragment.class.getCanonicalName())
                .commit();
    }

    private void setupNotification() {
        cardNotifications.setOnClickListener(v -> showReportsFragment());

        tracingViewModel.getAppStatusLiveData().observe(getViewLifecycleOwner(), tracingStatusInterface -> {
            //update status view
            if (loadingView.getVisibility() == VISIBLE) {
                loadingView.animate()
                        .setStartDelay(getResources().getInteger(android.R.integer.config_mediumAnimTime))
                        .setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime))
                        .alpha(0f)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                loadingView.setVisibility(View.GONE);
                            }
                        });
            } else {
                loadingView.setVisibility(View.GONE);
            }
            if (tracingStatusInterface.isReportedAsInfected()) {
                NotificationStateHelper.updateStatusView(reportStatusView, NotificationState.POSITIVE_TESTED);
            } else if (tracingStatusInterface.wasContactReportedAsExposed()) {
                long daysSinceExposure = tracingStatusInterface.getDaysSinceExposure();
                NotificationStateHelper.updateStatusView(reportStatusView, NotificationState.EXPOSED, daysSinceExposure);
            } else {
                NotificationStateHelper.updateStatusView(reportStatusView, NotificationState.NO_REPORTS);
            }

            TracingStatus.ErrorState errorState = tracingStatusInterface.getReportErrorState();
            if (tracingStatusInterface.getTracingState().equals(TracingState.NOT_ACTIVE) &&
                    !tracingStatusInterface.isReportedAsInfected()) {
                NotificationErrorStateHelper
                        .updateNotificationErrorView(reportErrorView, NotificationStateError.TRACING_DEACTIVATED);
                reportErrorView.findViewById(R.id.error_status_button).setOnClickListener(v -> {
                    enableTracing();
                });
            } else if (errorState != null) {
                TracingErrorStateHelper
                        .updateErrorView(reportErrorView, errorState);
                reportErrorView.findViewById(R.id.error_status_button).setOnClickListener(v -> {
                    loadingView.setVisibility(VISIBLE);
                    loadingView.animate()
                            .alpha(1f)
                            .setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime))
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    tracingViewModel.sync();
                                }
                            });
                });
            } else if (!isNotificationChannelEnabled(getContext(), NotificationUtil.NOTIFICATION_CHANNEL_ID)) {
                NotificationErrorStateHelper
                        .updateNotificationErrorView(reportErrorView, NotificationStateError.NOTIFICATION_STATE_ERROR);
                reportErrorView.findViewById(R.id.error_status_button).setOnClickListener(v -> {
                    openChannelSettings(NotificationUtil.NOTIFICATION_CHANNEL_ID);
                });
            } else {
                //hide errorview
                TracingErrorStateHelper.updateErrorView(reportErrorView, null);
            }
        });
    }

    private void setupInteroperability() {
        TypedValue outValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        interoperabilityCard.setForeground(requireContext().getDrawable(outValue.resourceId));

        updateInteroperabilityState(secureStorage.getConfigInteroperabilityMode(), secureStorage.getConfigInteroperabilityPossible());

        //Handle changes to interoperability availability
        secureStorage.getConfigInteroperabilityPossibleLiveData().observe(getViewLifecycleOwner(), interoperabilityPossible -> {
            updateInteroperabilityState(secureStorage.getConfigInteroperabilityMode(), secureStorage.getConfigInteroperabilityPossible());
        });

        //Handle changes to interoperability mode
        secureStorage.getConfigInteroperabilityModeLiveData().observe(getViewLifecycleOwner(), interoperabilityMode -> {
            updateInteroperabilityState(secureStorage.getConfigInteroperabilityMode(), secureStorage.getConfigInteroperabilityPossible());
        });

        interoperabilityCard.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), InteroperabilityActivity.class);
            startActivity(intent);
        });
    }

    private void updateInteroperabilityState(InteroperabilityMode interoperabilityMode, boolean isInteropPossible) {
        LinearLayout backgroundView = interoperabilityCard.findViewById(R.id.status_background);
        ImageView iconView = interoperabilityCard.findViewById(R.id.status_icon);
        TextView titleView = interoperabilityCard.findViewById(R.id.status_title);
        TextView textView = interoperabilityCard.findViewById(R.id.status_text);

        if (!isInteropPossible) {
            backgroundView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.blue_main));
            iconView.setImageResource(R.drawable.ic_warning);
            iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.white)));
            titleView.setText(R.string.interop_mode_unavailable_title);
            titleView.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
            textView.setText(R.string.interop_mode_unavailable_text);
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
        } else {
            backgroundView.setBackgroundColor(ContextCompat.getColor(getContext(), InteroperabilityMode.getBackgroundColor(interoperabilityMode)));
            iconView.setImageResource(InteroperabilityMode.getIcon(interoperabilityMode));
            iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), InteroperabilityMode.getTitleTextColor(interoperabilityMode))));
            titleView.setText(InteroperabilityMode.getTitle(interoperabilityMode));
            titleView.setTextColor(ContextCompat.getColor(getContext(), InteroperabilityMode.getTitleTextColor(interoperabilityMode)));
            textView.setText(InteroperabilityMode.getText(interoperabilityMode));
            textView.setTextColor(ContextCompat.getColor(getContext(), InteroperabilityMode.getTextColor(interoperabilityMode)));
        }
    }

    private void openChannelSettings(String channelId) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireActivity().getPackageName());
            startActivity(intent);
        } else {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + requireActivity().getPackageName()));
            startActivity(intent);
        }
    }

    private boolean isNotificationChannelEnabled(Context context, @Nullable String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!TextUtils.isEmpty(channelId)) {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel channel = manager.getNotificationChannel(channelId);
                if (channel == null) {
                    return manager.areNotificationsEnabled();
                }
                return channel.getImportance() != NotificationManager.IMPORTANCE_NONE &&
                        !(!manager.areNotificationsEnabled() &&
                                channel.getImportance() == NotificationManager.IMPORTANCE_DEFAULT) &&
                        manager.areNotificationsEnabled();
            }
            return true;
        } else {
            return NotificationManagerCompat.from(context).areNotificationsEnabled();
        }
    }

    private void setupWhatToDo() {

        cardSymptoms.setOnClickListener(
                v -> getActivity().getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
                        .replace(R.id.main_fragment_container, WtdSymptomsFragment.newInstance())
                        .addToBackStack(WtdSymptomsFragment.class.getCanonicalName())
                        .commit());
        cardTest.setOnClickListener(
                v -> getActivity().getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
                        .replace(R.id.main_fragment_container, WtdPositiveTestFragment.newInstance())
                        .addToBackStack(WtdPositiveTestFragment.class.getCanonicalName())
                        .commit());
    }

    private void setupNonProductionHint() {
        View nonProduction = requireView().findViewById(R.id.non_production_message);
        if (BuildConfig.IS_FLAVOR_PROD || BuildConfig.IS_FLAVOR_ABNAHME) {
            nonProduction.setVisibility(View.GONE);
        } else {
            nonProduction.setVisibility(VISIBLE);
        }
    }

    private void setupScrollBehavior() {
        int scrollRangePx = getResources().getDimensionPixelSize(R.dimen.top_item_padding);
        int translationRangePx = -getResources().getDimensionPixelSize(R.dimen.spacing_huge);
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float progress = UiUtils.computeScrollAnimProgress(scrollY, scrollRangePx);
            headerView.setAlpha(1 - progress);
            headerView.setTranslationY(progress * translationRangePx);
        });
        scrollView.post(() -> {
            float progress = UiUtils.computeScrollAnimProgress(scrollView.getScrollY(), scrollRangePx);
            headerView.setAlpha(1 - progress);
            headerView.setTranslationY(progress * translationRangePx);
        });
    }

    private void enableTracing() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        tracingViewModel.enableTracing(activity,
                () -> {
                },
                (e) -> {
                    String message = ENExceptionHelper.getErrorMessage(e, activity);
                    Logger.e(TAG, message);
                    new AlertDialog.Builder(activity, R.style.NextStep_AlertDialogStyle)
                            .setTitle(R.string.android_en_start_failure)
                            .setMessage(message)
                            .setPositiveButton(R.string.android_button_ok, (dialog, which) -> {
                            })
                            .show();
                },
                () -> {
                    // cancelled
                });
    }
}
