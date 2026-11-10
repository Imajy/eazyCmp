package com.aj.shared.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CartItem(
    val id: String,
    val quantity: Int,
    val unitPrice: Double = 0.0,
    val title: String = "",
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class CartSnapshot(
    val items: List<CartItem> = emptyList(),
    val totalCount: Int = 0,
    val totalAmount: Double = 0.0
)

/**
 * Optimistic In-Memory & Persistent Cart State Manager.
 * Handles instant UI state updates and automatic background synchronization.
 */
class CartStateStore(
    private val preferencesKey: String = "eazycmp_active_cart",
    private val preferences: PreferencesStore? = null
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _cartState = MutableStateFlow(loadPersistedCart())
    val cartState: StateFlow<CartSnapshot> = _cartState.asStateFlow()

    fun getItemQuantity(itemId: String): Int {
        return _cartState.value.items.firstOrNull { it.id == itemId }?.quantity ?: 0
    }

    fun addItem(item: CartItem) {
        val current = _cartState.value.items.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == item.id }

        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = existing.copy(
                quantity = existing.quantity + item.quantity,
                unitPrice = if (item.unitPrice > 0) item.unitPrice else existing.unitPrice
            )
        } else {
            current.add(item)
        }
        updateState(current)
    }

    fun updateQuantity(itemId: String, quantity: Int) {
        val current = _cartState.value.items.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == itemId }

        if (existingIndex >= 0) {
            if (quantity <= 0) {
                current.removeAt(existingIndex)
            } else {
                current[existingIndex] = current[existingIndex].copy(quantity = quantity)
            }
            updateState(current)
        }
    }

    fun removeItem(itemId: String) {
        val current = _cartState.value.items.filterNot { it.id == itemId }
        updateState(current)
    }

    fun clear() {
        updateState(emptyList())
    }

    private fun updateState(items: List<CartItem>) {
        val totalCount = items.sumOf { it.quantity }
        val totalAmount = items.sumOf { it.quantity * it.unitPrice }
        val snapshot = CartSnapshot(items = items, totalCount = totalCount, totalAmount = totalAmount)
        _cartState.value = snapshot
        persistCart(snapshot)
    }

    private fun loadPersistedCart(): CartSnapshot {
        val raw = preferences?.getString(preferencesKey, "")
        if (!raw.isNullOrBlank()) {
            return runCatching { json.decodeFromString<CartSnapshot>(raw) }.getOrDefault(CartSnapshot())
        }
        return CartSnapshot()
    }

    private fun persistCart(snapshot: CartSnapshot) {
        preferences?.let {
            val raw = json.encodeToString(snapshot)
            it.putString(preferencesKey, raw)
        }
    }
}
