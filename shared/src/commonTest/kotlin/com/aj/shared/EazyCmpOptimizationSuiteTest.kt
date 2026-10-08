package com.aj.shared

import com.aj.shared.deeplink.UpiPaymentRequest
import com.aj.shared.deeplink.buildUpiUri
import com.aj.shared.location.GpsLocationSmoother
import com.aj.shared.location.LatLng
import com.aj.shared.location.bearingTo
import com.aj.shared.location.distanceTo
import com.aj.shared.location.distanceToKm
import com.aj.shared.location.formatDistance
import com.aj.shared.location.isWithinRadius
import com.aj.shared.security.EazyCrypto
import com.aj.shared.storage.CartItem
import com.aj.shared.storage.CartStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EazyCmpOptimizationSuiteTest {

    @Test
    fun testGeoUtilsHaversineAndBearing() {
        // Jaipur to Delhi coordinates (~235-240 km)
        val jaipur = LatLng(26.9124, 75.7873)
        val delhi = LatLng(28.7041, 77.1025)

        val distanceMeters = jaipur.distanceTo(delhi)
        val distanceKm = jaipur.distanceToKm(delhi)

        assertTrue(distanceKm in 230.0..250.0, "Expected ~235-240km between Jaipur and Delhi, got $distanceKm")
        assertEquals(jaipur.formatDistance(delhi), "${(distanceMeters / 100.0).toInt() / 10.0} km")

        // Radius check
        assertTrue(jaipur.isWithinRadius(LatLng(26.9150, 75.7890), 1000.0))
        assertFalse(jaipur.isWithinRadius(delhi, 50000.0))

        // Bearing check
        val bearing = jaipur.bearingTo(delhi)
        assertTrue(bearing in 0.0..90.0, "Jaipur to Delhi should head North-East")

        // Kalman GPS Smoother
        val smoother = GpsLocationSmoother()
        val smoothed = smoother.process(jaipur)
        assertEquals(jaipur.latitude, smoothed.latitude)
        assertEquals(jaipur.longitude, smoothed.longitude)

        val reading2 = smoother.process(LatLng(26.9128, 75.7876))
        assertNotNull(reading2)
    }

    @Test
    fun testEazyCryptoSha256AndTokens() {
        val helloSha256 = EazyCrypto.sha256("hello world")
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", helloSha256)

        val hex = "0123456789abcdef"
        val bytes = EazyCrypto.hexToBytes(hex)
        val converted = EazyCrypto.toHexString(bytes)
        assertEquals(hex, converted)

        val token = EazyCrypto.randomToken(16)
        assertEquals(16, token.length)

        val uuid = EazyCrypto.randomUuid()
        assertEquals(36, uuid.length)
        assertTrue(uuid.contains("-"))
    }

    @Test
    fun testUpiPaymentUriGeneration() {
        val req = UpiPaymentRequest(
            payeeVpa = "merchant@okaxis",
            payeeName = "OnGoCart Store",
            amount = 499.00,
            transactionRefId = "ORD123456",
            transactionNote = "Grocery Order"
        )
        val uri = buildUpiUri(req)
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=merchant%40okaxis"))
        assertTrue(uri.contains("pn=OnGoCart%20Store"))
        assertTrue(uri.contains("am=499.0"))
        assertTrue(uri.contains("cu=INR"))
        assertTrue(uri.contains("tr=ORD123456"))
    }

    @Test
    fun testCartStateStoreMutations() {
        val cart = CartStateStore()
        cart.addItem(CartItem(id = "item_1", quantity = 2, unitPrice = 50.0, title = "Apple"))
        cart.addItem(CartItem(id = "item_2", quantity = 1, unitPrice = 100.0, title = "Banana"))

        assertEquals(3, cart.cartState.value.totalCount)
        assertEquals(200.0, cart.cartState.value.totalAmount)

        cart.updateQuantity("item_1", 3)
        assertEquals(4, cart.cartState.value.totalCount)
        assertEquals(250.0, cart.cartState.value.totalAmount)

        cart.removeItem("item_2")
        assertEquals(3, cart.cartState.value.totalCount)
        assertEquals(150.0, cart.cartState.value.totalAmount)

        cart.clear()
        assertEquals(0, cart.cartState.value.totalCount)
        assertEquals(0.0, cart.cartState.value.totalAmount)
    }
}
