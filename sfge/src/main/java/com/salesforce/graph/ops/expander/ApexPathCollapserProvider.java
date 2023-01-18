package com.salesforce.graph.ops.expander;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Holds an instance of ApexPathCollapser for this thread. */
public class ApexPathCollapserProvider {
    private static final Logger LOGGER = LogManager.getLogger(ApexPathCollapserProvider.class);

    @VisibleForTesting static ThreadLocal<ApexPathCollapser> PATH_COLLAPSERS;

    static void initialize(ApexPathExpanderConfig config) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("initialize() invoked on ApexPathCollaperProvider.");
        }
        //                if (PATH_COLLAPSERS != null && PATH_COLLAPSERS.get() != null) {
        //                    throw new ProgrammingException(
        //                            "ApexPathCollapser for this thread should be initialized only
        // once.");
        //                }
        final List<ApexDynamicPathCollapser> dynamicPathCollapsers = config.getDynamicCollapsers();
        if (!dynamicPathCollapsers.isEmpty()) {
            PATH_COLLAPSERS =
                    ThreadLocal.withInitial(() -> new ApexPathCollapserImpl(dynamicPathCollapsers));
        } else {
            PATH_COLLAPSERS = ThreadLocal.withInitial(() -> NoOpApexPathCollapser.getInstance());
        }
    }

    static ApexPathCollapser get() {
        return PATH_COLLAPSERS.get();
    }

    static void reset() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("clear() invoked on ApexPathCollaperProvider.");
        }
        PATH_COLLAPSERS.get().reset();
        PATH_COLLAPSERS.remove();
    }
}
