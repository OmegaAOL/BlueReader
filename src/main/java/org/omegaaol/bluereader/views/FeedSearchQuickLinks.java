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
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.activities.PostListingActivity;
import org.omegaaol.bluereader.common.EventListenerSet;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.bluesky.url.SearchPostListURL;

import java.util.Objects;

public class FeedSearchQuickLinks extends FlexboxLayout {

	private AppCompatActivity mActivity;

	@Nullable private EventListenerSet<String> mBinding;
	@Nullable private EventListenerSet.Listener<String> mBindingListener;

	private Button mButtonFeed;
	private Button mButtonUser;
	private Button mButtonUrl;
	private MaterialButton mButtonSearch;

	public FeedSearchQuickLinks(final Context context) {
		this(context, null);
	}

	public FeedSearchQuickLinks(
			final Context context,
			final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FeedSearchQuickLinks(
			final Context context,
			final AttributeSet attrs,
			final int defStyleAttr) {

		super(context, attrs, defStyleAttr);
	}

	@SuppressWarnings("RedundantCast")
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		mButtonFeed = Objects.requireNonNull(
				(MaterialButton)findViewById(R.id.button_toggle_search_feeds));
		mButtonUser = Objects.requireNonNull(
				(MaterialButton)findViewById(R.id.button_toggle_search_users));
		mButtonUrl = Objects.requireNonNull(
				(MaterialButton)findViewById(R.id.button_go_to_url));
		mButtonSearch = Objects.requireNonNull(
				(MaterialButton)findViewById(R.id.button_toggle_search_posts));

	}

	public void bind(
			@NonNull final AppCompatActivity activity,
			@NonNull final EventListenerSet<String> querySource) {

		mActivity = activity;

		if(mBinding != null) {
			throw new RuntimeException("Search view already bound");
		}

		mBinding = querySource;

		doBind();
	}

	private void doBind() {
		if(mBinding != null) {
			mBindingListener = this::update;
			update(mBinding.register(mBindingListener));
		}
	}

	private void doUnbind() {
		if(mBinding != null && mBindingListener != null) {
			mBinding.unregister(mBindingListener);
			mBindingListener = null;
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		doBind();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		doUnbind();
	}

	private void update(@Nullable String query) {

		if(query != null) {
			query = query.trim();
		}

		if(query == null || query.isEmpty()) {
			mButtonFeed.setText(R.string.action_toggle_search_feeds);

			mButtonFeed.setEnabled(false);
			mButtonUser.setEnabled(false);
			mButtonUrl.setEnabled(false);
			mButtonSearch.setEnabled(false);

			mButtonFeed.setVisibility(VISIBLE);
			mButtonUser.setVisibility(VISIBLE);
			mButtonUrl.setVisibility(VISIBLE);
			mButtonSearch.setVisibility(VISIBLE);

		} else {

			final ProcessedQuery queryProcessed = new ProcessedQuery(query);

			if(queryProcessed.queryFeed != null) {
				mButtonFeed.setVisibility(VISIBLE);

				final String feedPrefixed = queryProcessed.queryFeed;
				mButtonFeed.setText(feedPrefixed);

				mButtonFeed.setOnClickListener(
						view -> LinkHandler.onLinkClicked(mActivity, feedPrefixed));

			} else {
				mButtonFeed.setVisibility(GONE);
			}

			if(queryProcessed.queryUser != null) {
				mButtonUser.setVisibility(VISIBLE);

				mButtonUser.setOnClickListener(view -> LinkHandler.onLinkClicked(
						mActivity,
						"/u/" + queryProcessed.queryUser));

			} else {
				mButtonUser.setVisibility(GONE);
			}

			if(queryProcessed.queryUrl != null) {
				mButtonUrl.setVisibility(VISIBLE);

				mButtonUrl.setOnClickListener(view -> LinkHandler.onLinkClicked(
						mActivity,
						queryProcessed.queryUrl));

			} else {
				mButtonUrl.setVisibility(GONE);
			}

			mButtonSearch.setOnClickListener(view -> {

				final SearchPostListURL url
						= SearchPostListURL.build(null, queryProcessed.querySearch);

				final Intent intent = new Intent(mActivity, PostListingActivity.class);
				intent.setData(url.generateJsonUri());
				mActivity.startActivity(intent);
			});

			mButtonFeed.setEnabled(true);
			mButtonUser.setEnabled(true);
			mButtonUrl.setEnabled(true);
			mButtonSearch.setEnabled(true);
		}
	}

	private static class ProcessedQuery {

		@Nullable public final String queryFeed;
		@Nullable public final String queryUser;
		@Nullable public final String queryUrl;
		@Nullable public final String querySearch;

		public ProcessedQuery(@NonNull final String query) {

			querySearch = query;

			final boolean startsWithSlashRSlash = query.startsWith("/r/");
			final boolean startsWithRSlash = query.startsWith("r/");

			final boolean startsWithSlashUSlash = query.startsWith("/u/");
			final boolean startsWithUSlash = query.startsWith("u/");

			if(query.contains("://")) {
				queryFeed = null;
				queryUser = null;
				queryUrl = query;

			} else if(startsWithSlashUSlash || startsWithUSlash) {

				if(startsWithSlashUSlash) {
					queryUser = query.substring(3);
				} else {
					queryUser = query.substring(2);
				}

				queryFeed = null;
				queryUrl = null;

			} else if(query.startsWith("/")) {
				queryFeed = null;
				queryUser = null;
				queryUrl = "https://reddit.com" + query;

			} else {
				queryFeed = query.replaceAll("[ \t]+", "_");
				queryUser = queryFeed;
				queryUrl = "https://" + query;
			}
		}
	}
}
