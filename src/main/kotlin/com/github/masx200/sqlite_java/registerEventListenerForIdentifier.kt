package com.github.masx200.sqlite_java

fun registerEventListenerForIdentifier(
    db: DB,
    identifier: String,
    callback: (identifier: String, myevent: MyEvent) -> Unit
): AutoCloseable {
    var asyncEventBus = db.getAsyncEventBus(identifier)
    var myEventListener = MyEventListener {
        callback(identifier, it)
    }
    asyncEventBus.register(myEventListener)
    return object : AutoCloseable {
        override fun close() {
            asyncEventBus.unregister(myEventListener)
        }
    }

}