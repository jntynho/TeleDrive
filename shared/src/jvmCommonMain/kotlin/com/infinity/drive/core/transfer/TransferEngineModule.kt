package com.infinity.drive.core.transfer

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.bind

val transferEngineModule = module {
    singleOf(::ApkIconUploader)
    singleOf(::PartUploader)
    singleOf(::PartDownloader)
    singleOf(::TransferExecutor) bind TransferRunner::class
    single { TransferQueueDrainer(get(),get(),get<TransferRunner>(),get(),get(),get(),get()) }
}
