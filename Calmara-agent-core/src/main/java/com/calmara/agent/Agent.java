package com.calmara.agent;

public interface Agent {

    String getAgentName();

    String getSystemPrompt();

    AgentResponse execute(String input, AgentContext context);
}
