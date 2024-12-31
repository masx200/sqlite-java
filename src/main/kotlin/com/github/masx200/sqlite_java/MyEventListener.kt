package com.github.masx200.sqlite_java

import com.google.common.eventbus.Subscribe

class MyEventListener(var callback: (MyEvent) -> Unit) {
    @Subscribe
    fun handleMyEvent(event: MyEvent) {
        callback(event)
    }
}

