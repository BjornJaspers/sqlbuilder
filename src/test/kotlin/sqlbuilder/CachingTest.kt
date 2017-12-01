package sqlbuilder

import org.junit.Before
import org.junit.Test
import sqlbuilder.Setup.sqlBuilder
import sqlbuilder.exceptions.CacheException
import sqlbuilder.kotlin.pojo.User
import sqlbuilder.kotlin.select
import sqlbuilder.kotlin.select.selectBeans
import sqlbuilder.meta.Table
import java.io.Serializable
import kotlin.test.assertEquals

class CachingTest {
    @Before
    fun setup() {
        Setup.createTables(sqlBuilder)

        assertEquals(1L, sqlBuilder.insert().getKeys(true).insertBean(SerializableUser(
                username = "test a",
                id = null
        )), "generated key incorrect")
    }

    @Test
    fun fileBasedCache() {
        val tempFile = createTempFile("users", "cache")

        val oneUserCached = sqlBuilder.select {
            cache(tempFile)
            selectBeans<SerializableUser>()
        }
        assertEquals(1, oneUserCached.size)

        assertEquals(2L, sqlBuilder.insert().getKeys(true).insertBean(SerializableUser(
                username = "test b",
                id = null
        )), "generated key incorrect")

        val allUsersCached = sqlBuilder.select {
            cache(tempFile)
            selectBeans<SerializableUser>()
        }
        assertEquals(1, allUsersCached.size)

        tempFile.delete()

        val allUsersCachedAfterReset = sqlBuilder.select {
            cache(tempFile)
            selectBeans<SerializableUser>()
        }
        assertEquals(2, allUsersCachedAfterReset.size)
    }

    @Test(expected = CacheException::class)
    fun cacheNonSerializableResults() {
        sqlBuilder.select {
            cache(createTempFile("users", "cache"))
            selectBeans<User>()
        }
    }

    @Table("users")
    data class SerializableUser(var id: Int?, var username: String?) : Serializable
}