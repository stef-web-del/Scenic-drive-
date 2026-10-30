package de.scenicroutes.shared

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import de.scenicroutes.shared.db.ScenicDb

fun createDriver(context: Context, name: String? = "scenic.db"): SqlDriver = AndroidSqliteDriver(ScenicDb.Schema, context, name)
