package com.salvador.bmxbuilder.agent.llm;

/** The LLM couldn't be used: no API key, network down, rate limited, or the provider returned an error. */
public class LLMException extends RuntimeException {

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
