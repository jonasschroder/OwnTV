package tv.own.owntv.features.catalog

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteStatement
import tv.own.owntv.core.database.OwnTVDatabase

/** Uses the SAME bundled Room driver as Core. No legacy openHelper and no secondary database. */
internal suspend fun <T> OwnTVDatabase.readCatalog(sql: String, arguments: Array<out Any?>,
    kind: NativeReadKind = NativeReadKind.CATALOG, block: (CatalogCursor) -> T): T = useReaderConnection { connection ->
    NativeReadWork.record(kind)
    connection.usePrepared(sql) { statement ->
        arguments.forEachIndexed { index, value -> when (value) {
            null -> statement.bindNull(index + 1)
            is String -> statement.bindText(index + 1, value)
            is Number -> statement.bindLong(index + 1, value.toLong())
            else -> error("Unsupported catalog argument")
        } }
        block(CatalogCursor(statement))
    }
}
internal class CatalogCursor(private val statement: SQLiteStatement) {
    fun moveToNext() = statement.step()
    fun getLong(index: Int) = statement.getLong(index)
    fun getInt(index: Int) = statement.getLong(index).toInt()
    fun getDouble(index: Int) = statement.getDouble(index)
    fun getString(index: Int) = statement.getText(index)
    fun isNull(index: Int) = statement.isNull(index)
}
