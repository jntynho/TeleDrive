package app.tgdrive.rebuild

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.infinity.drive.presentation.input.editInlineText
import com.infinity.drive.presentation.input.InlineDrafts
import org.junit.Assert.*
import org.junit.Test

class InlineInputTest {
    @Test fun sensitiveDraftsSurviveViewRecreationInMemoryAndAreErasedAfterSignIn(){
        val drafts=InlineDrafts()
        val first=drafts.value("password");first.value="sensitive draft"
        assertSame(first,drafts.value("password"))
        drafts.clear()
        assertEquals("",first.value)
        assertEquals("",drafts.value("password").value)
    }
    @Test fun insertionReplacesTheSelectionRatherThanAppendingToTheWrongField(){
        val next=editInlineText(TextFieldValue("123456",TextRange(2,5)),"90")
        assertEquals("12906",next.text);assertEquals(TextRange(4),next.selection)
    }
    @Test fun backspaceRemovesOneWholeUnicodeCodePoint(){
        val emoji="a\uD83D\uDE80"
        val next=editInlineText(TextFieldValue(emoji,TextRange(emoji.length)),backspace=true)
        assertEquals("a",next.text);assertEquals(TextRange(1),next.selection)
    }
    @Test fun deletionRespectsSelectionsAndTheBeginningOfTheField(){
        assertEquals("adef",editInlineText(TextFieldValue("abcdef",TextRange(1,3)),backspace=true).text)
        assertEquals("abc",editInlineText(TextFieldValue("abc",TextRange.Zero),backspace=true).text)
    }
    @Test fun arbitraryUnicodePasswordsCanBePastedWithoutNormalization(){
        val password="e\u0301 كلمة \uD83D\uDE80"
        assertEquals(password,editInlineText(TextFieldValue("",TextRange.Zero),password).text)
    }
}
