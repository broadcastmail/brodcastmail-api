package com.broadcastmail.api.connection.filter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FilterContextServiceDataTypeTest {

    @ParameterizedTest
    @CsvSource({
            "datetime, 1, datetime",
            "number, 2, number",
            "timestamp with time zone, 3, datetime",
            "date, 3, datetime",
            "uuid, 3, text",
            "jsonb, 3, text",
            "integer, 3, number",
            "text, 3, enum",
            "character varying, 3, enum",
            "USER-DEFINED, 3, enum",
            "text, 500, text",
    })
    void lowCardinalityShouldOnlyMakeTextColumnsEnums(String pgType, int cardinality, String expected) {
        assertThat(FilterContextService.resolveDataType(pgType, cardinality, false)).isEqualTo(expected);
    }
}
