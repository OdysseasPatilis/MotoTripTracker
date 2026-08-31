package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.backend.TripCloudUploader
import javax.inject.Inject

class UploadTripToCloudUseCase @Inject constructor(
    private val tripCloudUploader: TripCloudUploader,
) {
    operator fun invoke(localTripId: Long) {
        tripCloudUploader.enqueueUpload(localTripId)
    }
}
