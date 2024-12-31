package api_test

import com.github.masx200.sqlite_java.Column
import com.github.masx200.sqlite_java.DB
import com.github.masx200.sqlite_java.DB.Companion.connect
import com.github.masx200.sqlite_java.DataSupport
import com.github.masx200.sqlite_java.MyEvent
import com.github.masx200.sqlite_java.Options
import com.github.masx200.sqlite_java.registerEventListenerForIdentifier
import java.util.Arrays
import java.util.function.Consumer
import org.junit.jupiter.api.Test

class APITest {

    @Test
    fun findOneByCondition() {
        val user: User?
        connect().use { db ->
            var up = setUp(db)
            insert()
            user = db.findOneByPredicate<User>(User::class.java, "name = ?", "user3")
            tearDown(up)
        }
        user!!.printJson()
    }

    @Test
    fun updateByCondition() {
        val db = connect()
        db.use { db ->
            var up = setUp(db)
            insert()
            println(db.updateByPredicate<User>(User(Consumer { u: User? -> u!!.age = 70 }), "name = ?", "user4"))
            tearDown(up)
            db.close()
        }

    }

    @Test
    fun findOneById() {
        val user: User?
        connect().use { db ->
            var up = setUp(db)
            insert()
            val id = db.first<User>(User::class.java)!!.id()
            user = db.findOneById<User>(User::class.java, id)
            tearDown(up)
        }
        user!!.printJson()
    }

    @Test
    fun updateById() {
        connect().use { db ->
            var up = setUp(db)
            insert()
            val user = db.first<User>(User::class.java)
            println(db.updateById<User>(user!!.set(Consumer { u: User? -> u!!.age = 60 })))
            tearDown(up)
        }
    }

    @Test
    fun insert() {
        val users = Arrays.asList<User>(User(Consumer { u: User ->
            u.name = "user1"
            u.age = 18
            u.vip = false
        }), User(Consumer { u: User? ->
            u!!.name = "user2"
            u.age = 20
            u.vip = true
        }), User(Consumer { u: User? ->
            u!!.name = "user3"
            u.age = 22
            u.vip = false
        }), User(Consumer { u: User? ->
            u!!.name = "user4"
            u.age = 24
            u.vip = true
        }), User(Consumer { u: User? ->
            u!!.name = "user5"
            u.age = 26
            u.vip = true
        }))

        connect().use { db ->
            var up = setUp(db)
            users.forEach(Consumer { u: User -> println(db.insert<User>(u)) })
            tearDown(up)
        }
    }

    @Test
    fun findAll() {
        val users: MutableList<User>?
        connect().use { db ->
            insert()
            var up = setUp(db)
            users = db.findAll<User>(User::class.java).toMutableList()
            tearDown(up)
        }
        users?.forEach(Consumer { obj: User? -> obj!!.printJson() })
    }

    @Test
    fun findByIds() {
        val users: MutableList<User>?
        connect().use { db ->
            insert()
            var up = setUp(db)
            users = db.findByVarargId<User>(User::class.java, 2L, 1L).toMutableList()
            tearDown(up)
        }
        users?.forEach(Consumer { obj: User? -> obj!!.printJson() })
    }

    @Test
    fun findByIdList() {
        val users: MutableList<User?>?
        connect().use { db ->
            insert()
            var up = setUp(db)
            users = db.findByListId<User>(User::class.java, mutableListOf<Long>(1L, 2L)).toMutableList()
            tearDown(up)
        }
        users!!.forEach(Consumer { obj: User? -> obj!!.printJson() })
    }

    @Test
    fun find() {
        val users: MutableList<User?>?
        connect().use { db ->
            insert()
            var up = setUp(db)
            users = db.findByConsumer<User>(User::class.java, Consumer { options: Options? ->
                options!!
                    .select("name", "age")
                    .where("age <= ? && vip = ?", 50, true)
                    .order("age", Options.DESC)
                    .limit(5)
                    .offset(0)
            }).toMutableList()
            tearDown(up)
        }

        users!!.forEach(Consumer { obj: User? -> obj!!.printJson() })
    }

    @Test
    fun deleteAll() {
        connect().use { db ->
            insert()
            var up = setUp(db)
            println(db.deleteAll<User>(User::class.java))
            tearDown(up)
        }
    }

    @Test
    fun deleteByIdList() {
        connect().use { db ->
            var up = setUp(db)
            println(db.deleteByListId<User>(User::class.java, mutableListOf<Long>(1L, 4L)))
            tearDown(up)
        }
    }

    @Test
    fun deleteByCondition() {
        connect().use { db ->
            insert()
            var up = setUp(db)
            println(db.deleteByPredicate<User>(User::class.java, "name = ?", "user3"))
            tearDown(up)
        }
    }

    @Test
    fun deleteByIds() {
        try {
            connect().use { db ->
                insert()
                var up = setUp(db)
                println(db.deleteByVarargId<User>(User::class.java, 4L))
                tearDown(up)
            }
        } catch (e: Exception) {
//            e.printStackTrace();
            throw e
        }
    }

    @Test
    fun first() {
        val user2: User?
        connect().use { db ->
            var up = setUp(db)
            insert()
            val user1 = db.first<User>(User::class.java)
            user1!!.printJson()
            user2 = db.firstByPredicate<User>(User::class.java, "vip = ?", true)
            tearDown(up)
        }
        user2!!.printJson()
    }

    @Test
    fun last() {
        val user2: User?
        connect().use { db ->
            var up = setUp(db)
            insert()
            val user1 = db.last<User>(User::class.java)
            user1!!.printJson()
            user2 = db.lastByPredicate<User>(User::class.java, "vip = ?", false)
            tearDown(up)
        }
        user2!!.printJson()
    }

    @Test
    fun count() {
        val count2: Long
        connect().use { db ->
            insert()
            var up = setUp(db)
            val count1 = db.count<User>(User::class.java)
            println(count1)
            count2 = db.countByPredicate<User>(User::class.java, "vip = ?", true)
            tearDown(up)
        }
        println(count2)
    }

    @Test
    fun average() {
        val d2: Double
        connect().use { db ->
            insert()
            var up = setUp(db)
            val d1 = db.average<User>(User::class.java, "age")
            println(d1)
            d2 = db.averageByPredicate<User>(User::class.java, "age", "vip = ?", false)
            tearDown(up)
        }
        println(d2)
    }

    @Test
    fun sum() {
        val i2: Int
        connect().use { db ->
            var up = setUp(db)
            insert()
            val i1 = db.sum<User>(User::class.java, "age").toInt()
            println(i1)

            i2 = db.sumByPredicate<User>(User::class.java, "age", "vip = ?", false).toInt()
            tearDown(up)
        }
        println(i2)
    }

    @Test
    fun min() {
        val age2: Int
        connect().use { db ->
            var up = setUp(db)
            insert()
            val age1 = db.min<User>(User::class.java, "age")!!.toInt()
            println(age1)
            age2 = db.minByPredicate<User>(User::class.java, "age", "vip = ?", true)!!.toInt()
            tearDown(up)
        }
        println(age2)
    }

    @Test
    fun max() {
        val age2: Int
        connect().use { db ->
            var up = setUp(db)
            insert()
            val age1 = db.max<User>(User::class.java, "age")!!.toInt()
            println(age1)
            age2 = db.maxByPredicate<User>(User::class.java, "age", "vip = ?", false)!!.toInt()
            tearDown(up)
        }
        println(age2)
    }

    @Test
    fun version() {
        val version: String?
        connect().use { db ->
            version = db.version()
        }
        println(version)
    }
}

// 每个测试开始前执行
fun tearDown(listeners: MutableList<AutoCloseable>) {
    println("After each test")
    listeners.forEach { it.close() }
}

fun setUp(db: DB): MutableList<AutoCloseable> {
    var listeners = mutableListOf<AutoCloseable>()
    println("Before each test")
    DB.sqlIdentifiers.forEach { identifier ->
        listeners.add(
            registerEventListenerForIdentifier(db, identifier) { identifier, it: MyEvent ->
                System.out.println("$identifier:" + "Received event:" + it.message)
            }
        )
    }
    return listeners
}


// 每个测试结束后执行


class User(consumer: Consumer<User>) : DataSupport<User>(consumer) {
    @Column(index = true)
    var uid: Long? = null
    var name: String? = null
    var age: Int? = null
    var vip: Boolean? = null

    @Column(json = true)
    var labels: MutableList<String?>? = null
}

class Book(consumer: Consumer<Book>) : DataSupport<Book>(consumer) {
    var name: String? = null
    var author: String? = null
    var price: Double? = null
}

fun connect(): DB {
    val db = connect("database/example.db")
    db.tables(User::class.java)
    // db.deleteAll(User.class);
    return db
}



