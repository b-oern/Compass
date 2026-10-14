/*
 * This file is part of Compass.
 * Copyright (C) 2026 Philipp Bobek <philipp.bobek@mailbox.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Compass is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.bsnx.apps.compass

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceFragmentCompat
import net.bsnx.apps.compass.db.CompassDB
import net.bsnx.apps.compass.preference.PreferenceConstants
import net.bsnx.apps.compass.preference.PreferenceStore

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(PreferenceConstants.VERSION)?.summaryProvider =
            SummaryProvider<Preference> { BuildConfig.VERSION_NAME }

        findPreference<EditTextPreference>(PreferenceConstants.STEP_LENGTH)?.apply {
            summaryProvider = SummaryProvider<EditTextPreference> {
                val length = PreferenceStore.parseStepLength(it.text) ?: PreferenceConstants.STEP_LENGTH_DEFAULT
                "%.2f m".format(length)
            }
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                it.keyListener = DigitsKeyListener.getInstance("0123456789.,")
            }
            setOnPreferenceChangeListener { _, newValue ->
                val valid = PreferenceStore.parseStepLength(newValue as? String) != null
                if (!valid) {
                    Toast.makeText(requireContext(), "Ungültige Schrittweite (0,1 – 3 m)", Toast.LENGTH_SHORT).show()
                }
                valid
            }
        }

        findPreference<Preference>(PreferenceConstants.THIRD_PARTY_LICENSES)?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_ThirdPartyLicensesFragment)
            true
        }

        findPreference<Preference>("pref_path_list")?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), DaysActivity::class.java)
                //.apply {
                //putExtra(ListActivity.EXTRA_DAY_UNIX,
                //    CompassDB.dateToUnix(CompassDB.getTodayDate()))
            //}
            startActivity(intent)
            true
        }
        // TODO addPreference
    }

    fun addPreference(title: String, description: String, onClick: () -> Unit) {
        val preference = Preference(requireContext()).apply {
            this.title = title
            summary = description
            setOnPreferenceClickListener {
                onClick()
                true
            }
        }
        preferenceScreen.addPreference(preference)
    }
}
