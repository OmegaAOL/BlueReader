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

package org.omegaaol.bluereader.common;

import org.omegaaol.bluereader.common.time.TimeDuration;
import org.omegaaol.bluereader.common.time.TimestampUTC;

public abstract class TimestampBound {

	public abstract boolean verifyTimestamp(TimestampUTC timestamp);

	public static final TimestampBound ANY = new TimestampBound() {
		@Override
		public boolean verifyTimestamp(final TimestampUTC timestamp) {
			return true;
		}
	};
	public static final TimestampBound NONE = new TimestampBound() {
		@Override
		public boolean verifyTimestamp(final TimestampUTC timestamp) {
			return false;
		}
	};

	public static final class MoreRecentThanBound extends TimestampBound {

		private final TimestampUTC minTimestamp;

		public MoreRecentThanBound(final TimestampUTC minTimestamp) {
			this.minTimestamp = minTimestamp;
		}

		@Override
		public boolean verifyTimestamp(final TimestampUTC timestamp) {
			return timestamp.isGreaterThan(minTimestamp);
		}
	}

	public static MoreRecentThanBound notOlderThan(final TimeDuration age) {
		return new MoreRecentThanBound(TimestampUTC.now().subtract(age));
	}
}
