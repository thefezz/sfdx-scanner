package com.salesforce.rules.unusedmethod;

import com.salesforce.graph.vertex.MethodVertex;

public class StaticMethodCallValidator extends BaseMethodCallValidator {

    public StaticMethodCallValidator(MethodVertex targetMethod, RuleStateTracker ruleStateTracker) {
        super(targetMethod, ruleStateTracker);
    }

    @Override
    protected boolean internalUsageDetected() {
        /*
        An internal invocation of a static method can be:
        - method()
        They can happen in:
        - The class itself
            - always
        - A subclass
            - Only if the class is virtual/abstract and the method's visibility is >= public (static can't be protected)
        - An inner class
            - Unless the inner class inherits a method with the same name
         */
        return false;
    }

    @Override
    protected boolean externalUsageDetected() {
        // TODO: EXPLAIN WHAT EXTERNAL USAGE MEANS IN THIS CONTEXT AND IMPLEMENT THIS METHOD.
        return false;
    }
}
