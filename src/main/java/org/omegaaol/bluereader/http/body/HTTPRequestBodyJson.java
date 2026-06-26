package org.omegaaol.bluereader.http.body;

import androidx.annotation.NonNull;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class HTTPRequestBodyJson implements HTTPRequestBody {

	@NonNull
	private final JSONObject mJson;

	public HTTPRequestBodyJson(@NonNull JSONObject json) {
		this.mJson = json;
	}

	@NonNull
	public JSONObject getJson() {
		return mJson;
	}

	@NonNull
	@Override
	public <E> E visit(@NonNull Visitor<E> visitor) {
		return visitor.visitRequestBody(this);
	}
}
