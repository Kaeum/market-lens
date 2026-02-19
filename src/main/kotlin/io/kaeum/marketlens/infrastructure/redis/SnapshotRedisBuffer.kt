package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant

@Component
@Profile("!test")
class SnapshotRedisBuffer(
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
) : SnapshotCachePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun updateIfNewer(tick: RealtimeTick): Boolean {
        val key = snapshotKey(tick.stockCode)
        val result = redisTemplate.execute(
            UPDATE_IF_NEWER_SCRIPT,
            listOf(key, DIRTY_SET_KEY),
            listOf(
                tick.eventTime.toString(),
                tick.currentPrice.toString(),
                tick.changeRate.toPlainString(),
                tick.accumulatedVolume.toString(),
                tick.tradingValue.toString(),
                Instant.now().toString(),
                tick.stockCode,
            ),
        ).awaitFirstOrNull()

        return result == 1L
    }

    override suspend fun getSnapshot(stockCode: String): StockPriceSnapshot? {
        val entries = redisTemplate.opsForHash<String, String>()
            .entries(snapshotKey(stockCode))
            .collectList()
            .awaitFirst()
            .associate { it.key to it.value }

        if (entries.isEmpty()) return null
        return toSnapshot(stockCode, entries)
    }

    override suspend fun getSnapshots(stockCodes: List<String>): List<StockPriceSnapshot> {
        if (stockCodes.isEmpty()) return emptyList()

        return stockCodes.mapNotNull { code ->
            try {
                getSnapshot(code)
            } catch (e: Exception) {
                log.warn("Failed to read snapshot from Redis for {}: {}", code, e.message)
                null
            }
        }
    }

    override suspend fun drainDirtyCodes(): Set<String> {
        val result = redisTemplate.execute(
            DRAIN_DIRTY_SCRIPT,
            listOf(DIRTY_SET_KEY),
            emptyList<String>(),
        ).awaitFirstOrNull() ?: return emptySet()

        @Suppress("UNCHECKED_CAST")
        val members = result as? List<*> ?: return emptySet()
        return members.mapNotNull { it?.toString() }.toSet()
    }

    override suspend fun warmUp(snapshots: List<StockPriceSnapshot>) {
        snapshots.forEach { snapshot ->
            val key = snapshotKey(snapshot.stockCode)
            val fields = mutableMapOf(
                FIELD_CURRENT_PRICE to snapshot.currentPrice.toString(),
                FIELD_CHANGE_RATE to (snapshot.changeRate?.toPlainString() ?: ""),
                FIELD_VOLUME to snapshot.volume.toString(),
                FIELD_TRADING_VALUE to snapshot.tradingValue.toString(),
                FIELD_UPDATED_AT to snapshot.updatedAt.toString(),
                FIELD_EVENT_TIME to snapshot.updatedAt.toString(),
            )
            snapshot.marketCap?.let { fields[FIELD_MARKET_CAP] = it.toString() }

            redisTemplate.opsForHash<String, String>()
                .putAll(key, fields)
                .awaitFirst()
        }
        log.info("Warmed up {} snapshots into Redis", snapshots.size)
    }

    private fun toSnapshot(stockCode: String, fields: Map<String, String>): StockPriceSnapshot? {
        val currentPrice = fields[FIELD_CURRENT_PRICE]?.toLongOrNull() ?: return null
        return StockPriceSnapshot(
            stockCode = stockCode,
            currentPrice = currentPrice,
            changeRate = fields[FIELD_CHANGE_RATE]?.takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
            volume = fields[FIELD_VOLUME]?.toLongOrNull() ?: 0L,
            marketCap = fields[FIELD_MARKET_CAP]?.toLongOrNull(),
            tradingValue = fields[FIELD_TRADING_VALUE]?.toLongOrNull() ?: 0L,
            updatedAt = fields[FIELD_UPDATED_AT]?.let { Instant.parse(it) } ?: Instant.now(),
        )
    }

    companion object {
        const val SNAPSHOT_KEY_PREFIX = "snapshot:"
        const val DIRTY_SET_KEY = "snapshot:dirty"

        private const val FIELD_CURRENT_PRICE = "currentPrice"
        private const val FIELD_CHANGE_RATE = "changeRate"
        private const val FIELD_VOLUME = "volume"
        private const val FIELD_MARKET_CAP = "marketCap"
        private const val FIELD_TRADING_VALUE = "tradingValue"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_EVENT_TIME = "eventTime"

        fun snapshotKey(stockCode: String) = "$SNAPSHOT_KEY_PREFIX$stockCode"

        private val UPDATE_IF_NEWER_SCRIPT = RedisScript.of<Long>(
            """
            local key = KEYS[1]
            local dirtyKey = KEYS[2]
            local newEventTime = ARGV[1]
            local existingEventTime = redis.call('HGET', key, 'eventTime')
            if existingEventTime and existingEventTime >= newEventTime then
                return 0
            end
            redis.call('HSET', key,
                'currentPrice', ARGV[2],
                'changeRate', ARGV[3],
                'volume', ARGV[4],
                'tradingValue', ARGV[5],
                'updatedAt', ARGV[6],
                'eventTime', ARGV[1])
            redis.call('SADD', dirtyKey, ARGV[7])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        private val DRAIN_DIRTY_SCRIPT = RedisScript.of<List<*>>(
            """
            local members = redis.call('SMEMBERS', KEYS[1])
            if #members > 0 then
                redis.call('DEL', KEYS[1])
            end
            return members
            """.trimIndent(),
            List::class.java,
        )
    }
}
