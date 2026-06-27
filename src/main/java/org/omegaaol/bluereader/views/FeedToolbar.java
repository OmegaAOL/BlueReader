/*******************************************************************************
 * This file is part of BlueReader.
 *
 * BlueReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BlueReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BlueReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.omegaaol.bluereader.views;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.activities.PostSubmitActivity;
import org.omegaaol.bluereader.common.AndroidCommon;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.SharedPrefsWrapper;
import org.omegaaol.bluereader.bluesky.FeedDetails;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionManager;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionState;

import java.util.Objects;

public class FeedToolbar extends LinearLayout implements
		FeedSubscriptionManager.FeedSubscriptionStateChangeListener,
		SharedPrefsWrapper.OnSharedPreferenceChangeListener {

	@NonNull private final Context mContext;

	// Field can't be local because the listener gets put in a weak map, and we want to stop it
	// being garbage collected.
	@Nullable private FeedSubscriptionManager.ListenerContext
			mSubscriptionListenerContext;

	private Runnable mRunnableOnAttach;
	private Runnable mRunnableOnDetach;
	private Runnable mRunnableOnSubscriptionsChange;
	private Runnable mRunnableOnPinnedChange;

	@NonNull private Optional<FeedDetails> mFeedDetails = Optional.empty();
	@NonNull private Optional<String> mUrl = Optional.empty();

	private ImageButton mButtonInfo;

	public void bindFeed(
			@NonNull final FeedDetails feed,
			@NonNull final Optional<String> url) {

		mFeedDetails = Optional.of(feed);
		mUrl = url;

		if(feed.hasSidebar()) {
			mButtonInfo.setVisibility(VISIBLE);
		} else {
			mButtonInfo.setVisibility(GONE);
		}

		mRunnableOnSubscriptionsChange.run();
		mRunnableOnPinnedChange.run();
	}

	public FeedToolbar(final Context context) {
		this(context, null);
	}

	public FeedToolbar(
			final Context context,
			@Nullable final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FeedToolbar(
			final Context context,
			@Nullable final AttributeSet attrs,
			final int defStyleAttr) {

		super(context, attrs, defStyleAttr);

		mContext = context;
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		final AppCompatActivity activity = (AppCompatActivity)mContext;

		final SharedPrefsWrapper sharedPreferences
				= General.getSharedPrefs(mContext);

		final RedditAccount currentUser =
				RedditAccountManager.getInstance(mContext).getDefaultAccount();

		final ImageButton buttonSubscribe = Objects.requireNonNull(
				(ImageButton)findViewById(R.id.feed_toolbar_button_subscribe));
		final ImageButton buttonUnsubscribe = Objects.requireNonNull(
				(ImageButton)findViewById(R.id.feed_toolbar_button_unsubscribe));
		final FrameLayout buttonSubscribeLoading = Objects.requireNonNull(
				(FrameLayout)findViewById(R.id.feed_toolbar_button_subscribe_loading));

		final ImageButton buttonPin = Objects.requireNonNull(
				(ImageButton)findViewById(R.id.feed_toolbar_button_pin));
		final ImageButton buttonUnpin = Objects.requireNonNull(
				(ImageButton)findViewById(R.id.feed_toolbar_button_unpin));

		final ImageButton buttonSubmit = Objects.requireNonNull(
				(ImageButton)findViewById(R.id.feed_toolbar_button_submit));
		final ImageButton buttonShare = Objects.requireNonNull(
				(ImageButton)findViewById(R.id.feed_toolbar_button_share));
		mButtonInfo = Objects.requireNonNull(
				(ImageButton)findViewById(R.id.feed_toolbar_button_info));

		for(int i = 0; i < getChildCount(); i++) {
			final View button = getChildAt(i);
			TooltipCompat.setTooltipText(button, button.getContentDescription());
		}

		buttonSubscribeLoading.addView(new ButtonLoadingSpinnerView(mContext));

		final FeedSubscriptionManager subscriptionManager
				= FeedSubscriptionManager.getSingleton(
						mContext,
						currentUser);

		mRunnableOnSubscriptionsChange = () -> {

			final FeedSubscriptionState
					subscriptionState = subscriptionManager.getSubscriptionState(
					mFeedDetails.get().id);

			if(subscriptionState == FeedSubscriptionState.SUBSCRIBED) {

				buttonSubscribe.setVisibility(GONE);
				buttonUnsubscribe.setVisibility(VISIBLE);
				buttonSubscribeLoading.setVisibility(GONE);

			} else if(subscriptionState == FeedSubscriptionState.NOT_SUBSCRIBED) {

				buttonSubscribe.setVisibility(VISIBLE);
				buttonUnsubscribe.setVisibility(GONE);
				buttonSubscribeLoading.setVisibility(GONE);

			} else {
				buttonSubscribe.setVisibility(GONE);
				buttonUnsubscribe.setVisibility(GONE);
				buttonSubscribeLoading.setVisibility(VISIBLE);
			}
		};

		mRunnableOnPinnedChange = () -> {

			final boolean pinned = PrefsUtility.pref_pinned_feeds_check(
					mFeedDetails.get().id);

			if(pinned) {
				buttonPin.setVisibility(GONE);
				buttonUnpin.setVisibility(VISIBLE);
			} else {
				buttonPin.setVisibility(VISIBLE);
				buttonUnpin.setVisibility(GONE);
			}
		};

		mRunnableOnAttach = () -> {
			mSubscriptionListenerContext = subscriptionManager.addListener(this);
			sharedPreferences.registerOnSharedPreferenceChangeListener(this);

			mRunnableOnSubscriptionsChange.run();
			mRunnableOnPinnedChange.run();
		};

		mRunnableOnDetach = () -> {
			mSubscriptionListenerContext.removeListener();
			mSubscriptionListenerContext = null;

			sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
		};

		if(currentUser.isAnonymous()) {

			final OnClickListener mustBeLoggedInListener
					= v -> General.showMustBeLoggedInDialog(activity);

			buttonSubscribe.setOnClickListener(mustBeLoggedInListener);
			buttonUnsubscribe.setOnClickListener(mustBeLoggedInListener);
			buttonSubmit.setOnClickListener(mustBeLoggedInListener);

		} else {
			buttonSubscribe.setOnClickListener(v -> subscriptionManager.subscribe(
					mFeedDetails.get().id,
					activity));

			buttonUnsubscribe.setOnClickListener(v -> subscriptionManager.unsubscribe(
					mFeedDetails.get().id,
					activity));

			buttonSubmit.setOnClickListener(v -> {
				final Intent intent = new Intent(
						activity,
						PostSubmitActivity.class);
				intent.putExtra("feed", mFeedDetails.get().id.toString());
				activity.startActivity(intent);
			});
		}

		buttonPin.setOnClickListener(v -> PrefsUtility.pref_pinned_feeds_add(
				mContext,
				mFeedDetails.get().id));

		buttonUnpin.setOnClickListener(v -> PrefsUtility.pref_pinned_feeds_remove(
				mContext,
				mFeedDetails.get().id));

		buttonShare.setOnClickListener(v -> LinkHandler.shareText(
				activity,
				mFeedDetails.get().id.toString(),
				mUrl.orElse(mFeedDetails.get().url)));

		mButtonInfo.setOnClickListener(
				v -> mFeedDetails.get().showSidebarActivity(activity));
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if(mRunnableOnAttach != null) {
			mRunnableOnAttach.run();
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		if(mRunnableOnDetach != null) {
			mRunnableOnDetach.run();
		}
	}

	@Override
	public void onFeedSubscriptionListUpdated(
			final FeedSubscriptionManager feedSubscriptionManager) {

		if(mRunnableOnSubscriptionsChange != null) {
			AndroidCommon.UI_THREAD_HANDLER.post(mRunnableOnSubscriptionsChange);
		}
	}

	@Override
	public void onFeedSubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {

		if(mRunnableOnSubscriptionsChange != null) {
			AndroidCommon.UI_THREAD_HANDLER.post(mRunnableOnSubscriptionsChange);
		}
	}

	@Override
	public void onFeedUnsubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {

		if(mRunnableOnSubscriptionsChange != null) {
			AndroidCommon.UI_THREAD_HANDLER.post(mRunnableOnSubscriptionsChange);
		}
	}

	@Override
	public void onSharedPreferenceChanged(
			@NonNull final SharedPrefsWrapper sharedPreferences,
			@NonNull final String key) {

		if(mRunnableOnPinnedChange != null
				&& key.equals(mContext.getString(R.string.pref_pinned_feeds_key))) {
			mRunnableOnPinnedChange.run();
		}
	}
}
