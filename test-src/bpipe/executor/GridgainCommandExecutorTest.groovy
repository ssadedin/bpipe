package bpipe.executor

import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class GridgainCommandExecutorTest {


    @Test
    public void testAcceptList() {

        assertTrue( GridgainCommandExecutor.acceptLib('mail.jar'))
        assertFalse( GridgainCommandExecutor.acceptLib('groovy-1.8.x.jar'))
        assertFalse( GridgainCommandExecutor.acceptLib('groovypp-x.y.z.jar'))
        assertFalse( GridgainCommandExecutor.acceptLib('commons-cli-1.2.jar'))
        assertTrue( GridgainCommandExecutor.acceptLib('any-other-lib-is-accepted.jar'))
    }


}
