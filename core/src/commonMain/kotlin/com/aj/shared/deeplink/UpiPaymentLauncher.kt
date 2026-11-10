package com.aj.shared.deeplink

/**
 * Standard UPI payment apps supported for targeted launching.
 */
enum class UpiApp(val packageName: String, val appName: String) {
    GENERIC("", "All UPI Apps"),
    GOOGLE_PAY("com.google.android.apps.nbu.paisa.user", "Google Pay"),
    PHONE_PE("com.phonepe.app", "PhonePe"),
    PAYTM("net.one97.paytm", "Paytm"),
    CRED("com.dreamplug.androidapp", "CRED"),
    BHIM("in.org.npci.upiapp", "BHIM"),
    AMAZON_PAY("in.amazon.mShop.android.shopping", "Amazon Pay")
}

/**
 * NPCI-compliant UPI Payment parameters model.
 */
data class UpiPaymentRequest(
    val payeeVpa: String,          // e.g. "merchant@okaxis"
    val payeeName: String,         // e.g. "OnGoCart Store"
    val amount: Double,            // e.g. 499.00
    val transactionRefId: String = "", // Unique Order ID / Txn ID
    val transactionNote: String = "Payment",
    val currency: String = "INR",
    val merchantCode: String? = null,
    val callbackUrl: String? = null
)

/**
 * Builds standard NPCI compliant upi://pay URI string.
 */
fun buildUpiUri(request: UpiPaymentRequest): String {
    val formattedAmount = ((request.amount * 100).toLong() / 100.0).toString()
    val params = mutableListOf<String>()

    params.add("pa=${encodeUriComponent(request.payeeVpa)}")
    params.add("pn=${encodeUriComponent(request.payeeName)}")
    params.add("am=$formattedAmount")
    params.add("cu=${request.currency}")
    params.add("tn=${encodeUriComponent(request.transactionNote)}")

    if (request.transactionRefId.isNotBlank()) {
        params.add("tr=${encodeUriComponent(request.transactionRefId)}")
    }
    if (!request.merchantCode.isNullOrBlank()) {
        params.add("mc=${encodeUriComponent(request.merchantCode)}")
    }
    if (!request.callbackUrl.isNullOrBlank()) {
        params.add("url=${encodeUriComponent(request.callbackUrl)}")
    }

    return "upi://pay?" + params.joinToString("&")
}

private fun encodeUriComponent(s: String): String {
    return s.replace(" ", "%20")
        .replace("&", "%26")
        .replace("=", "%3D")
        .replace("?", "%3F")
        .replace("#", "%23")
        .replace("/", "%2F")
        .replace(":", "%3A")
        .replace("@", "%40")
}

/**
 * Extension on DeepLinkHandler for instant UPI checkout launching.
 */
fun DeepLinkHandler.createUpiLink(request: UpiPaymentRequest): String {
    return buildUpiUri(request)
}
