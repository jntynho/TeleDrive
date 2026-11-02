package app.tgdrive.ui

import androidx.compose.runtime.*
import app.tgdrive.library.*

internal data class LibraryUsage(val count:Int,val bytes:Long,val groups:List<Long>)
internal fun libraryUsage(state:LibraryState):LibraryUsage {
    val files=state.items.filter{it.deletedAt==0L&&it.versionId.isNotEmpty()&&(state.itemVaults[it.id].orEmpty().isEmpty()||state.itemVaults[it.id] in state.unlockedVaultIds)}
    val groups=LongArray(4)
    files.forEach{val index=when{it.mime.startsWith("image/")->0;it.mime.startsWith("video/")->1;it.mime.startsWith("audio/")->2;else->3};groups[index]+=it.size.coerceAtLeast(0)}
    return LibraryUsage(files.size,groups.sum(),groups.toList())
}

@Composable internal fun LibraryUsageCard(state:LibraryState,onAdd:()->Unit) {
    val usage=remember(state.items,state.itemVaults,state.unlockedVaultIds){libraryUsage(state)}
    LibraryDashboard(LibraryOverview(usage.count,usage.bytes,usage.groups,state.offlineBytes,state.lockedVaults),onAdd)
}
