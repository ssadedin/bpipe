package bpipe

import org.junit.Test

import static org.junit.Assert.*
import org.junit.Before

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class EventManagerTest {

    @Before
    def void clear() {
        EventManager.instance.listeners.clear()
    }

    @Test
    def void testAddListener() {

        def lister = new PipelineEventListener() {
            @Override
            void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) { }
        }

        EventManager.instance.addListener( PipelineEvent.STARTED, lister )
        assertEquals( 1, EventManager.instance.listeners.size() )
        assertEquals( lister, EventManager.instance.listeners[PipelineEvent.STARTED][0] )

    }


    @Test
    def void testRemoveListener() {

        def lister = new PipelineEventListener() {
            @Override
            void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) { }
        }

        EventManager.instance.addListener( PipelineEvent.STARTED, lister )
        assertEquals( 1, EventManager.instance.listeners.size() )
        assertEquals( 1, EventManager.instance.listeners[PipelineEvent.STARTED].size() )

        EventManager.instance.removeListener( PipelineEvent.STARTED, lister )
        assertEquals( 0, EventManager.instance.listeners[PipelineEvent.STARTED].size() )
    }




}
