package org.fogbowcloud.manager.core;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public class RoundRobinMemberPicker implements FederationMemberPicker {

	private int current = -1;
	
	@Override
	public FederationMember pick(ManagerFacade facade) {
		List<FederationMember> members = facade.getMembers();
		if (members.isEmpty()) {
			return null;
		}
		current = (current + 1) % members.size();
		FederationMember currentMember = members.get(current);
		
		String myJid = facade.getProperties().getProperty("xmpp_jid");
		if (currentMember.getResourcesInfo().getId().equals(myJid) && members.size() > 1) {
			current = (current + 1) % members.size();
			currentMember = members.get(current);
		}
		
		return members.get(current);
	}

}
