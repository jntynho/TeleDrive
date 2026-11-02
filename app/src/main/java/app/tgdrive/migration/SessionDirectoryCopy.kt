package app.tgdrive.migration

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A copy, never a move: an upgrade cannot consume the previous session. */
internal suspend fun copySessionDirectory(source:File,target:File) {
    require(source.isDirectory){"Previous session directory is unavailable."}
    require(!target.exists()){ "A new session already exists; refusing to replace it." }
    val size=source.walkTopDown().filter{it.isFile}.sumOf{it.length()}
    target.parentFile!!.mkdirs()
    require(target.parentFile!!.usableSpace>size+64L*1024*1024){"Not enough space to copy the previous session safely."}
    val scratch=File(target.parentFile,target.name+".upgrade-copy")
    // Only this upgrade's disposable partial copy is cleared after an interrupted attempt.
    if(scratch.exists())scratch.deleteRecursively()
    try {
        source.walkTopDown().forEach{entry->
            currentCoroutineContext().ensureActive()
            val relative=entry.relativeTo(source)
            val destination=File(scratch,relative.path)
            if(entry.isDirectory)destination.mkdirs()else{
                destination.parentFile!!.mkdirs();entry.inputStream().use{input->destination.outputStream().use{out->
                    val buffer=ByteArray(128*1024)
                    while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}
                }}
                require(destination.length()==entry.length()){ "Previous session changed while it was being copied." }
            }
        }
        check(scratch.renameTo(target)){"Could not finalize the copied session."}
    }finally{scratch.deleteRecursively()}
}
