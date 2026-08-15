package org.jahia.community.translation.assisted.service;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the {@link TranslatorService} that should serve a translation request: the
 * highest-ranked one that is actually {@link TranslatorService#isAvailable() available}.
 *
 * <p>Service ranking alone is not enough. Before the migration to the genai-connector module,
 * the LLM translator was gated by {@code ConfigurationPolicy.REQUIRE} on a configuration the
 * manager deleted whenever no API key was set, so an unusable translator simply was not
 * registered and a plain highest-ranking lookup landed on DeepL. The provider key now lives in
 * the connector's own configuration, which this module gets no notification about — so the LLM
 * translator stays registered even while the connector is unconfigured, and availability has to
 * be checked at lookup time instead.</p>
 */
public final class TranslatorServiceResolver {

    private static final Logger logger = LoggerFactory.getLogger(TranslatorServiceResolver.class);

    private TranslatorServiceResolver() {
    }

    /**
     * @return the highest-ranked available translator, or empty when none is configured
     */
    public static Optional<TranslatorService> resolve() {
        Bundle bundle = FrameworkUtil.getBundle(TranslatorServiceResolver.class);
        BundleContext bundleContext = bundle == null ? null : bundle.getBundleContext();
        if (bundleContext == null) {
            logger.warn("No bundle context available to look up a TranslatorService");
            return Optional.empty();
        }

        final Collection<ServiceReference<TranslatorService>> references;
        try {
            references = bundleContext.getServiceReferences(TranslatorService.class, null);
        } catch (InvalidSyntaxException e) {
            // Unreachable: the filter is null.
            logger.error("Unable to look up the TranslatorService implementations", e);
            return Optional.empty();
        }

        // ServiceReference orders ascending by ranking, so reverse it to try the preferred one first.
        List<ServiceReference<TranslatorService>> ranked = new ArrayList<>(references);
        ranked.sort(Comparator.reverseOrder());

        for (ServiceReference<TranslatorService> reference : ranked) {
            TranslatorService translatorService = bundleContext.getService(reference);
            if (translatorService == null) {
                continue;
            }
            boolean available = translatorService.isAvailable();
            // Both implementations are immediate DS components of this bundle: their lifecycle is
            // owned by SCR, not by our usage count, so releasing the reference here is safe and
            // keeps repeated lookups from inflating it.
            bundleContext.ungetService(reference);
            if (available) {
                return Optional.of(translatorService);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping translator {}: not available", translatorService.getProviderKey());
            }
        }
        return Optional.empty();
    }
}
