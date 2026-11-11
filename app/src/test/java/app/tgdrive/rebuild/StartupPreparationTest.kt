package app.tgdrive.rebuild

import com.infinity.drive.data.repository.StartupFileCache
import com.infinity.drive.domain.model.*
import com.infinity.drive.presentation.components.StartupPreparationState
import org.junit.Assert.*
import org.junit.Test

class StartupPreparationTest {
    private fun file(id:String,chat:Long?=1,category:FileCategory=FileCategory.IMAGE)=DriveFile(id,null,"$id.jpg",1,"image/jpeg",category,null,null,chat,null,null,null,BackupState.NONE,
        false,false,false,false,null,null,null,null,createdAt=1,modifiedAt=1,addedAt=1)
    @Test fun pagesAreOneShotAndMustMatchTheWholeQueryAndChannel(){
        val cache=StartupFileCache();val spec=FileQuerySpec(chatId=1,scopeToChat=true,categories=listOf(FileCategory.IMAGE))
        cache.putPage(spec,listOf(file("photo")))
        assertNull(cache.takePage(spec.copy(chatId=2)))
        assertNull(cache.takePage(spec.copy(nameQuery="other")))
        assertEquals("photo",cache.takePage(spec)!!.single().id)
        assertNull(cache.takePage(spec))
    }
    @Test fun expiredAndClearedSeedsCannotReappearAfterSessionChanges(){
        var time=0L;val cache=StartupFileCache{time};val spec=FileQuerySpec(chatId=1)
        cache.putRecent(1,listOf(file("recent")));cache.putPage(spec,listOf(file("photo")))
        time=61_000_000_000L
        assertNull(cache.takeRecent(1,12));assertNull(cache.takePage(spec))
        cache.putRecent(1,listOf(file("private")));cache.putPage(spec,listOf(file("private")))
        cache.clear()
        assertTrue(cache.assets(1).isEmpty());assertNull(cache.takePage(spec));assertNull(cache.takeRecent(1,12))
    }
    @Test fun assetBudgetIncludesAudioEvenWhenTheRecentAndGalleryPagesAreFull(){
        val cache=StartupFileCache()
        cache.putRecent(1,(0..11).map{file("recent-$it")})
        cache.putPage(FileQuerySpec(chatId=1,categories=listOf(FileCategory.IMAGE)),(0..39).map{file("photo-$it")})
        cache.putPage(FileQuerySpec(chatId=1,categories=listOf(FileCategory.AUDIO)),(0..39).map{file("audio-$it",category=FileCategory.AUDIO)})
        assertTrue(cache.assets(1).size<=24)
        assertTrue(cache.assets(1).any{it.category==FileCategory.AUDIO})
        assertTrue(cache.assets(2).isEmpty())
    }
    @Test fun preparationSurvivesConfigurationReuseButNewModesAndLogoutNeedTheirOwnReadiness(){
        val state=StartupPreparationState()
        assertTrue(state.needs("sign-in"));state.complete("sign-in")
        assertFalse(state.needs("sign-in"));assertTrue(state.needs("library"))
        state.complete("library");state.complete("library")
        assertFalse(state.needs("library"))
        state.reset();assertTrue(state.needs("library"));assertTrue(state.needs("sign-in"))
    }
}
