package com.sportage.obd

import android.content.Context
import com.sportage.obd.ui.dashboard.DashboardLayoutStore
import com.sportage.obd.ui.dashboard.DashboardLayoutTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The selected layout must survive an app restart and tolerate a corrupt preference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DashboardLayoutStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val store get() = DashboardLayoutStore(context)

    @Before fun clearPrefs() {
        context.getSharedPreferences(DashboardLayoutStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun selectionRoundTripsAcrossStoreInstances() {
        assertEquals(DashboardLayoutTheme.Classic, store.load())
        store.save(DashboardLayoutTheme.CircularGauge)
        assertEquals(DashboardLayoutTheme.CircularGauge, DashboardLayoutStore(context).load())
        store.save(DashboardLayoutTheme.RectangularWidgets)
        assertEquals(DashboardLayoutTheme.RectangularWidgets, DashboardLayoutStore(context).load())
    }

    @Test fun corruptPersistedIdFallsBackToClassic() {
        context.getSharedPreferences(DashboardLayoutStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(DashboardLayoutStore.KEY_LAYOUT, "removed-layout").commit()
        assertEquals(DashboardLayoutTheme.Classic, store.load())
    }
}
