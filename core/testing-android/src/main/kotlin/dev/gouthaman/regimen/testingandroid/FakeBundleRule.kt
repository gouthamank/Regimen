package dev.gouthaman.regimen.testingandroid

import android.os.Bundle
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.WeakHashMap

class FakeBundleRule : TestWatcher() {

    private val backing = WeakHashMap<Bundle, MutableMap<String, Any?>>()

    private fun store(bundle: Bundle) = backing.getOrPut(bundle) { mutableMapOf() }

    override fun starting(description: Description) {
        mockkConstructor(Bundle::class)

        every { anyConstructed<Bundle>().putLong(any(), any()) } answers {
            store(self as Bundle)[firstArg()] = secondArg<Long>()
        }
        every { anyConstructed<Bundle>().getLong(any()) } answers {
            store(self as Bundle)[firstArg()] as? Long ?: 0L
        }
        every { anyConstructed<Bundle>().getLong(any(), any()) } answers {
            store(self as Bundle)[firstArg()] as? Long ?: secondArg()
        }

        every { anyConstructed<Bundle>().putInt(any(), any()) } answers {
            store(self as Bundle)[firstArg()] = secondArg<Int>()
        }
        every { anyConstructed<Bundle>().getInt(any()) } answers {
            store(self as Bundle)[firstArg()] as? Int ?: 0
        }
        every { anyConstructed<Bundle>().getInt(any(), any()) } answers {
            store(self as Bundle)[firstArg()] as? Int ?: secondArg()
        }

        every { anyConstructed<Bundle>().putString(any(), any()) } answers {
            store(self as Bundle)[firstArg()] = secondArg<String?>()
        }
        every { anyConstructed<Bundle>().getString(any()) } answers {
            store(self as Bundle)[firstArg()] as? String
        }
        every { anyConstructed<Bundle>().getString(any(), any()) } answers {
            store(self as Bundle)[firstArg()] as? String ?: secondArg()
        }

        // String route args (UUID ids) go through SavedStateHandle's CharSequence path, not
        // putString/getString directly.
        every { anyConstructed<Bundle>().putCharSequence(any(), any()) } answers {
            store(self as Bundle)[firstArg()] = secondArg<CharSequence?>()
        }
        every { anyConstructed<Bundle>().getCharSequence(any()) } answers {
            store(self as Bundle)[firstArg()] as? CharSequence
        }
        every { anyConstructed<Bundle>().getCharSequence(any(), any()) } answers {
            store(self as Bundle)[firstArg()] as? CharSequence ?: secondArg()
        }

        every { anyConstructed<Bundle>().putBoolean(any(), any()) } answers {
            store(self as Bundle)[firstArg()] = secondArg<Boolean>()
        }
        every { anyConstructed<Bundle>().getBoolean(any()) } answers {
            store(self as Bundle)[firstArg()] as? Boolean ?: false
        }
        every { anyConstructed<Bundle>().getBoolean(any(), any()) } answers {
            store(self as Bundle)[firstArg()] as? Boolean ?: secondArg()
        }

        every { anyConstructed<Bundle>().containsKey(any()) } answers {
            store(self as Bundle).containsKey(firstArg())
        }
        every { anyConstructed<Bundle>().isEmpty } answers {
            store(self as Bundle).isEmpty()
        }

        // Generic untyped getter (declared on BaseBundle) - SavedStateHandle's/Navigation's route
        // decoding calls this directly for String-typed route args rather than getString/
        // getCharSequence.
        every { anyConstructed<Bundle>().get(any()) } answers {
            store(self as Bundle)[firstArg()]
        }
    }

    override fun finished(description: Description) {
        unmockkConstructor(Bundle::class)
        backing.clear()
    }
}
