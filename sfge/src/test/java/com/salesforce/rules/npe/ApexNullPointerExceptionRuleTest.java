package com.salesforce.rules.npe;

import com.salesforce.rules.AbstractPathBasedRule;
import com.salesforce.rules.ApexNullPointerExceptionRule;
import com.salesforce.testutils.BasePathBasedRuleTest;
import com.salesforce.testutils.ViolationWrapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ApexNullPointerExceptionRuleTest extends BasePathBasedRuleTest {

    @ValueSource(strings = {"String s", "String s = null", "String s = getNullStr()"})
    @ParameterizedTest(name = "{displayName}: {0}")
    @Disabled
    public void testNullInitialization_expectViolation(String initialization) {
        // spotless:off
        String sourceCode =
            "public class MyClass {\n"
          + "    public void foo() {\n"
          + "        " + initialization + ";\n"
          // Since `s` is definitely null, this should throw an NPE.
          + "        Integer i = s.length();\n"
          + "    }\n"
          + "    \n"
          + "    private void getNullStr() {\n"
          + "        return null;\n"
          + "    }\n"
          + "}";
        // spotless:on
        AbstractPathBasedRule rule = ApexNullPointerExceptionRule.getInstance();
        assertViolations(rule, sourceCode, expect(4, "s.length"));
    }

    protected ViolationWrapper.NullPointerViolationBuilder expect(int line, String operation) {
        return ViolationWrapper.NullPointerViolationBuilder.get(line, operation);
    }
}
