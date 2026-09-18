package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.domain.TripCloudUpload
import javax.inject.Inject

class UploadTripToCloudUseCase @Inject constructor(
    private val tripCloudUpload: TripCloudUpload,
) {
    operator fun invoke(localTripId: Long) {
        tripCloudUpload.enqueueUpload(localTripId)
    }

    suspend fun uploadNow(localTripId: Long): Result<Unit> = runCatching {
        tripCloudUpload.uploadNow(localTripId)
    }
}
