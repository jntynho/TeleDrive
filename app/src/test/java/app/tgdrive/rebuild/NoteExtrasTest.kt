package app.tgdrive.rebuild

import com.infinity.drive.presentation.note.NoteExtras
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteExtrasTest {
    @Test fun attachmentNamesCannotBreakTheInternalLink() {
        val link = NoteExtras.attachmentLink("scan[2]\n.jpg", "123-456")
        assertEquals(listOf(NoteExtras.Attachment("scan(2) .jpg", "123-456")), NoteExtras.attachments("$link\n$link"))
        assertEquals(emptyList<NoteExtras.Attachment>(), NoteExtras.attachments("[other](https://example.com)"))
    }

    @Test fun checklistsToggleOneLineAndKeepOtherTextAndLineEndings() {
        val body = "Plan\r\n- [ ] First\r\n  * [x] Second\r\n"
        val item = NoteExtras.tasks(body).first()
        assertEquals("Plan\r\n- [x] First\r\n  * [x] Second\r\n", NoteExtras.toggle(body, item))
        assertEquals(body, NoteExtras.toggle(body, item.copy(checked = true)))
    }
}
