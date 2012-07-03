package bpipe.executor

import com.hazelcast.core.HazelcastInstance

import java.util.concurrent.ExecutorService
import com.hazelcast.client.ClientConfig
import com.hazelcast.client.HazelcastClient
import bpipe.EventManager
import bpipe.PipelineEvent
import bpipe.PipelineEventListener
import groovy.util.logging.Log
import bpipe.Config

/**
 *  Instantiate a Hazelcast client connecting to a running grid.
 *  <p>
 *  By default the grid is supposed to available on the localhost.
 *  To provide a different host address(es) use provide the configuration
 *  property {@code hazelcast.client.addresses} in the {@code bpipe.config} file
 *  <p>
 *  For example:
 *  <code>
 *  executor="hazelcast"
 *  hazelcast.client.addresses = ["10.90.0.1", "10.90.0.2:5702"]
 *  </code>
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Log
class HazelcastGridProvider implements ExecutorServiceProvider {

    @Lazy
    HazelcastInstance client = {

        def addresses = Config.userConfig.get("hazelcast.client.addresses", "localhost")
        log.info("Connecting Hazelcast grid to addresses: ${addresses}")

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.addAddress( addresses );
        def result = HazelcastClient.newHazelcastClient(clientConfig);
        log.info("Hazelcast grid touched")

        /*
        * register the shutdown event
        */
        EventManager.instance.addListener(PipelineEvent.FINISHED, new PipelineEventListener() {
            @Override
            void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) {
                log.info("Shutting down Hazelcast")
                result.getLifecycleService().shutdown()
            }
        })

        return result

    }()


    def getName() { "Hazelcast" }

    @Override
    ExecutorService getExecutor() {

        client.getExecutorService()

    }
}
