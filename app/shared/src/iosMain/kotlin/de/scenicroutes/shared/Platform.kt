package de.scenicroutes.shared

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import de.scenicroutes.shared.db.ScenicDb

fun createDriver(): SqlDriver = NativeSqliteDriver(ScenicDb.Schema, "scenic.db")
