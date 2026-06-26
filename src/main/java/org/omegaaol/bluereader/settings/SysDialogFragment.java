package org.omegaaol.bluereader.settings;

import android.app.AlertDialog;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.activities.BaseActivity;

import java.util.concurrent.atomic.AtomicInteger;

public class SysDialogFragment extends ListPreferenceDialogFragmentCompat {
	public void updater(ListPreference preference, int number){
		preference.setValueIndex(number);
		if (preference.getOnPreferenceChangeListener() != null) {
			preference.getOnPreferenceChangeListener()
					.onPreferenceChange(preference, preference.getEntryValues()[number]);
		}
	}

	@Override
	public AlertDialog onCreateDialog(Bundle savedInstanceState) {
		ListPreference preference = (ListPreference) getPreference();
		final AtomicInteger selectedIndex = new AtomicInteger();
		selectedIndex.set(preference.findIndexOfValue(preference.getValue()));

		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		builder.setTitle(preference.getTitle());
		builder.setSingleChoiceItems(preference.getEntries(),
				preference.findIndexOfValue(preference.getValue()),
				(dialog, which) -> {
					selectedIndex.set(which);
				});
		builder.setPositiveButton(getContext().getString(R.string.dialog_apply),
				(dialog, id) -> {
					updater(preference, selectedIndex.get());
					dialog.dismiss();
					SysDialogFragment.this.dismiss();
					getActivity().recreate(); // recreation for listprefs
				});
		builder.setNegativeButton(getContext().getString(R.string.dialog_cancel), null);
		return builder.create();
	}

	public static SysDialogFragment newInstance(String key) {
		SysDialogFragment fragment = new SysDialogFragment();
		Bundle b = new Bundle(1);
		b.putString(ARG_KEY, key);
		fragment.setArguments(b);
		return fragment;
	}
}
