package validation.aireview

// Standalone fixture outside the application's source set.
class DummyPurchaseService {
    fun remainingStockAfterPurchase(stock: Int, quantity: Int): Int {
        require(quantity > 0) { "Quantity must be positive" }
        require(stock >= 0) { "Stock must not be negative" }
        return stock - quantity
    }
}
