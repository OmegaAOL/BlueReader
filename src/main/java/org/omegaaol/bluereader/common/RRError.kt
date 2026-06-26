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

package org.omegaaol.bluereader.common

import org.omegaaol.bluereader.http.FailedRequestBody

class RRError @JvmOverloads constructor(
	@JvmField val title: String? = null,
	@JvmField val message: String? = null,
	@JvmField val reportable: Boolean? = true,
	@JvmField val t: Throwable? = null,
	@JvmField val httpStatus: Int? = null,
	@JvmField val url: String? = null,
	@JvmField val debuggingContext: String? = null,
	response: Optional<FailedRequestBody> = Optional.empty(),
	@JvmField val resolution: Resolution? = null
) {
	enum class Resolution {
		ACCEPT_BLUESKY_TERMS,
		ACCOUNTS_LIST
	}

    @JvmField
	val response = response.map(FailedRequestBody::toString).orElseNull()

	override fun toString() = "$title: $message (http: $httpStatus, thrown: $t)"
}
