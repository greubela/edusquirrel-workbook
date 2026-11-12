package it.evadid.evacuation.core.datastructures.maps

import munit.FunSuite
import scala.collection.mutable.{ListBuffer, HashSet}

class CollectionsSpec extends FunSuite {
  test("list buckets preserve duplicates and copies own their buffers") {
    val map = new MultiHashMapList[String, Int]
    map.addElement("a" -> 1).addElement("a" -> 1).addElement("b" -> 2)
    val copy = map.getCopy
    assertEquals(copy("a").toList, List(1, 1))
    copy.addElement("a" -> 3)
    assertEquals(map("a").toList, List(1, 1))
    assertEquals(map.getAllValues, Set(1, 2))
    assertEquals(map.getCopyWithMappedKeys(_ => "all")("all").toList.sorted, List(1, 1, 2))
    assertEquals(map.getCopyWithFilteredKeys(_ == "b").keys().toSet, Set("b"))
    assertEquals(map.getCopyWithApplied(_ * 2)("a").toList, List(2, 2))
  }
  test("list lookups and removing missing entries do not create buckets") {
    val map = new MultiHashMapList[String, Int]
    assertEquals(map.get("absent"), None)
    val fallback = ListBuffer(7)
    assert(map.getOrElse("absent", fallback) eq fallback)
    map.removeElement("absent" -> 1)
    assert(!map.contains("absent"))
    map("created") += 5
    assertEquals(map("created").toList, List(5))
  }
  test("list maps support structural equality, hashing and self-addition") {
    val map = new MultiHashMapList[String, Int]
    map.addElement("a" -> 1).addElement("b" -> 2)
    val copy = map.getCopy
    assert(map == copy); assertEquals(map.hashCode, copy.hashCode)
    assert(!map.equals("other"))
    map.addAll(map)
    assertEquals(map("a").toList, List(1, 1))
    map.removeAllValues(_ == 1)
    assertEquals(map("a").toList, Nil)
    assertEquals(map("b").toList, List(2, 2))
  }
  test("list transformations use independent snapshots even when callbacks mutate the source") {
    val map = new MultiHashMapList[String, Int]
    map.addElement("a" -> 1).addElement("a" -> 2)
    val copied = map.getCopyWithMappedKeys { key =>
      map.addElement(key -> 99)
      "all"
    }
    assertEquals(copied("all").toList, List(1, 2))
    assertEquals(map("a").toList, List(1, 2, 99))
    val replaced = map.getCopyWithReplacedValues(values => values.reverse.take(2))
    assertEquals(replaced("a").toList, List(99, 2))
    replaced("a").clear()
    assertEquals(map("a").toList, List(1, 2, 99))
  }

  test("list buffer access retains its existing ownership contract") {
    val map = new MultiHashMapList[String, Int]
    val bucket = ListBuffer(1, 2, 2)
    map += ("a" -> bucket)
    assert(map.get("a").exists(_ eq bucket))
    assert(map.iterator.next()._2 eq bucket)
    bucket += 3
    map.removeAllValues(_ == 2)
    assertEquals(bucket.toList, List(1, 3))
    assertEquals(map.getAllEntries, Set("a" -> 1, "a" -> 3))
  }

  test("empty list buckets retain their rendering and copy behavior") {
    val map = new MultiHashMapList[String, Int]
    map("empty")
    assert(map.toString.contains("key \"empty\":\n"))
    assertEquals(map.getCopy.keys().toSet, Set.empty[String])
    assertEquals(map.getAllValues, Set.empty[Int])
    val populated = map.getCopyWithReplacedValues(_ => Seq(7))
    assertEquals(populated("empty").toList, List(7))
  }

  test("set buckets deduplicate, transform values and compare structurally") {
    val map = new MultiHashMapSet[String, Int]
    map.addElement("a" -> 1).addElement("a" -> 1).addElement("a" -> 2)
    assertEquals(map("a").toSet, Set(1, 2))
    val transformed = map.getCopyWithApplied(_ * 2)
    assertEquals(transformed("a").toSet, Set(2, 4))
    val copy = map.getCopyWithApplied(identity)
    assert(map == copy); assertEquals(map.hashCode, copy.hashCode)
    copy.removeAllValues(_ == 1)
    assertEquals(copy("a").toSet, Set(2))
    assertEquals(map("a").toSet, Set(1, 2))
  }
  test("set lookups and removal do not insert absent keys") {
    val map = new MultiHashMapSet[String, Int]
    assertEquals(map.get("absent"), None)
    val fallback = HashSet(7)
    assert(map.getOrElse("absent", fallback) eq fallback)
    map.removeElement("absent" -> 1)
    assert(!map.contains("absent"))
    map("created") += 1
    assert(map.contains("created"))
  }
  test("object pools try factories in order, cache successes and return None for misses") {
    var first = 0; var second = 0
    val pool = CachedObjectPoolFactoryMap[String, Int](Seq(
      (k: String) => { first += 1; Option.when(k == "first")(1) },
      (k: String) => { second += 1; Option.when(k == "second")(2) }))
    assertEquals(pool.get("first"), Some(1)); assertEquals(second, 0)
    assertEquals(pool.get("second"), Some(2)); val queries = (first, second)
    assertEquals(pool.get("second"), Some(2)); assertEquals((first, second), queries)
    assertEquals(pool.get("absent"), None)
    assertEquals(pool.iterator.toMap, Map("first" -> 1, "second" -> 2))
    pool.clear(); assertEquals(pool.iterator.toList, Nil)
  }
  test("object pool updated and removed maps are snapshots without modifying the pool") {
    val pool = CachedObjectPoolFactoryMap[String, Int](_.length)
    pool.get("abc")
    assertEquals(pool.updated("new", 42), Map("abc" -> 3, "new" -> 42))
    assertEquals(pool.removed("abc"), Map.empty[String, Int])
    assertEquals(pool.iterator.toMap, Map("abc" -> 3))
  }
}
