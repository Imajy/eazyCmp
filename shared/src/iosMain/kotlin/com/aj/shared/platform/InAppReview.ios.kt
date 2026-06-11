package com.aj.shared.platform

import platform.StoreKit.SKStoreReviewController

actual fun requestInAppReview() {
    SKStoreReviewController.requestReview()
}
