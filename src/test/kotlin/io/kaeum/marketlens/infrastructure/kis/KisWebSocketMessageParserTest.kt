package io.kaeum.marketlens.infrastructure.kis

import io.kaeum.marketlens.domain.price.TickType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class KisWebSocketMessageParserTest {

    @Test
    fun `parse trade message with pipe and caret separators`() {
        // H0STCNT0 체결가: encrypted|trId|dataCount|data
        // 필드: [0]종목코드 [1]체결시각 [2]체결가 [3].. [4].. [5]전일대비 [6].. [7].. [8]등락률 [9]체결수량 [10].. [11].. [12]누적거래량 [13].. [14]누적거래대금 [15]..
        val tradeData = buildTradeFields(
            stockCode = "005930",
            tradeTime = "100530",
            currentPrice = "72000",
            changeRate = "1.50",
            volume = "500",
            accumulatedVolume = "10000000",
            tradingValue = "50000000000",
        )
        val rawMessage = "0|H0STCNT0|001|$tradeData"

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.Trade)
        val tick = (result as KisWebSocketMessage.Trade).tick
        assertEquals("005930", tick.stockCode)
        assertEquals(72000L, tick.currentPrice)
        assertEquals("1.50".toBigDecimal(), tick.changeRate)
        assertEquals(500L, tick.volume)
        assertEquals(10000000L, tick.accumulatedVolume)
        assertEquals(50000000000L, tick.tradingValue)
        assertEquals(LocalTime.of(10, 5, 30), tick.tradeTime)
        assertEquals(TickType.TRADE, tick.tickType)
    }

    @Test
    fun `parse heartbeat PINGPONG message`() {
        val rawMessage = """{"header":{"tr_id":"PINGPONG","datetime":"20240101120000"}}"""

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.Heartbeat)
    }

    @Test
    fun `parse subscription success response`() {
        val rawMessage = """
            {
              "header": {
                "tr_id": "H0STCNT0",
                "tr_key": "005930",
                "encrypt": "N"
              },
              "body": {
                "rt_cd": "0",
                "msg_cd": "OPSP0000",
                "msg1": "SUBSCRIBE SUCCESS"
              }
            }
        """.trimIndent()

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.SubscriptionResponse)
        val response = result as KisWebSocketMessage.SubscriptionResponse
        assertEquals("H0STCNT0", response.trId)
        assertEquals("005930", response.stockCode)
        assertTrue(response.success)
    }

    @Test
    fun `parse subscription failure response`() {
        val rawMessage = """
            {
              "header": {
                "tr_id": "H0STCNT0",
                "tr_key": "999999",
                "encrypt": "N"
              },
              "body": {
                "rt_cd": "1",
                "msg_cd": "OPSP0002",
                "msg1": "SUBSCRIBE FAIL"
              }
            }
        """.trimIndent()

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.SubscriptionResponse)
        val response = result as KisWebSocketMessage.SubscriptionResponse
        assertFalse(response.success)
    }

    @Test
    fun `parse unknown message for unsupported trId`() {
        val rawMessage = "0|H0STASP0|001|somedata"

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.Unknown)
    }

    @Test
    fun `parse empty message returns Unknown`() {
        val result = KisWebSocketMessageParser.parse("")
        assertTrue(result is KisWebSocketMessage.Unknown)
    }

    @Test
    fun `parse trade with insufficient fields returns Unknown`() {
        // Only 10 fields — need at least 16 (index 15+)
        val fields = (0..9).joinToString("^") { "0" }
        val rawMessage = "0|H0STCNT0|001|$fields"

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.Unknown)
    }

    @Test
    fun `parse trade with invalid number returns Unknown`() {
        val tradeData = buildTradeFields(
            stockCode = "005930",
            tradeTime = "100530",
            currentPrice = "NOT_A_NUMBER",
            changeRate = "1.50",
            volume = "500",
            accumulatedVolume = "10000000",
            tradingValue = "50000000000",
        )
        val rawMessage = "0|H0STCNT0|001|$tradeData"

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.Unknown)
    }

    @Test
    fun `parse OPSP0001 as successful subscription`() {
        val rawMessage = """
            {
              "header": {"tr_id": "H0STCNT0", "tr_key": "005930"},
              "body": {"rt_cd": "0", "msg_cd": "OPSP0001", "msg1": "ALREADY SUBSCRIBED"}
            }
        """.trimIndent()

        val result = KisWebSocketMessageParser.parse(rawMessage)

        assertTrue(result is KisWebSocketMessage.SubscriptionResponse)
        assertTrue((result as KisWebSocketMessage.SubscriptionResponse).success)
    }

    /**
     * Build a pipe-separated trade data string with 16 fields.
     * Fields: [0]stockCode [1]tradeTime [2]currentPrice [3]dummy [4]dummy
     *         [5]changeRate [6]dummy [7]dummy [8]dummy [9]volume
     *         [10]dummy [11]dummy [12]accumulatedVolume [13]dummy [14]tradingValue [15]dummy
     */
    private fun buildTradeFields(
        stockCode: String,
        tradeTime: String,
        currentPrice: String,
        changeRate: String,
        volume: String,
        accumulatedVolume: String,
        tradingValue: String,
    ): String {
        val fields = MutableList(16) { "0" }
        fields[0] = stockCode
        fields[1] = tradeTime
        fields[2] = currentPrice
        fields[5] = changeRate
        fields[9] = volume
        fields[12] = accumulatedVolume
        fields[14] = tradingValue
        return fields.joinToString("^")
    }
}
