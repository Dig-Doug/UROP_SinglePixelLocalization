package com.roeper.bu.urop.lib;

public class BrokerConfig {
	private String hostname;
	private String username;
	private String password;
	private String topicPrefix;
	
	protected BrokerConfig()
	{
		
	}
	
	public BrokerConfig(String aHostname, String aUsername, String aPassword, String aTopicPrefix)
	{
		this.hostname = aHostname;
		this.username = aUsername;
		this.password = aPassword;
		this.topicPrefix = aTopicPrefix;
	}

	public String getHostname() {
		return hostname;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getTopicPrefix() {
		return topicPrefix;
	}
}
