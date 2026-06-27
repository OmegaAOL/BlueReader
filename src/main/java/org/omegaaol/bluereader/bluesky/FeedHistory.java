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

import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;


// Keeps an in-memory list of all known feeds per account
public class FeedHistory {

	private static final HashMap<RedditAccount, HashSet<FeedCanonicalId>> FEEDS
			= new HashMap<>();

	private static HashSet<FeedCanonicalId> getForAccount(final RedditAccount account) {

		HashSet<FeedCanonicalId> result = FEEDS.get(account);

		if(result == null) {
			result = new HashSet<>(Constants.Bluesky.DEFAULT_FEEDS);
			FEEDS.put(account, result);
		}

		return result;
	}

	public static synchronized void addFeed(
			final RedditAccount account,
			final FeedCanonicalId id) {

		getForAccount(account).add(id);
	}

	public static synchronized void addFeeds(
			final RedditAccount account,
			final Collection<FeedCanonicalId> ids) {

		getForAccount(account).addAll(ids);
	}

	public static synchronized ArrayList<FeedCanonicalId> getFeedsSorted(
			final RedditAccount account) {

		final ArrayList<FeedCanonicalId> result = new ArrayList<>(getForAccount(
				account));
		Collections.sort(result);
		return result;
	}
}
