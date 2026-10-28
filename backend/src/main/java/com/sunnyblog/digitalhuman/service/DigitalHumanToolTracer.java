package com.sunnyblog.digitalhuman.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/** Wraps the article function callbacks so every invocation is persisted. */
@Service
@RequiredArgsConstructor
public class DigitalHumanToolTracer {

    private final DigitalHumanToolCallService toolCalls;

    public List<FunctionCallback> wrap(Long sessionId, FunctionCallback... delegates) {
        return Arrays.stream(delegates)
                .map(delegate -> wrapOne(sessionId, delegate))
                .toList();
    }

    private FunctionCallback wrapOne(Long sessionId, FunctionCallback delegate) {
        return new FunctionCallback() {
            @Override
            public String getName() {
                return delegate.getName();
            }

            @Override
            public String getDescription() {
                return delegate.getDescription();
            }

            @Override
            public String getInputTypeSchema() {
                return delegate.getInputTypeSchema();
            }

            @Override
            public String call(String input) {
                try {
                    String result = delegate.call(input);
                    toolCalls.record(sessionId, delegate.getName(), input, result);
                    return result;
                } catch (RuntimeException ex) {
                    toolCalls.record(sessionId, delegate.getName(), input, "ERROR: " + ex.getMessage());
                    throw ex;
                }
            }

            @Override
            public String call(String input, ToolContext context) {
                try {
                    String result = delegate.call(input, context);
                    toolCalls.record(sessionId, delegate.getName(), input, result);
                    return result;
                } catch (RuntimeException ex) {
                    toolCalls.record(sessionId, delegate.getName(), input, "ERROR: " + ex.getMessage());
                    throw ex;
                }
            }
        };
    }
}
