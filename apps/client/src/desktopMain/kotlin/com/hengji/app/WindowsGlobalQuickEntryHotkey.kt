package com.hengji.app

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import java.awt.EventQueue
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class WindowsGlobalQuickEntryHotkey(
    private val onTriggered: () -> Unit,
    private val onStatus: (String) -> Unit,
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private var messageThreadId: Int? = null
    private var worker: Thread? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val ready = CountDownLatch(1)
        worker = Thread(
            {
                messageThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
                val registered = User32.INSTANCE.RegisterHotKey(
                    null,
                    HOTKEY_ID,
                    WinUser.MOD_CONTROL or WinUser.MOD_SHIFT or MOD_NOREPEAT,
                    KeyEvent.VK_N,
                )
                EventQueue.invokeLater {
                    onStatus(
                        if (registered) {
                            "全局快捷记账：Ctrl+Shift+N；应用内也可使用同一组合键。"
                        } else {
                            "Ctrl+Shift+N 已被其他程序占用；衡记未覆盖冲突，应用内快捷键仍可用。"
                        },
                    )
                }
                ready.countDown()
                if (!registered) return@Thread
                try {
                    val message = WinUser.MSG()
                    while (User32.INSTANCE.GetMessage(message, null, 0, 0) > 0) {
                        if (message.message == WinUser.WM_HOTKEY && message.wParam.toInt() == HOTKEY_ID) {
                            EventQueue.invokeLater(onTriggered)
                        }
                    }
                } finally {
                    User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID)
                }
            },
            "hengji-global-quick-entry",
        ).apply {
            isDaemon = true
            start()
        }
        if (!ready.await(2, TimeUnit.SECONDS)) {
            onStatus("全局快捷键注册超时；应用内 Ctrl+Shift+N 仍可用。")
        }
    }

    override fun close() {
        if (!started.compareAndSet(true, false)) return
        messageThreadId?.let { threadId ->
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, WPARAM(0), LPARAM(0))
        }
        worker = null
    }

    private companion object {
        const val HOTKEY_ID = 0x484A
        const val MOD_NOREPEAT = 0x4000
    }
}
