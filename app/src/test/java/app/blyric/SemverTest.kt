package app.blyric

import app.blyric.update.Semver
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemverTest {
    private fun v(s: String) = Semver.parse(s)!!

    @Test fun ordersNumerically() {
        assertTrue(v("1.10.0") > v("1.9.9"))
        assertTrue(v("2.0.0") > v("1.99.99"))
        assertTrue(v("v1.0.1") > v("1.0.0"))
    }

    @Test fun stableBeatsPrerelease() {
        assertTrue(v("1.0.0") > v("1.0.0-beta.1"))
        assertTrue(v("1.0.0-beta.2") > v("1.0.0-beta.1"))
    }

    @Test fun rejectsInvalid() {
        assertNull(Semver.parse("1.0"))
        assertNull(Semver.parse("abc"))
    }
}
