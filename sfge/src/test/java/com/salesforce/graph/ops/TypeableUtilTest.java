package com.salesforce.graph.ops;

import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TypeableUtilTest {

    @Test
    public void testMapType_Simple() {
        String input = "Map<String, Integer>";
        String expectedKeyType = "String";
        String expectedValueType = "Integer";
        assertMapType(input, expectedKeyType, expectedValueType);
    }

    @Disabled // TODO: Handle Nested map scenarios
    @Test
    public void testMapType_NestedMap() {
        String input = "Map<String, Map<String, Id>>";
        String expectedKeyType = "String";
        String expectedValueType = "Map<String, Id>";
        assertMapType(input, expectedKeyType, expectedValueType);
    }

    private void assertMapType(String input, String expectedKeyType, String expectedValueType) {
        Optional<Pair<String, String>> mapType = TypeableUtil.getMapType(input);
        MatcherAssert.assertThat(mapType.isPresent(), Matchers.equalTo(true));
        final String keyType = mapType.get().getKey();
        final String valueType = mapType.get().getValue();

        MatcherAssert.assertThat(keyType, Matchers.equalTo(expectedKeyType));
        MatcherAssert.assertThat(valueType, Matchers.equalTo(expectedValueType));
    }
}
