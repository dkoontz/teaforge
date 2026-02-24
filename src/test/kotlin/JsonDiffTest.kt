import teaforge.debugger.DiffOperation
import teaforge.debugger.JsonDiff
import teaforge.debugger.JsonValue
import teaforge.debugger.JsonValue.Companion.arr
import teaforge.debugger.JsonValue.Companion.bool
import teaforge.debugger.JsonValue.Companion.num
import teaforge.debugger.JsonValue.Companion.obj
import teaforge.debugger.JsonValue.Companion.str
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonDiffTest {
    // ----- Helper shortcuts -----

    private fun emptyObj(): JsonValue.JsonObject = JsonValue.JsonObject(emptyMap())

    // ----- Identical values produce empty diff -----

    @Test
    fun `identical empty objects produce empty diff`() {
        val ops = JsonDiff.diff(emptyObj(), emptyObj())
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `identical non-empty objects produce empty diff`() {
        val model = obj("count" to num(5), "name" to str("Alice"))
        val ops = JsonDiff.diff(model, model)
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `identical arrays produce empty diff`() {
        val a = arr(num(1), num(2), num(3))
        val ops = JsonDiff.diff(a, a)
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `identical primitive strings produce empty diff`() {
        val ops = JsonDiff.diff(str("hello"), str("hello"))
        assertTrue(ops.isEmpty())
    }

    // ----- Add operations -----

    @Test
    fun `field present in new but not old produces add operation`() {
        val oldModel = obj("a" to num(1))
        val newModel = obj("a" to num(1), "b" to str("new"))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0]
        assertTrue(op is DiffOperation.Add)
        assertEquals("/b", op.path)
        assertEquals(str("new"), op.value)
    }

    @Test
    fun `all fields added when diffing against empty object (init scenario)`() {
        val newModel = obj("x" to num(10), "y" to num(20))
        val ops = JsonDiff.diff(emptyObj(), newModel)

        assertEquals(2, ops.size)
        assertTrue(ops.all { it is DiffOperation.Add })
        val paths = ops.map { (it as DiffOperation.Add).path }.toSet()
        assertEquals(setOf("/x", "/y"), paths)
    }

    // ----- Remove operations -----

    @Test
    fun `field present in old but not new produces remove operation`() {
        val oldModel = obj("a" to num(1), "b" to str("old"))
        val newModel = obj("a" to num(1))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0]
        assertTrue(op is DiffOperation.Remove)
        assertEquals("/b", op.path)
    }

    // ----- Replace operations -----

    @Test
    fun `field with different primitive value produces replace operation`() {
        val oldModel = obj("count" to num(3))
        val newModel = obj("count" to num(7))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0]
        assertTrue(op is DiffOperation.Replace)
        assertEquals("/count", op.path)
        assertEquals(num(7), op.value)
    }

    @Test
    fun `boolean value change produces replace operation`() {
        val ops =
            JsonDiff.diff(
                obj("flag" to bool(false)),
                obj("flag" to bool(true)),
            )
        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Replace
        assertEquals("/flag", op.path)
        assertEquals(bool(true), op.value)
    }

    // ----- Nested object diffs -----

    @Test
    fun `nested object field change produces correct path`() {
        val oldModel = obj("user" to obj("name" to str("Alice"), "age" to num(30)))
        val newModel = obj("user" to obj("name" to str("Bob"), "age" to num(30)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Replace
        assertEquals("/user/name", op.path)
        assertEquals(str("Bob"), op.value)
    }

    @Test
    fun `deeply nested field change produces correct path`() {
        val oldModel = obj("outer" to obj("inner" to obj("value" to num(1))))
        val newModel = obj("outer" to obj("inner" to obj("value" to num(2))))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Replace
        assertEquals("/outer/inner/value", op.path)
        assertEquals(num(2), op.value)
    }

    @Test
    fun `nested object field added produces correct path`() {
        val oldModel = obj("user" to obj("name" to str("Alice")))
        val newModel = obj("user" to obj("name" to str("Alice"), "age" to num(25)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Add
        assertEquals("/user/age", op.path)
        assertEquals(num(25), op.value)
    }

    // ----- Array diffs -----

    @Test
    fun `array element modification produces replace at correct index`() {
        val oldModel = obj("items" to arr(num(1), num(2), num(3)))
        val newModel = obj("items" to arr(num(1), num(99), num(3)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Replace
        assertEquals("/items/1", op.path)
        assertEquals(num(99), op.value)
    }

    @Test
    fun `longer new array produces add operations for extra elements`() {
        val oldModel = obj("items" to arr(num(1), num(2)))
        val newModel = obj("items" to arr(num(1), num(2), num(3), num(4)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(2, ops.size)
        assertTrue(ops.all { it is DiffOperation.Add })
        val paths = ops.map { (it as DiffOperation.Add).path }.toSet()
        assertEquals(setOf("/items/2", "/items/3"), paths)
    }

    @Test
    fun `shorter new array produces remove operations for missing elements`() {
        val oldModel = obj("items" to arr(num(1), num(2), num(3), num(4)))
        val newModel = obj("items" to arr(num(1), num(2)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(2, ops.size)
        assertTrue(ops.all { it is DiffOperation.Remove })
        val paths = ops.map { (it as DiffOperation.Remove).path }.toSet()
        assertEquals(setOf("/items/2", "/items/3"), paths)
    }

    @Test
    fun `array with object elements recurses correctly`() {
        val oldModel =
            obj(
                "users" to
                    arr(
                        obj("id" to num(0), "name" to str("Alice")),
                        obj("id" to num(1), "name" to str("Bob")),
                    ),
            )
        val newModel =
            obj(
                "users" to
                    arr(
                        obj("id" to num(0), "name" to str("Alice")),
                        obj("id" to num(1), "name" to str("Charlie")),
                    ),
            )

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Replace
        assertEquals("/users/1/name", op.path)
        assertEquals(str("Charlie"), op.value)
    }

    // ----- Mixed operations -----

    @Test
    fun `mixed add replace remove operations`() {
        val oldModel = obj("a" to num(1), "b" to num(2), "c" to num(3))
        val newModel = obj("a" to num(10), "b" to num(2), "d" to num(4))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(3, ops.size)

        val addOp = ops.filterIsInstance<DiffOperation.Add>()
        assertEquals(1, addOp.size)
        assertEquals("/d", addOp[0].path)
        assertEquals(num(4), addOp[0].value)

        val replaceOp = ops.filterIsInstance<DiffOperation.Replace>()
        assertEquals(1, replaceOp.size)
        assertEquals("/a", replaceOp[0].path)
        assertEquals(num(10), replaceOp[0].value)

        val removeOp = ops.filterIsInstance<DiffOperation.Remove>()
        assertEquals(1, removeOp.size)
        assertEquals("/c", removeOp[0].path)
    }

    // ----- JSON Pointer special character escaping -----

    @Test
    fun `tilde in key is escaped as ~0`() {
        val oldModel = emptyObj()
        val newModel = JsonValue.JsonObject(mapOf("a~b" to num(1)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Add
        assertEquals("/a~0b", op.path)
    }

    @Test
    fun `slash in key is escaped as ~1`() {
        val oldModel = emptyObj()
        val newModel = JsonValue.JsonObject(mapOf("a/b" to num(1)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Add
        assertEquals("/a~1b", op.path)
    }

    @Test
    fun `tilde and slash are both escaped correctly`() {
        val oldModel = emptyObj()
        val newModel = JsonValue.JsonObject(mapOf("a~/b" to num(1)))

        val ops = JsonDiff.diff(oldModel, newModel)

        assertEquals(1, ops.size)
        val op = ops[0] as DiffOperation.Add
        assertEquals("/a~0~1b", op.path)
    }

    // ----- No change -----

    @Test
    fun `model that does not change produces empty diff`() {
        val model = obj("count" to num(5), "active" to bool(true), "name" to str("test"))
        val ops = JsonDiff.diff(model, model)
        assertTrue(ops.isEmpty())
    }
}
