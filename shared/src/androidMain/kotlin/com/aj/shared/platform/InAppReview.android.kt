package com.aj.shared.platform

import com.aj.shared.api.appContext
import com.google.android.play.core.review.ReviewManagerFactory

actual fun requestInAppReview() {
    val activity = appContext as? android.app.Activity ?: return
    val manager = ReviewManagerFactory.create(activity)
    val request = manager.requestReviewFlow()
    request.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            manager.launchReviewFlow(activity, task.result)
        }
    }
}
