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

package org.omegaaol.bluereader.reddit.prepared;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.RRThemeAttributes;
import org.omegaaol.bluereader.common.time.TimestampUTC;

public interface RedditRenderableCommentListItem {

	CharSequence getHeader(
			final RRThemeAttributes theme,
			final RedditChangeDataManager changeDataManager,
			final Context context,
			final int commentAgeUnits,
			@Nullable final TimestampUTC postCreated,
			@Nullable final TimestampUTC parentCommentCreated);

	String getAccessibilityHeader(
			final RRThemeAttributes theme,
			final RedditChangeDataManager changeDataManager,
			final Context context,
			final int commentAgeUnits,
			@Nullable final TimestampUTC postCreated,
			@Nullable final TimestampUTC parentCommentCreated,
			final boolean collapsed,
			@NonNull final Optional<Integer> indentLevel);

	View getBody(
			final BaseActivity activity,
			final Integer textColor,
			final Float textSize,
			final boolean showLinkButtons);
}
