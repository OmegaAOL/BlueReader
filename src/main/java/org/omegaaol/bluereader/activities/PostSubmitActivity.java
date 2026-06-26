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

package org.omegaaol.bluereader.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.fragments.postsubmit.PostSubmitContentFragment;
import org.omegaaol.bluereader.fragments.postsubmit.PostSubmitSubredditSelectionFragment;
import org.omegaaol.bluereader.reddit.things.InvalidSubredditNameException;
import org.omegaaol.bluereader.reddit.things.SubredditCanonicalId;


public class PostSubmitActivity extends BaseActivity implements
		PostSubmitSubredditSelectionFragment.Listener,
		PostSubmitContentFragment.Listener {

	@NonNull private static final String TAG = "PostSubmitActivity";

	@Nullable private String mIntentUrl;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {

		PrefsUtility.applyThemeAccent(this);


		super.onCreate(savedInstanceState);

		SubredditCanonicalId intentSubreddit = null;

		final Intent intent = getIntent();

		if(intent != null) {

			final String subreddit = intent.getStringExtra("subreddit");

			if(subreddit != null) {
				try {
					intentSubreddit = new SubredditCanonicalId(subreddit);

				} catch(final InvalidSubredditNameException e) {
					Log.e(TAG, "Invalid subreddit name", e);
				}
			}

			if(Intent.ACTION_SEND.equalsIgnoreCase(intent.getAction())
					&& intent.hasExtra(Intent.EXTRA_TEXT)) {
				mIntentUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
			}
		}

		setBaseActivityListing(R.layout.single_fragment_layout);

		getSupportFragmentManager().beginTransaction()
				.setReorderingAllowed(false)
				.add(
						R.id.single_fragment_container,
						PostSubmitSubredditSelectionFragment.class,
						new PostSubmitSubredditSelectionFragment.Args(intentSubreddit).toBundle())
				.commit();
	}

	@Override
	public void onBackPressed() {
		if(General.onBackPressed()) {
			super.onBackPressed();
		}
	}

	@Override
	public void onSubredditSelected(
			@NonNull final String username,
			@NonNull final SubredditCanonicalId subreddit) {

		getSupportFragmentManager().beginTransaction()
				.setReorderingAllowed(false)
				.replace(
						R.id.single_fragment_container,
						PostSubmitContentFragment.class,
						new PostSubmitContentFragment.Args(
								username,
								subreddit,
								mIntentUrl).toBundle())
				.addToBackStack("Subreddit selected")
				.commit();
	}

	@Override
	public void onNotLoggedIn() {
		General.quickToast(this, R.string.error_toast_notloggedin);
		finish();
	}

	@Override
	public void onContentFragmentSubmissionSuccess(@Nullable final String redirectUrl) {

		if(redirectUrl != null) {
			LinkHandler.onLinkClicked(this, redirectUrl);
		}

		finish();
	}

	@Override
	public void onContentFragmentSubredditDoesNotExist() {

		onBackPressed();

		final Context applicationContext = getApplicationContext();

		General.showResultDialog(this, new RRError(
				applicationContext.getString(R.string.error_subreddit_does_not_exist_title),
				applicationContext.getString(R.string.error_subreddit_does_not_exist_message),
				false,
				new RuntimeException()));
	}

	@Override
	public void onContentFragmentSubredditPermissionDenied() {

		onBackPressed();

		final Context applicationContext = getApplicationContext();

		General.showResultDialog(this, new RRError(
				applicationContext.getString(
						R.string.error_subreddit_info_permission_denied_title),
				applicationContext.getString(
						R.string.error_subreddit_info_permission_denied_message),
				false,
				new RuntimeException()));
	}

	@Override
	public void onContentFragmentFlairRequestError(@NonNull final RRError error) {
		onBackPressed();
		General.showResultDialog(this, error);
	}
}
