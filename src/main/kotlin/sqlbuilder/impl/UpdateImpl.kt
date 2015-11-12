package sqlbuilder.impl

import sqlbuilder.Backend
import sqlbuilder.PersistenceException
import sqlbuilder.exclude
import sqlbuilder.include
import sqlbuilder.usea
import sqlbuilder.SqlConverter
import sqlbuilder.IncorrectResultSizeException

import java.util.Arrays
import java.sql.SQLException
import java.sql.Statement
import java.sql.PreparedStatement
import org.slf4j.LoggerFactory
import sqlbuilder.Update

/**
 * Update statement: pass in a bean or run a custom statement
 *
 * @author Laurent Van der Linden
 */
class UpdateImpl(private val backend: Backend): Update {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var entity: String? = null
    private var checkNullability = false
    private var getkeys = false
    private var includeFields: Array<out String>? = null
    private var excludeFields: Array<out String>? = null

    var generatedKey: Long = 0

    public override fun entity(entity: String): Update {
        this.entity = entity
        return this
    }

    public override fun checkNullability(check: Boolean): Update {
        checkNullability = check
        return this
    }

    public override fun updateBean(bean: Any) {
        val keys = backend.configuration.metaResolver.getKeys(bean.javaClass)
        updateBean(bean, keys)
    }

    public override fun updateBean(bean: Any, keys: Array<out String>) {
        val metaResolver = backend.configuration.metaResolver
        if (entity == null) entity = metaResolver.getTableName(bean.javaClass)
        entity = backend.configuration.escapeEntity(entity)

        if (keys.size == 0) {
            throw PersistenceException("cannot update bean without a list of keys")
        }

        val sqlCon = backend.getSqlConnection()
        try {
            val getters = metaResolver.getProperties(bean.javaClass, false)
                    .exclude(excludeFields)
                    .include(includeFields)

            val sql = StringBuilder("update ").append(entity).append(" set ")


            val valueProperties = getters.filter { !keys.contains(it.name) }
            val keyProperties = getters.filter { keys.contains(it.name) }

            valueProperties.map { "${it.name} = ?" }.joinTo(sql, ",")

            sql.append(" where ")

            keyProperties.map { "${it.name} = ?" }.joinTo(sql, " and ")

            val sqlString = sql.toString()
            logger.info(sqlString)

            val sqlConverter = SqlConverter(backend.configuration)

            try {
                if (checkNullability) {
                    backend.checkNullability(entity!!, bean, sqlCon, getters)
                }
                var updates = 0
                sqlCon.prepareStatement(sqlString)!!.usea { ps: PreparedStatement ->
                    for ((index, getter) in valueProperties.withIndex()) {
                        try {
                            sqlConverter.setParameter(ps, getter.get(bean), index + 1, getter.classType, null)
                        } catch (e: IllegalArgumentException) {
                            throw PersistenceException("unable to get " + getter.name, e)
                        }
                    }

                    for ((index, getter) in keyProperties.withIndex()) {
                        sqlConverter.setParameter(ps, getter.get(bean), valueProperties.size + index + 1, getter.classType, null)
                    }

                    updates = ps.executeUpdate()
                }

                if (updates != 1) {
                    throw PersistenceException("updateBean resulted in " + updates + " updated rows instead of 1 using <" + sql + "> with bean " + bean)
                }
            } catch (sqlx: SQLException) {
                throw PersistenceException("update <" + sql + "> failed", sqlx)
            }

        } finally {
            backend.closeConnection(sqlCon)
        }
    }

    /**
     * Custom update that allows null parameters due to the types argument.
     * @param sql statement
     * @return updated rows
     */
    public override fun updateStatement(sql: String): Int {
        return updateStatement(sql, null, null)
    }

    /**
     * Custom update that allows null parameters due to the types argument.
     * @param sql statement
     * @param parameters parameters objects
     * @return updated rows
     */
    public override fun updateStatement(sql: String, vararg parameters: Any): Int {
        return updateStatement(sql, parameters, null)
    }

    /**
     * Custom update that allows null parameters due to the types argument.
     * @param sql statement
     * @param parameters parameters objects
     * @param types array of java.sql.Types
     * @return updated rows
     */
    public override fun updateStatement(sql: String, parameters: Array<out Any>?, types: IntArray?): Int {
        logger.info(sql)

        val sqlConverter = SqlConverter(backend.configuration)

        val connection = backend.getSqlConnection()
        try {
            try {
                val autoGeneratedKeys = if (getkeys) Statement.RETURN_GENERATED_KEYS else Statement.NO_GENERATED_KEYS
                return connection.prepareStatement(sql, autoGeneratedKeys)!!.usea { ps ->
                    if (parameters != null) {
                        for ((index, parameter) in parameters.withIndex()) {
                            val parameterType = if (types == null) null else types[index]
                            sqlConverter.setParameter(ps, parameters[index], index + 1, null, parameterType)
                        }
                    }

                    val rows = ps.executeUpdate()

                    generatedKey = 0
                    if (getkeys) {
                        try {
                            val keys = ps.getGeneratedKeys()
                            if (keys != null) {
                                if (keys.next()) {
                                    generatedKey = keys.getLong(1)
                                }
                                keys.close()
                            }
                        } catch (ignore: AbstractMethodError) {
                        } catch (sqlx: SQLException) {
                            throw PersistenceException("unable to retreive generated keys", sqlx)
                        }

                    }
                    rows
                }
            } catch (px: PersistenceException) {
                throw PersistenceException("update <" + sql + "> failed with parameters " + Arrays.toString(parameters), px)
            }


        } catch (e: SQLException) {
            throw PersistenceException(e.message, e)
        } finally {
            backend.closeConnection(connection)
        }
    }

    /**
     * Special updatestatement that throws PersistenceException if updated rows do not match.
     * @param sql
     * @param expectedUpdateCount
     * @param parameters
     * @return updated rows if matching the expected
     */
    public override fun updateStatementExpecting(sql: String, expectedUpdateCount: Int, vararg parameters: Any): Int {
        val updates = updateStatement(sql, parameters, null)
        if (updates != expectedUpdateCount) {
            val errorBuilder = StringBuilder("expected $expectedUpdateCount rows to be updated, but $updates rows were ($sql [")
            parameters.joinTo(errorBuilder, ",")
            errorBuilder.append("])")
            throw IncorrectResultSizeException(errorBuilder.toString())
        }
        return updates
    }

    /**
     * store any generated id after executing the update statement (which should be an insert in this case)
     * <br/>use <code>getGeneratedKey</code> to get the value afterwards
     * @param cond
     * @return
     */
    public override fun getKeys(cond: Boolean): Update {
        this.getkeys = cond
        return this
    }

    override fun excludeFields(vararg excludes: String): Update {
        this.excludeFields = excludes
        return this
    }

    override fun includeFields(vararg includes: String): Update {
        this.includeFields = includes
        return this
    }
}