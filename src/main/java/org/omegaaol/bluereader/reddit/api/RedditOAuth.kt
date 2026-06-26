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
package org.omegaaol.bluereader.reddit.api

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import org.json.JSONObject
import org.omegaaol.bluereader.R
import org.omegaaol.bluereader.account.RedditAccount
import org.omegaaol.bluereader.account.RedditAccountManager
import org.omegaaol.bluereader.cache.CacheRequest.RequestFailureType
import org.omegaaol.bluereader.common.*
import org.omegaaol.bluereader.common.General.closeSafely
import org.omegaaol.bluereader.common.General.readWholeStreamAsUTF8
import org.omegaaol.bluereader.common.General.safeDismissDialog
import org.omegaaol.bluereader.common.General.uriFromString
import org.omegaaol.bluereader.http.FailedRequestBody
import org.omegaaol.bluereader.http.HTTPBackend
import org.omegaaol.bluereader.http.HTTPBackend.RequestDetails
import org.omegaaol.bluereader.http.body.HTTPRequestBodyJson
import org.omegaaol.bluereader.jsonwrap.JsonValue
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object RedditOAuth {
	private const val TAG = "RedditOAuth"
    private const val REDIRECT_URI_NEW = "redreader://rr_oauth_redir"
    private const val ALL_SCOPES = ("identity edit flair history "
            + "modconfig modflair modlog modposts modwiki mysubreddits "
            + "privatemessages read report save submit subscribe vote "
            + "wikiedit wikiread")
    private val URL_BASE = Constants.Bluesky.getScheme() + "://" + Constants.Bluesky.getPDSHost();
	private val URL_CREATESESSION = URL_BASE + Constants.Bluesky.PATH_CREATESESSION;
	private val URL_REFRESHSESSION = URL_BASE + Constants.Bluesky.PATH_REFRESHSESSION;

    @JvmStatic
	val promptUri: Uri
        get() {
            val uri = "https://www.reddit.com/api/v1/authorize.compact".toUri().buildUpon()
            uri.appendQueryParameter("response_type", "code")
            uri.appendQueryParameter("duration", "permanent")
            uri.appendQueryParameter("state", "Texas")
            uri.appendQueryParameter("redirect_uri", REDIRECT_URI_NEW)
            uri.appendQueryParameter("client_id", appId)
            uri.appendQueryParameter("scope", ALL_SCOPES)
            return uri.build()
        }

	private val appId: String?
		get() = PrefsUtility.pref_reddit_client_id_override() ?: GlobalConfig.appId

	private val cachedAppId = CachedStringHash { appId ?: "null" }

	fun init(context: Context) {
		try {
			val fileContents = context.assets.open("reddit_auth.txt").use {
				readWholeStreamAsUTF8(it)
			}.split("\"")

			if (fileContents.size != 3) {
				throw RuntimeException("Invalid file contents: $fileContents")
			}

			val id = fileContents[1].trim()

			if (id.isEmpty()) {
				throw RuntimeException("No ID provided in reddit_auth.txt")
			}

			GlobalConfig.appId = id

		} catch (e: Exception) {
			Log.i(TAG, "Got exception during init", e)
		}
	}

	private fun checkAccess(context: Context, user: RedditAccount?): RRError? {
		if (!PrefsUtility.isRedditUserAgreementAccepted()) {
			return RRError(
				title = context.getString(R.string.bsky_terms_error_title),
				message = context.getString(R.string.bsky_terms_error_message),
				resolution = RRError.Resolution.ACCEPT_BLUESKY_TERMS,
				reportable = true
			)
		}

		if (user?.run(::needsRelogin) == true) {
			return RRError(
				title = context.getString(R.string.bsky_relogin_error_title),
				message = context.getString(R.string.bsky_relogin_error_message),
				resolution = RRError.Resolution.ACCOUNTS_LIST,
				reportable = true
			)
		}

		return null
	}

	@JvmStatic
	fun needsRelogin(user: RedditAccount) = !user.isAnonymous && user.clientId != cachedAppId.hash

	@JvmStatic
	fun anyNeedRelogin(context: Context) =
		RedditAccountManager.getInstance(context).accounts.any(this::needsRelogin)

    private fun handleTokenFetchError(
        exception: Throwable?,
        httpStatus: Int?,
        context: Context,
        uri: String,
		body: Optional<FailedRequestBody>
    ): FetchTokensResult {
		Log.e("OAuth", "ERROR: " + body.get().toJson().get().asObject()?.getString("error") + ", MESSAGE: " + body.get().toJson().get().asObject()?.getString("message"))
        return if (httpStatus != null && httpStatus != 200) {
			var title = context.getString(R.string.error_unknown_title) // default unknown
			var message = context.getString(R.string.message_cannotlogin)

			if (httpStatus == 400){
				title = context.getString(R.string.title_error_400)
				message = context.getString(R.string.message_error_400)
			}

			if (body.isPresent) {
				val error = body.get().toJson().get().asObject()?.getString("error")
				title = context.getString(R.string.error_login_failed)
				message = when (error) {
					context.getString(R.string.error_authentication_required_code) ->
						context.getString(R.string.error_authentication_required)

					context.getString(R.string.error_2fa_required_code) ->
						context.getString(R.string.error_2fa_required)

					context.getString(R.string.error_rate_limit_code) ->
						context.getString(R.string.error_rate_limit)

					else ->
						context.getString(R.string.error_message_prefix) + " " + error
				}
			}

            FetchTokensResult(
                FetchTokenResultStatus.UNKNOWN_ERROR,
                RRError(
                    title,
                    message,
                    true,
                    null,
                    httpStatus,
                    uri,
                    null
                )
            )
        } else if (exception is IOException) {
            FetchTokensResult(
                FetchTokenResultStatus.CONNECTION_ERROR,
                RRError(
                    context.getString(R.string.error_connection_title),
                    context.getString(R.string.error_connection_message),
                    true,
                    exception,
                    null,
                    uri,
                    null
                )
            )
        } else {
            FetchTokensResult(
                FetchTokenResultStatus.UNKNOWN_ERROR,
                RRError(
                    context.getString(R.string.error_unknown_title),
                    context.getString(R.string.error_unknown_message),
                    true,
                    exception,
                    null,
                    uri,
                    null
                )
            )
        }
    }

    @SuppressLint("SuspiciousIndentation")
	@JvmStatic
	public final fun fetchTokensWithCredentials( // can fetch tokens using either an old refresh token or credentials.
		context: Context,
		identifier: String,
		password: String
    ): FetchTokensResult {

		checkAccess(context, null)?.apply {
			return FetchTokensResult(FetchTokenResultStatus.INVALID_REQUEST, this)
		}

        val uri = URL_CREATESESSION
		val jsonObject = JSONObject()
		jsonObject.put("identifier", identifier)
		jsonObject.put("password", password)

        return try {
            val request = HTTPBackend.getBackend().prepareRequest(
                context,
                RequestDetails(
                    uriFromString(uri)!!,
                    Optional.of(HTTPRequestBodyJson(jsonObject)),
					true
                )
            )
			request.addHeader("Content-Type", "application/json")
            val result = AtomicReference<FetchTokensResult>()
            request.executeInThisThread(object : HTTPBackend.Listener {
				override fun onError(
					@RequestFailureType failureType: Int,
					exception: Throwable?,
					httpStatus: Int?,
					body: Optional<FailedRequestBody>
				) {
					result.set(
						handleTokenFetchError(
							exception,
							httpStatus,
							context,
							uri,
							body // omegabm4
						)
					)
				}

                override fun onSuccess(
                    mimetype: String,
                    bodyBytes: Long,
                    body: InputStream
                ) {
                    try {

                        val jsonValue = JsonValue.parse(body)
                        val responseObject = jsonValue.asObject()

                        val refreshToken = RefreshToken(responseObject!!.getString("refreshJwt"))
                        val accessToken = AccessToken(
                            responseObject.getString("accessJwt")
                        )

                        result.set(
                            FetchTokensResult(
                                refreshToken,
                                accessToken
                            )
                        )
                    } catch (e: IOException) {
                        result.set(
                            FetchTokensResult(
                                FetchTokenResultStatus.CONNECTION_ERROR,
                                RRError(
                                    context.getString(R.string.error_connection_title),
                                    context.getString(R.string.error_connection_message),
                                    true,
                                    e,
                                    null,
                                    uri,
                                    null
                                )
                            )
                        )
                    } catch (t: Throwable) {
                        throw RuntimeException(t)
                    } finally {
                        closeSafely(body)
                    }
                }
            })
            result.get()
        } catch (t: Throwable) {
            FetchTokensResult(
                FetchTokenResultStatus.UNKNOWN_ERROR,
                RRError(
                    context.getString(R.string.error_unknown_title),
                    context.getString(R.string.error_unknown_message),
                    true,
                    t,
                    null,
                    uri,
                    null
                )
            )
        }
    }

	@JvmStatic
	public final fun fetchTokensWithRefresh( // fetcher with refreshtoken
		context: Context,
		refreshToken: RefreshToken
	): FetchTokensResult {

		Log.e("OAuth", "Running token retriever (from refresh token)")
		//Log.e("OAuth", "Refresh token JWT: " + refreshToken.token)

		checkAccess(context, null)?.apply {
			return FetchTokensResult(FetchTokenResultStatus.INVALID_REQUEST, this)
		}

		var uri = URL_REFRESHSESSION

		return try {
			val request = HTTPBackend.getBackend().prepareRequest(
				context,
				RequestDetails(
					uriFromString(uri)!!,
					Optional.empty(),
					true
				)
			)
			request.addHeader("Authorization", "Bearer " + refreshToken.token)
			request.addHeader("Accept", "application/json")

			val result = AtomicReference<FetchTokensResult>()
			request.executeInThisThread(object : HTTPBackend.Listener {
				override fun onError(
					@RequestFailureType failureType: Int,
					exception: Throwable?,
					httpStatus: Int?,
					body: Optional<FailedRequestBody>
				) {
					result.set(
						handleTokenFetchError(
							exception,
							httpStatus,
							context,
							uri,
							body // omegabm4
						)
					)
				}

				override fun onSuccess(
					mimetype: String,
					bodyBytes: Long,
					body: InputStream
				) {
					try {

						val jsonValue = JsonValue.parse(body)
						val responseObject = jsonValue.asObject()

						val refreshToken = RefreshToken(responseObject!!.getString("refreshJwt"))
						val accessToken = AccessToken(
							responseObject.getString("accessJwt")
						)

						result.set(
							FetchTokensResult(
								refreshToken,
								accessToken
							)
						)
					} catch (e: IOException) {
						result.set(
							FetchTokensResult(
								FetchTokenResultStatus.CONNECTION_ERROR,
								RRError(
									context.getString(R.string.error_connection_title),
									context.getString(R.string.error_connection_message),
									true,
									e,
									null,
									uri,
									null
								)
							)
						)
					} catch (t: Throwable) {
						throw RuntimeException(t)
					} finally {
						closeSafely(body)
					}
				}
			})
			result.get()
		} catch (t: Throwable) {
			FetchTokensResult(
				FetchTokenResultStatus.UNKNOWN_ERROR,
				RRError(
					context.getString(R.string.error_unknown_title),
					context.getString(R.string.error_unknown_message),
					true,
					t,
					null,
					uri,
					null
				)
			)
		}
	}

    private fun loginAsynchronous(
        context: Context,
        handle: String,
		password: String,
        listener: LoginListener
    ) {
        object : Thread() {
            override fun run() {
                try {
					if (handle.isNullOrEmpty() || password.isNullOrEmpty()) {
						throw Throwable(context.getString(R.string.error_login_fields_empty))
					}
                    val fetchTokensResult = fetchTokensWithCredentials(context, handle, password)
                    if (fetchTokensResult.status
                        != FetchTokenResultStatus.SUCCESS
                    ) {
                        listener.onLoginFailure(
                            LoginError.fromFetchRefreshTokenStatus(
                                fetchTokensResult.status
                            ),
                            fetchTokensResult.error
                        )
                        return
                    }

                    val account = RedditAccount(
                        handle,
                        fetchTokensResult.refreshToken,
                        0,
						cachedAppId.hash
                    )
                    account.setAccessToken(fetchTokensResult.accessToken)
                    val accountManager = RedditAccountManager.getInstance(context)
                    accountManager.addAccount(account)
                    accountManager.defaultAccount = account
                    listener.onLoginSuccess(account)
                } catch (t: Throwable) {
					val message: String = t.message ?: context.getString(R.string.error_unknown_message)
                    listener.onLoginFailure(
                        LoginError.UNKNOWN_ERROR,
                        RRError(
							context.getString(R.string.error_login_failed),
                            message,
                            true,
                            t
                        )
                    )
                }
            }
        }.start()
    }

    @JvmStatic
	fun completeLogin(
        activity: AppCompatActivity,
        handle: String,
		password: String,
        onDone: RunnableOnce
    ) {
        val progressDialog = ProgressDialog(activity)
        progressDialog.setTitle(R.string.accounts_loggingin)
        progressDialog.setMessage(
            activity.applicationContext.getString(
                R.string.accounts_loggingin_msg
            )
        )
        progressDialog.isIndeterminate = true
        progressDialog.setCancelable(true)
        progressDialog.setCanceledOnTouchOutside(false)
        val cancelled = AtomicBoolean(false)
        progressDialog.setOnCancelListener {
			if (!cancelled.getAndSet(true)) {
                safeDismissDialog(progressDialog)
                onDone.run()
            }
        }
        progressDialog.setOnKeyListener { _: DialogInterface?, keyCode: Int, _: KeyEvent? ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (!cancelled.getAndSet(true)) {
                    safeDismissDialog(progressDialog)
                    onDone.run()
                }
            }
            true
        }
        progressDialog.show()
        loginAsynchronous(
            activity.applicationContext,
            handle,
			password,
            object : LoginListener {
                override fun onLoginSuccess(account: RedditAccount?) {
                    AndroidCommon.UI_THREAD_HANDLER.post {
                        if (cancelled.get()) {
                            return@post
                        }
                        safeDismissDialog(progressDialog)
						onDone.run()
                    }
                }

                override fun onLoginFailure(
                    error: LoginError?,
                    details: RRError?
                ) {
                    AndroidCommon.UI_THREAD_HANDLER.post {
                        if (cancelled.get()) {
                            return@post
                        }
                        safeDismissDialog(progressDialog)
                        val builder = AlertDialog.Builder(activity)
                        builder.setNeutralButton(
                            R.string.dialog_close
                        ) { _: DialogInterface?, _: Int -> onDone.run() }
                        builder.setOnCancelListener { onDone.run() }
                        if (Build.VERSION.SDK_INT >= 17) {
                            builder.setOnDismissListener { onDone.run() }
                        }
                        builder.setTitle(details!!.title)
                        builder.setMessage(details.message)
                        builder.show()
                    }
                }
            })
    }

    open class Token(@JvmField val token: String) {
        override fun toString(): String {
            return token
        }
    }

    class AccessToken(token: String?) : Token(token!!) {
        private val mMonotonicTimestamp = SystemClock.elapsedRealtime()

		val isExpired: Boolean
            get() {
                val halfHourInMs = (30 * 60 * 1000).toLong()
                return mMonotonicTimestamp + halfHourInMs < SystemClock.elapsedRealtime()
            }
    }

    class RefreshToken(token: String?) : Token(token!!)
    enum class FetchTokenResultStatus {
        SUCCESS, USER_REFUSED_PERMISSION, INVALID_REQUEST, INVALID_RESPONSE, CONNECTION_ERROR, UNKNOWN_ERROR
    }

    enum class FetchUserInfoResultStatus {
        SUCCESS, INVALID_RESPONSE, CONNECTION_ERROR, UNKNOWN_ERROR
    }

    class FetchTokensResult {
		@JvmField
        val status: FetchTokenResultStatus
		@JvmField
        val error: RRError?
		@JvmField
        val refreshToken: RefreshToken?
		@JvmField
        val accessToken: AccessToken?

        constructor(
			status: FetchTokenResultStatus,
			error: RRError?
        ) {
            this.status = status
            this.error = error
            refreshToken = null
            accessToken = null
        }

        constructor(
            refreshToken: RefreshToken?,
            accessToken: AccessToken?
        ) {
            status = FetchTokenResultStatus.SUCCESS
            error = null
            this.refreshToken = refreshToken
            this.accessToken = accessToken
        }
    }

    enum class LoginError {
        SUCCESS, USER_REFUSED_PERMISSION, CONNECTION_ERROR, UNKNOWN_ERROR;

        companion object {
            fun fromFetchRefreshTokenStatus(status: FetchTokenResultStatus?) =
				when (status) {
					FetchTokenResultStatus.SUCCESS -> SUCCESS
					FetchTokenResultStatus.USER_REFUSED_PERMISSION -> USER_REFUSED_PERMISSION
					FetchTokenResultStatus.INVALID_REQUEST -> UNKNOWN_ERROR
					FetchTokenResultStatus.INVALID_RESPONSE -> UNKNOWN_ERROR
					FetchTokenResultStatus.CONNECTION_ERROR -> CONNECTION_ERROR
					FetchTokenResultStatus.UNKNOWN_ERROR -> UNKNOWN_ERROR
					else -> UNKNOWN_ERROR
				}

            fun fromFetchUserInfoStatus(status: FetchUserInfoResultStatus?) =
				when (status) {
					FetchUserInfoResultStatus.SUCCESS -> SUCCESS
					FetchUserInfoResultStatus.INVALID_RESPONSE -> UNKNOWN_ERROR
					FetchUserInfoResultStatus.CONNECTION_ERROR -> CONNECTION_ERROR
					FetchUserInfoResultStatus.UNKNOWN_ERROR -> UNKNOWN_ERROR
					else -> UNKNOWN_ERROR
				}
        }
    }

    interface LoginListener {
        fun onLoginSuccess(account: RedditAccount?)
        fun onLoginFailure(error: LoginError?, details: RRError?)
    }



}
