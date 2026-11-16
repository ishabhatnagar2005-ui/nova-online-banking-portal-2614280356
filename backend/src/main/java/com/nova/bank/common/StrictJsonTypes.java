package com.nova.bank.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Require the JSON scalar types specified by selected request contracts. */
public final class StrictJsonTypes {
    private StrictJsonTypes() {}

    /** Read only a JSON string, without coercing numbers or booleans. */
    public static class Text extends JsonDeserializer<String> {
        @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) return (String) context.handleUnexpectedToken(String.class, parser);
            return parser.getText();
        }
    }

    /** Read only a JSON boolean, without coercing strings or numbers. */
    public static class BooleanValue extends JsonDeserializer<Boolean> {
        @Override public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
            return parser.getBooleanValue();
        }
    }
}
