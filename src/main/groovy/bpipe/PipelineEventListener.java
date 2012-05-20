package bpipe;

import java.util.Map;

public interface PipelineEventListener {
	
	void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details);

}
