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
package org.omegaaol.bluereader.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Paint
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import org.omegaaol.bluereader.R
import org.omegaaol.bluereader.account.RedditAccountManager
import org.omegaaol.bluereader.activities.OAuthLoginActivity
import org.omegaaol.bluereader.common.BetterSSB
import org.omegaaol.bluereader.common.PrefsUtility
import org.omegaaol.bluereader.common.RunnableOnce
import org.omegaaol.bluereader.reddit.api.RedditOAuth
import org.omegaaol.bluereader.reddit.api.RedditOAuth.needsRelogin
import org.omegaaol.bluereader.viewholders.VH1Text
import java.util.*

class AccountListAdapter (private val context: Context, private val fragment: Fragment) :
    HeaderRecyclerAdapter<RecyclerView.ViewHolder?>() {
    private val accounts = RedditAccountManager.getInstance(context).accounts
	private val rrIconAdd: Drawable?

    init {
		val attr = context.obtainStyledAttributes(intArrayOf(R.attr.rrIconAdd))
        rrIconAdd = ContextCompat.getDrawable(context, attr.getResourceId(0, 0))
        attr.recycle()
    }

    override fun onCreateHeaderItemViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_1_text, parent, false)
        return VH1Text(v)
    }

    override fun onCreateContentItemViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_1_text, parent, false)
        return VH1Text(v)
    }

	override fun onBindHeaderItemViewHolder(
		holder: RecyclerView.ViewHolder?,
		position: Int
	) {
		val vh = holder as VH1Text
		vh.text.text = context.getString(R.string.accounts_add)
		vh.text.setCompoundDrawablesWithIntrinsicBounds(rrIconAdd, null, null, null)

		if (PrefsUtility.pref_login_legacy()) holder.itemView.setOnClickListener { v: View? -> showLoginDialog() }
		else holder.itemView.setOnClickListener { v: View? -> showLoginWarningDialog() }
	}

	// this is the custom flow for user/pass login
	private fun showLoginDialog() {

		val container = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(50, 30, 50, 40)
		}

		val handleField = EditText(context).apply {
			hint = context.getString(R.string.accounts_handle)
			typeface = android.graphics.Typeface.MONOSPACE
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
		}
		container.addView(handleField)

		val passwordField = EditText(context).apply {
			hint = context.getString(R.string.accounts_password)
			typeface = android.graphics.Typeface.MONOSPACE
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
		}
		container.addView(passwordField)

		AlertDialog.Builder(context)
			.setTitle(context.getString(R.string.accounts_title))
			.setView(container)
			.setPositiveButton(context.getString(R.string.accounts_login)) { _, _ ->
				val handle = handleField.text.toString()
				val password = passwordField.text.toString()
				RedditOAuth.completeLogin(
					context as AppCompatActivity,
					handle,
					password,
					RunnableOnce{}
				)
			}
			.setNegativeButton(context.getString(R.string.dialog_cancel), null)
			.show()

	}

	// this is the modified RR flow for oauth login
	private fun showLoginWarningDialog() {
		MaterialAlertDialogBuilder(context)
			.setMessage(
				String.format(
					Locale.US,
					"%s\n\n%s",
					context.getString(
						R.string.reddit_login_browser_popup_line_1
					),
					context.getString(
						R.string.reddit_login_browser_popup_line_2_internal_browser_only
					)
				)
			)
			.setCancelable(true)
			.setPositiveButton(
				R.string.dialog_continue
			) { dialog: DialogInterface?, which: Int -> launchLogin() }
			.setNegativeButton(
				R.string.dialog_close
			) { dialog: DialogInterface?, which: Int -> }
			.show()
	}

    private fun launchLogin() {
        val loginIntent = Intent(context, OAuthLoginActivity::class.java)
        fragment.startActivityForResult(loginIntent, 123)
    }

    override fun onBindContentItemViewHolder(
        holder: RecyclerView.ViewHolder?,
        position: Int
    ) {
		val accountManager = RedditAccountManager.getInstance(context)

		val vh = holder as VH1Text
        val account = accounts[position]
        val username = BetterSSB()
        if (account.isAnonymous) {
            username.append(context.getString(R.string.accounts_anon), 0)
        } else {
            username.append(account.username, 0)
        }
        if (account == accountManager.defaultAccount) {
            val attr = context.obtainStyledAttributes(intArrayOf(R.attr.rrListSubtitleCol))
            val col = attr.getColor(0, 0)
            attr.recycle()
            username.append(
                "  (" + context.getString(R.string.accounts_active) + ")",
                BetterSSB.FOREGROUND_COLOR or BetterSSB.SIZE,
                col,
                0,
                0.8f
            )
        }
        if (needsRelogin(account)) {
            username.append(
                "  (" + context.getString(R.string.bsky_relogin_error_title) + ")",
                BetterSSB.FOREGROUND_COLOR or BetterSSB.SIZE,
                Color.rgb(200, 50, 50),
                0,
                0.8f
            )
        }
        vh.text.text = username.get()
        vh.itemView.setOnClickListener {

			val actions = ArrayList<AccountAction>()

			if(account != accountManager.defaultAccount) {
				actions.add(AccountAction(R.string.accounts_setactive) {
					accountManager.defaultAccount = account
				})
			}

			if(account.isNotAnonymous) {
				actions.add(AccountAction(R.string.accounts_remove) {
					AlertDialog.Builder(context)
						.setTitle(R.string.accounts_remove)
						.setMessage(R.string.accounts_removal_confirmation)
						.setPositiveButton(
							R.string.accounts_remove
						) { _, _ -> accountManager.deleteAccount(account) }
						.setNegativeButton(R.string.dialog_cancel, null)
						.show()
				})
			}

			if (needsRelogin(account)) {
				actions.add(AccountAction(R.string.accounts_reauth) {
					//showLoginDialog()
				})
			}

			val items = actions.map { context.getString(it.message) }.toTypedArray()

			if (items.isNotEmpty()) {
				val builder = AlertDialog.Builder(context)
				builder.setItems(items) { dialog, which ->
					actions[which].action()
				}

				builder.setNeutralButton(R.string.dialog_cancel, null)
				val alert = builder.create()
				alert.setTitle(if (account.isAnonymous) context.getString(R.string.accounts_anon) else account.username)
				alert.setCanceledOnTouchOutside(true)
				alert.show()
			}
        }
    }

    override fun getContentItemCount(): Int {
        return accounts.size
    }

	private class AccountAction(
		@StringRes val message: Int,
		val action: () -> Unit
	)
}
