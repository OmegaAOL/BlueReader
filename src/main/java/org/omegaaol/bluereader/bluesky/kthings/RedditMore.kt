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

package org.omegaaol.bluereader.bluesky.kthings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.omegaaol.bluereader.bluesky.url.PostCommentListingURL
import org.omegaaol.bluereader.bluesky.url.RedditURLParser
import org.omegaaol.bluereader.bluesky.url.RedditURLParser.RedditURL

@Suppress("PropertyName")
@Serializable
@Parcelize
data class RedditMore(
	var count: Int,
	var children: List<String>,
	var parent_id: String
) : Parcelable {

	fun getMoreUrls(
		commentListingURL: RedditURL
	): List<PostCommentListingURL> {

		val urls = ArrayList<PostCommentListingURL>(16)

		if (commentListingURL.pathType() == RedditURLParser.POST_COMMENT_LISTING_URL) {
			if (count > 0) {
				for (child in children) {
					urls.add(commentListingURL.asPostCommentListURL().commentId(child))
				}
			} else {
				urls.add(commentListingURL.asPostCommentListURL().commentId(parent_id))
			}
		}
		return urls
	}
}
