package app.tgdrive.rebuild

import androidx.compose.ui.geometry.Offset
import com.infinity.drive.presentation.gallery.PhotoPlace
import com.infinity.drive.presentation.gallery.captureMonth
import com.infinity.drive.presentation.gallery.exifCaptureTime
import com.infinity.drive.presentation.gallery.projectPlace
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoHistoryTest {
    @Test fun `timeline groups photos by local month`() {
        val timestamp = Instant.parse("2026-01-31T23:30:00Z").toEpochMilli()
        assertEquals(YearMonth.of(2026, 2), captureMonth(timestamp, ZoneId.of("Africa/Casablanca")))
        assertEquals(YearMonth.of(2026, 1), captureMonth(timestamp, ZoneId.of("UTC")))
    }

    @Test fun `capture dates use the photo timezone when present`() {
        val raw = "2026:01:31 23:30:00"
        val utc = ZoneId.of("UTC")
        assertEquals(Instant.parse("2026-01-31T22:30:00Z").toEpochMilli(),
            exifCaptureTime(raw, "+01:00", utc))
        assertEquals(Instant.parse("2026-01-31T23:30:00Z").toEpochMilli(),
            exifCaptureTime(raw, null, utc))
        assertNull(exifCaptureTime("2026:02:30 23:30:00", "+01:00", utc))
    }

    @Test fun `map points retain east and north through pan and zoom`() {
        assertEquals(Offset(180f, 90f), projectPlace(PhotoPlace(0.0, 0.0), 360f, 180f, 1f, Offset.Zero))
        assertEquals(Offset(230f, 5f), projectPlace(PhotoPlace(latitude = 45.0, longitude = 30.0), 360f, 180f, 2f, Offset(-10f, 5f)))
    }
}
