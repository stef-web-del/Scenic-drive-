package de.scenicroutes.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.scenicroutes.shared.db.ScenicDb
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDbTest {
    @Test fun migrationUndLoeschflag() {
        assertEquals(4L, ScenicDb.Schema.version)
        val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Stand 0.3.x: nur alte Tabellen -> Migration legt neue an
        d.execute(null, "CREATE TABLE Dummy(x INTEGER)", 0)
        ScenicDb.Schema.migrate(d, 1, 2)
        val q = ScenicDb(d).localQueries
        q.insertProfile("u-1", "geniesser", 1, 1)
        q.insertDriven("d-1", 100001, 0, 2, 2)
        q.markDrivenDeleted(3, 100001)
        assertEquals(0, q.drivenActive().executeAsList().size)
        assertEquals("geniesser", q.profile().executeAsOne().profile)
    }
}
