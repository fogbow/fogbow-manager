package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;

public interface NetworkPlugin {

	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt);

	public Instance getInstance(Token token, String instanceId);

	public void removeInstance(Token token, String instanceId);
	
}
