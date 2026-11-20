package app.tgdrive.rebuild

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.data.repository.SettingsRepositoryImpl
import com.infinity.drive.data.local.preferences.PreferenceKeys
import com.infinity.drive.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class PreferenceCustomizationTest {
    private class Store:DataStore<Preferences>{
        val state=MutableStateFlow<Preferences>(emptyPreferences());private val mutex=Mutex()
        override val data:Flow<Preferences> =state
        override suspend fun updateData(transform:suspend (Preferences)->Preferences):Preferences=mutex.withLock{transform(state.value).also{state.value=it}}
    }
    private val cipher=object:CredentialCipher{override fun encrypt(plaintext:ByteArray)=plaintext.copyOf();override fun decrypt(ciphertext:ByteArray)=ciphertext.copyOf()}
    @Test fun newPreferencesHaveCompatibleDefaultsAndSurviveRepositoryRecreation()=runBlocking{
        val store=Store();val repository=SettingsRepositoryImpl(store,cipher)
        assertEquals(DashboardStyle.CLASSIC,repository.preferences.first().dashboardStyle)
        assertEquals(setOf("ORBIT","RIBBON"),repository.preferences.first().dashboardDarkStyles)
        assertEquals(0,repository.preferences.first().backupMinFileSizeMb)
        repository.update{it.copy(accentColour=AccentColour.TEAL,dashboardStyle=DashboardStyle.ORBIT,dashboardDarkStyles=setOf("CLASSIC","ORBIT"),backupMinFileSizeMb=16,backupMaxFileSizeMb=2048)}
        val read=SettingsRepositoryImpl(store,cipher).preferences.first()
        assertEquals(AccentColour.TEAL,read.accentColour);assertEquals(DashboardStyle.ORBIT,read.dashboardStyle)
        assertEquals(setOf("CLASSIC","ORBIT"),read.dashboardDarkStyles)
        assertEquals(16,read.backupMinFileSizeMb);assertEquals(2048,read.backupMaxFileSizeMb)
    }
    @Test fun invertedBoundsAreRejectedAtomicallyAndUnknownStyleValuesFallBack()=runBlocking{
        val store=Store();val repository=SettingsRepositoryImpl(store,cipher)
        repository.update{it.copy(backupMinFileSizeMb=10,backupMaxFileSizeMb=100)}
        try{repository.update{it.copy(backupMaxFileSizeMb=5)};fail("Invalid ranges must not be saved")}catch(_:IllegalArgumentException){}
        assertEquals(100,repository.preferences.first().backupMaxFileSizeMb)
        store.edit{it[PreferenceKeys.DASHBOARD_STYLE]="future-style";it[PreferenceKeys.ACCENT_COLOUR]="future-colour"}
        assertEquals(DashboardStyle.CLASSIC,repository.preferences.first().dashboardStyle)
        assertEquals(AccentColour.BLUE,repository.preferences.first().accentColour)
    }
    @Test fun replacedDashboardStylesMigrateWithoutResettingOtherPreferences()=runBlocking{
        val store=Store();val repository=SettingsRepositoryImpl(store,cipher)
        repository.update{it.copy(accentColour=AccentColour.TEAL)}
        listOf("AURORA" to DashboardStyle.HORIZON,"COMPACT" to DashboardStyle.STRATA).forEach{(old,new)->
            store.edit{it[PreferenceKeys.DASHBOARD_STYLE]=old}
            assertEquals(new,repository.preferences.first().dashboardStyle)
            assertEquals(AccentColour.TEAL,repository.preferences.first().accentColour)
        }
        store.edit{it[PreferenceKeys.DASHBOARD_DARK_STYLES]=setOf("STRATA","unknown-style")}
        assertEquals(setOf("STRATA"),repository.preferences.first().dashboardDarkStyles)
    }

    @Test fun customThemesPreserveBothPalettesAndTheFallbackAcrossRepositoryRecreation() = runBlocking {
        val store = Store()
        val repository = SettingsRepositoryImpl(store, cipher)
        val light = ThemePalette(0xFF2356A8, 0xFF365E72, 0xFF256C62, 0xFFF0F5FF,
            overrides = mapOf("PRIMARY_CONTAINER" to 0xFFBFD5FF))
        val dark = ThemePalette(0xFFADCBFC, 0xFF81BCD2, 0xFF89D5CC, 0xFF182436,
            overrides = mapOf("OUTLINE" to 0xFFB1BDCC))
        val theme = CustomTheme("blue-hour", "Blue Hour", light, dark)
        repository.update { it.selectAccentColour(AccentColour.TIDAL).saveCustomTheme(theme).copy(iconStyle = AppIconStyle.BROKEN) }
        val reopened = SettingsRepositoryImpl(store, cipher)
        val saved = reopened.preferences.first()
        assertEquals(theme, saved.selectedCustomTheme)
        assertEquals(AppIconStyle.BROKEN, saved.iconStyle)
        reopened.update { it.saveCustomTheme(theme.copy(name = "Evening")) }
        assertEquals(listOf(theme.copy(name = "Evening")), reopened.preferences.first().customThemes)
        reopened.update { it.deleteCustomTheme(theme.id) }
        val deleted = reopened.preferences.first()
        assertNull(deleted.selectedCustomTheme)
        assertTrue(deleted.customThemes.isEmpty())
        assertEquals(AccentColour.TIDAL, deleted.accentColour)
    }
}
