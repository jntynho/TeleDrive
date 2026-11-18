package app.tgdrive.rebuild

import androidx.compose.ui.geometry.Offset
import com.infinity.drive.presentation.gallery.PhotoPlace
import com.infinity.drive.presentation.gallery.captureMonth
import com.infinity.drive.presentation.gallery.projectPlace
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoHistoryTest {
    @Test fun `timeline groups photos by local month`() {
        val timestamp = Instant.parse("2026-01-31T23:30:00Z").toEpochMilli()
        assertEquals(YearMonth.of(2026, 2), captureMonth(timestamp, ZoneId.of("Africa/Casablanca")))
        assertEquals(YearMonth.of(2026, 1), captureMonth(timestamp, ZoneId.of("UTC")))
    }

    @Test fun `map points retain east and north through pan and zoom`() {
        assertEquals(Offset(180f, 90f), projectPlace(PhotoPlace(0.0, 0.0), 360f, 180f, 1f, Offset.Zero))
        assertEquals(Offset(230f, 5f), projectPlace(PhotoPlace(latitude = 45.0, longitude = 30.0), 360f, 180f, 2f, Offset(-10f, 5f)))
    }
}
