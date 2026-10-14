/*
 * This file is part of Compass.
 * Copyright (C) 2024 Philipp Bobek <philipp.bobek@mailbox.org>
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

package net.bsnx.apps.compass.preference

object PreferenceConstants {

    const val TRUE_NORTH = "true_north"

    const val HAPTIC_FEEDBACK = "haptic_feedback"

    const val SCREEN_ORIENTATION_LOCKED = "screen_orientation_locked"

    const val NIGHT_MODE = "night_mode"
    const val NIGHT_MODE_VALUE_FOLLOW_SYSTEM = "follow_system"
    const val NIGHT_MODE_VALUE_NO = "no"
    const val NIGHT_MODE_VALUE_YES = "yes"

    const val ACCESS_LOCATION_PERMISSION_REQUESTED = "access_location_permission_requested"

    /** Schrittweite in Metern, als String gespeichert (EditTextPreference). */
    const val STEP_LENGTH = "step_length"
    const val STEP_LENGTH_DEFAULT = 0.9f

    /** GPS-Positionen als Referenz zur Schrittmessung aufzeichnen. */
    const val GPS_TRACKING = "gps_tracking"

    /** ID des PathItems, an dem das nächste „Add“ fortsetzt (per Long-Press in der Karte). */
    const val ACTIVE_ITEM_ID = "active_item_id"

    const val THIRD_PARTY_LICENSES = "third_party_licenses"

    const val VERSION = "version"
}
