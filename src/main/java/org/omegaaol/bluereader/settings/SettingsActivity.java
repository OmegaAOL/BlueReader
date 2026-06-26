package org.omegaaol.bluereader.settings;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.common.PrefsUtility;

public class SettingsActivity extends BaseActivity {

	private String currentPanel = "root";

	private void launchFragment(@NonNull final String panel) {
		final Bundle bundle = new Bundle();
		bundle.putString("panel", panel);

		getSupportFragmentManager()
				.beginTransaction()
				.setReorderingAllowed(false)
				.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
				.replace(R.id.single_fragment_container, SettingsFragment.class, bundle)
				.addToBackStack("Settings: " + panel)
				.commit();

		currentPanel = panel;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		PrefsUtility.applyThemeAccent(this);
		super.onCreate(savedInstanceState);
		setBaseActivityListing(R.layout.single_fragment_layout);

		if (savedInstanceState != null) {
			currentPanel = savedInstanceState.getString("current_panel", "root");
		}

		if (savedInstanceState == null) {
			final Bundle bundle = new Bundle();
			bundle.putString("panel", currentPanel);

			getSupportFragmentManager()
					.beginTransaction()
					.setReorderingAllowed(false)
					.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
					.replace(R.id.single_fragment_container, SettingsFragment.class, bundle)
					.commit();
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("current_panel", currentPanel);
	}

	public void onPanelSelected(@NonNull final String panel) {
		launchFragment(panel);
	}
}
