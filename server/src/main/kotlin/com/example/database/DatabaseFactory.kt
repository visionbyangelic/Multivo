package com.example.database

import com.example.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

object DatabaseFactory {

    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    @Volatile
    private var pool: HikariDataSource? = null

    val dataSource: HikariDataSource
        get() = pool ?: error("Database is not connected. Call DatabaseFactory.connect() first.")

    @Synchronized
    fun connect(config: AppConfig) {
        close()

        val hikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 1
            connectionTimeout = 30_000
            idleTimeout = 600_000
            maxLifetime = 1_800_000
            poolName = "MultivoPool"
        }

        val newPool = HikariDataSource(hikari)
        try {
            Flyway.configure()
                .dataSource(newPool)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        } catch (e: Exception) {
            newPool.close()
            throw IllegalStateException("Unable to initialize the Multivo database: ${e.message}", e)
        }

        pool = newPool
        log.info("Multivo database connected and migrations applied")
    }

    fun isHealthy(): Boolean = runCatching {
        dataSource.connection.use { it.isValid(2) }
    }.getOrDefault(false)

    @Synchronized
    fun close() {
        pool?.close()
        pool = null
    }
}
