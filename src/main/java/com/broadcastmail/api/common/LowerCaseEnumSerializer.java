package com.broadcastmail.api.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class LowerCaseEnumSerializer extends StdSerializer<Enum<?>> {

    public LowerCaseEnumSerializer() {
        super(Enum.class, false);
    }

    @Override
    public void serialize(Enum<?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.name().toLowerCase());
    }
}
