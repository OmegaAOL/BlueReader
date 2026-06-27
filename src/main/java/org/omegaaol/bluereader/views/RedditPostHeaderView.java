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

import android.content.res.TypedArray;
import android.graphics.Color;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.common.Fonts;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.bluesky.api.RedditPostActions;
import org.omegaaol.bluereader.bluesky.prepared.RedditChangeDataManager;
import org.omegaaol.bluereader.bluesky.prepared.RedditPreparedPost;

public class RedditPostHeaderView extends LinearLayout {

	private final TextView subtitle;

	@Nullable private final Runnable mChangeListenerAddTask;
	@Nullable private final Runnable mChangeListenerRemoveTask;

	public RedditPostHeaderView(
			final BaseActivity activity,
			final RedditPreparedPost post) {

		super(activity);

		final float dpScale = activity.getResources().getDisplayMetrics().density;

		setOrientation(LinearLayout.VERTICAL);

		final LinearLayout greyHeader = new LinearLayout(activity);

		RedditPostActions.INSTANCE.setupAccessibilityActions(
				new AccessibilityActionManager(
						greyHeader,
						activity.getResources()),
				post,
				activity,
				true);

		greyHeader.setOrientation(LinearLayout.VERTICAL);

		final int sidesPadding = (int)(15.0f * dpScale);
		final int topPadding = (int)(10.0f * dpScale);

		greyHeader.setPadding(sidesPadding, topPadding, sidesPadding, topPadding);

		final float titleFontScale = PrefsUtility.appearance_fontscale_post_header_titles();

		final TextView title = new TextView(activity);
		title.setTextSize(19.0f * titleFontScale);
		title.setTypeface(Fonts.getRobotoLightOrAlternative());
		title.setText(post.src.getTitle());
		title.setContentDescription(post.buildAccessibilityTitle(activity, true));
		title.setTextColor(Color.WHITE);
		greyHeader.addView(title);

		final float subtitleFontScale =
				PrefsUtility.appearance_fontscale_post_header_subtitles();

		subtitle = new TextView(activity);
		subtitle.setTextSize(13.0f * subtitleFontScale);
		subtitle.setText(post.buildSubtitle(activity, true));
		subtitle.setContentDescription(post.buildAccessibilitySubtitle(activity, true));

		subtitle.setTextColor(Color.rgb(200, 200, 200));
		greyHeader.addView(subtitle);

		{
			final TypedArray appearance = activity.obtainStyledAttributes(new int[] {
					R.attr.rrPostListHeaderBackgroundCol});

			greyHeader.setBackgroundColor(appearance.getColor(0, General.COLOR_INVALID));

			appearance.recycle();
		}

		greyHeader.setOnClickListener(v -> {
			if(!post.isSelf()) {
				LinkHandler.onLinkClicked(
						activity,
						post.src.getUrl(),
						false,
						post.src.getSrc());
			}
		});

		greyHeader.setOnLongClickListener(v -> {
			RedditPostActions.INSTANCE.showActionMenu(activity, post);
			return true;
		});

		addView(greyHeader);

		final RedditAccount currentUser =
				RedditAccountManager.getInstance(activity).getDefaultAccount();

		if(!currentUser.isAnonymous()) {

			// A user is logged in

			final RedditChangeDataManager changeDataManager
					= RedditChangeDataManager.getInstance(currentUser);
			final RedditChangeDataManager.Listener changeListener;

			if(!PrefsUtility.pref_appearance_hide_headertoolbar_commentlist()) {

				final LinearLayout buttons =
						inflate(activity, R.layout.post_header_toolbar, this)
								.findViewById(R.id.post_toolbar_layout);

				for(int i = 0; i < buttons.getChildCount(); i++) {
					final ImageButton button = (ImageButton)buttons.getChildAt(i);
					TooltipCompat.setTooltipText(button, button.getContentDescription());
				}

				final ImageButton buttonLike =
						buttons.findViewById(R.id.post_toolbar_botton_add_like);
				final ImageButton buttonRemoveLike =
						buttons.findViewById(R.id.post_toolbar_botton_remove_like);
				final ImageButton buttonRepost = //fix
						buttons.findViewById(R.id.post_toolbar_botton_add_downvote);
				final ImageButton buttonRemoveRepost =
						buttons.findViewById(R.id.post_toolbar_botton_remove_downvote);
				final ImageButton buttonReply =
						buttons.findViewById(R.id.post_toolbar_botton_reply);
				final ImageButton buttonShare =
						buttons.findViewById(R.id.post_toolbar_botton_share);
				final ImageButton buttonMore =
						buttons.findViewById(R.id.post_toolbar_botton_more);

				buttonLike.setOnClickListener(v -> post.performAction(
						activity,
						RedditPostActions.Action.LIKE));
				buttonRemoveLike.setOnClickListener(v -> post.performAction(
						activity,
						RedditPostActions.Action.UNVOTE));
				buttonRepost.setOnClickListener(v -> post.performAction(
						activity,
						RedditPostActions.Action.REPOST));
				buttonRemoveRepost.setOnClickListener(v -> post.performAction(
						activity,
						RedditPostActions.Action.UNVOTE));
				buttonReply.setOnClickListener(v -> post.performAction(
						activity,
						RedditPostActions.Action.REPLY));
				buttonShare.setOnClickListener(v -> post.performAction(
						activity,
						RedditPostActions.Action.SHARE));
				buttonMore.setOnClickListener(v -> post.performAction(
						activity,
						RedditPostActions.Action.ACTION_MENU));

				changeListener = thingIdAndType -> {

					subtitle.setText(post.buildSubtitle(activity, true));
					subtitle.setContentDescription(
							post.buildAccessibilitySubtitle(activity, true));

					final boolean isLiked = changeDataManager.isLiked(
							post.src.getIdAndType());

					final boolean isDownvoted = changeDataManager.isDownvoted(
							post.src.getIdAndType());

					if(isLiked) {
						buttonLike.setVisibility(GONE);
						buttonRemoveLike.setVisibility(VISIBLE);
						buttonRepost.setVisibility(VISIBLE);
						buttonRemoveRepost.setVisibility(GONE);

					} else if(isDownvoted) {
						buttonLike.setVisibility(VISIBLE);
						buttonRemoveLike.setVisibility(GONE);
						buttonRepost.setVisibility(GONE);
						buttonRemoveRepost.setVisibility(VISIBLE);

					} else {
						buttonLike.setVisibility(VISIBLE);
						buttonRemoveLike.setVisibility(GONE);
						buttonRepost.setVisibility(VISIBLE);
						buttonRemoveRepost.setVisibility(GONE);
					}
				};

			} else {
				changeListener = thingIdAndType -> {
					subtitle.setText(post.buildSubtitle(activity, true));
					subtitle.setContentDescription(
							post.buildAccessibilitySubtitle(activity, true));
				};
			}

			mChangeListenerAddTask = () -> {
				changeDataManager.addListener(post.src.getIdAndType(), changeListener);
				changeListener.onRedditDataChange(post.src.getIdAndType());
			};

			mChangeListenerRemoveTask = () -> changeDataManager.removeListener(
					post.src.getIdAndType(),
					changeListener);

		} else {
			mChangeListenerAddTask = null;
			mChangeListenerRemoveTask = null;
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if(mChangeListenerAddTask != null) {
			mChangeListenerAddTask.run();
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		if(mChangeListenerRemoveTask != null) {
			mChangeListenerRemoveTask.run();
		}
	}
}
