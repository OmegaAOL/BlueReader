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

package org.omegaaol.bluereader.viewholders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.apache.commons.text.StringEscapeUtils;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.RRThemeAttributes;
import org.omegaaol.bluereader.bluesky.FeedDetails;
import org.omegaaol.bluereader.bluesky.prepared.bodytext.BodyElement;
import org.omegaaol.bluereader.bluesky.prepared.html.HtmlReader;
import org.omegaaol.bluereader.views.FeedToolbar;

import java.text.NumberFormat;
import java.util.Locale;

public class FeedItemViewHolder extends RecyclerView.ViewHolder {

	private final BaseActivity mActivity;
	private final RRThemeAttributes mTheme;
	private final float mBodyFontScale;

	private final TextView mPrimaryText;
	private final TextView mSubText;
	private final FrameLayout mSupportingText;
	private final FeedToolbar mActions;
	private final View mGoButton;

	public FeedItemViewHolder(
			@NonNull final ViewGroup parent,
			final BaseActivity activity) {

		super(LayoutInflater.from(parent.getContext())
				.inflate(R.layout.feed_item_view, parent, false));

		mActivity = activity;
		mTheme = new RRThemeAttributes(activity);
		mBodyFontScale = PrefsUtility.appearance_fontscale_bodytext();

		mPrimaryText = this.itemView.findViewById(R.id.feed_item_view_primary_text);
		mSubText = this.itemView.findViewById(R.id.feed_item_view_sub_text);
		mSupportingText = this.itemView.findViewById(R.id.feed_item_view_supporting_text);
		mActions = this.itemView.findViewById(R.id.feed_item_view_actions);
		mGoButton = this.itemView.findViewById(R.id.feed_item_view_go);
	}

	public void bind(@NonNull final FeedDetails feed) {

		mPrimaryText.setText(feed.name);

		final String subtitle;
		if(feed.subscribers == null) {
			subtitle = null;
		} else {
			subtitle = mActivity.getString(
					R.string.header_subscriber_count,
					NumberFormat.getNumberInstance(Locale.getDefault())
							.format(feed.subscribers));
		}

		if(subtitle == null) {
			mSubText.setVisibility(View.GONE);
		} else {
			mSubText.setVisibility(View.VISIBLE);
			mSubText.setText(subtitle);
		}

		mSupportingText.removeAllViews();

		if(feed.publicDescriptionHtmlEscaped != null
				&& !feed.publicDescriptionHtmlEscaped.trim().isEmpty()) {

			final BodyElement body = HtmlReader.parse(
					StringEscapeUtils.unescapeHtml4(feed.publicDescriptionHtmlEscaped),
					mActivity);

			mSupportingText.setVisibility(View.VISIBLE);

			mSupportingText.addView(body.generateView(
					mActivity,
					mTheme.rrCommentBodyCol,
					13.0f * mBodyFontScale,
					false));
		} else {
			mSupportingText.setVisibility(View.GONE);
		}

		mActions.bindFeed(feed, Optional.empty());

		mGoButton.setOnClickListener(
				v -> LinkHandler.onLinkClicked(mActivity, feed.url));
	}
}
