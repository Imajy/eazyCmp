package com.aj.shared.ui

/**
 * Default placeholders for UI widgets. The umbrella [com.aj.shared.EazyCmp] facade
 * mirrors these and can override them at runtime for host apps.
 */
object UiDefaults {
    var imagePlaceholder: Placeholder = Placeholder.LottieUrl(
        "https://lottie.host/a9be1300-ee73-471a-969d-6ebe32a5fb64/NT7azVsdv1.json"
    )
    var apiLoadingPlaceholder: Placeholder = Placeholder.LottieUrl(
        "https://letterhead.ajmonic.com/loading.json"
    )
}
