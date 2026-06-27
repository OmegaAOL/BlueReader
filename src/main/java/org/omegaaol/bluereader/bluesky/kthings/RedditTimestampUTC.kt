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

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.omegaaol.bluereader.common.time.TimestampUTC

@Serializable(with = RedditTimestampUTCSerializer::class)
@Parcelize
data class RedditTimestampUTC(
	val value: @WriteWith<TimestampUTCParceler> TimestampUTC
) : Parcelable

object RedditTimestampUTCSerializer : KSerializer<RedditTimestampUTC> {
	override val descriptor: SerialDescriptor
		get() = PrimitiveSerialDescriptor("RedditTimestampUTC", PrimitiveKind.DOUBLE)

	override fun deserialize(decoder: Decoder) =
		RedditTimestampUTC(value = TimestampUTC.fromUtcSecs((decoder.decodeDouble().toLong())))

	override fun serialize(encoder: Encoder, value: RedditTimestampUTC) {
		encoder.encodeDouble(value.value.toUtcSecs().toDouble())
	}
}

object TimestampUTCParceler : Parceler<TimestampUTC> {
	override fun create(parcel: Parcel)
			= TimestampUTC.fromUtcMs(parcel.readLong())

	override fun TimestampUTC.write(parcel: Parcel, flags: Int) {
		parcel.writeLong(toUtcMs())
	}

}
