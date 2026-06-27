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

package org.omegaaol.bluereader.bluesky;

import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.adapters.GroupedRecyclerViewAdapter;
import org.omegaaol.bluereader.fragments.PostListingFragment;
import org.omegaaol.bluereader.bluesky.prepared.RedditPreparedPost;
import org.omegaaol.bluereader.views.RedditPostView;

public class RedditPostListItem
		extends GroupedRecyclerViewAdapter.Item<RecyclerView.ViewHolder> {

	private final PostListingFragment mFragment;
	private final BaseActivity mActivity;

	private final RedditPreparedPost mPost;
	private final boolean mLeftHandedMode;

	public RedditPostListItem(
			final RedditPreparedPost post,
			final PostListingFragment fragment,
			final BaseActivity activity,
			final boolean leftHandedMode) {

		mFragment = fragment;
		mActivity = activity;
		mPost = post;
		mLeftHandedMode = leftHandedMode;
	}

	@Override
	public Class<RedditPostView> getViewType() {
		return RedditPostView.class;
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup viewGroup) {

		final RedditPostView view = new RedditPostView(
				mActivity,
				mFragment,
				mActivity,
				mLeftHandedMode);

		return new RecyclerView.ViewHolder(view) {
		};
	}

	@Override
	public void onBindViewHolder(final RecyclerView.ViewHolder viewHolder) {
		((RedditPostView)viewHolder.itemView).reset(mPost);
	}

	@Override
	public boolean isHidden() {
		return false;
	}

}
