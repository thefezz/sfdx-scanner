package com.salesforce.graph.ops.expander;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.salesforce.exception.ProgrammingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Holds an instance of ApexPathCollapser for this thread. */
public class ApexPathCollapserProvider {
    private static final Logger LOGGER = LogManager.getLogger(ApexPathCollapserProvider.class);

    @VisibleForTesting static ThreadLocal<ApexPathCollapser> PATH_COLLAPSERS;

    public static void initialize(ApexPathExpanderConfig config) {
        if (LOGGER.isInfoEnabled()) {
            String stackTrace = Arrays.toString(Thread.currentThread().getStackTrace());
            LOGGER.info("initialize() invoked on ApexPathCollaperProvider:" +  stackTrace);
        }
        if (PATH_COLLAPSERS != null && PATH_COLLAPSERS.get() != null) {
//            throw new ProgrammingException(
//                "ApexPathCollapser for this thread should be initialized only once.");
            reset();
            return;
        }
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
            LOGGER.info("reset() invoked on ApexPathCollaperProvider.");
        }
        PATH_COLLAPSERS.get().reset();
    }

    public static void remove() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("remove() invoked on ApexPathCollaperProvider.");
        }
        if (PATH_COLLAPSERS != null) {
            PATH_COLLAPSERS.remove();
            PATH_COLLAPSERS = null;
        }
    }
}
