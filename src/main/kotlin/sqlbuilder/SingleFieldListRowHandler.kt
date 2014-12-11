package sqlbuilder

import java.util.ArrayList

/**
 * Useful for mapping a single column as a List.
 * @param <T> type of single result
 *
 * @author Laurent Van der Linden
 */
public class SingleFieldListRowHandler<T>(private val requiredType: Class<T>) : ReturningRowHandler<List<T>> {
    private var list = ArrayList<T>()

    override var result: List<T> = list

    override fun handle(set: ResultSet, row: Int): Boolean {
        list.add(set.getObject(requiredType, 1) as T)
        return true
    }
}