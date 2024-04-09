package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.DeepLException;
import com.deepl.api.Translator;
import com.deepl.api.TranslatorOptions;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import javax.jcr.RepositoryException;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapperImpl;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.LazyPropertyIterator;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jahia.community.translation.deepl.DeeplConstants.PROP_API_KEY;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_PREFIX_TARGET_LANGUAGES;
import static org.jahia.community.translation.deepl.DeeplConstants.SERVICE_CONFIG_FILE_FULLNAME;
import static org.jahia.community.translation.deepl.DeeplConstants.SERVICE_CONFIG_FILE_NAME;

@Component(service = DeepLTranslatorService.class, configurationPid = SERVICE_CONFIG_FILE_NAME)
public class DeepLTranslatorServiceImpl implements DeepLTranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(DeepLTranslatorServiceImpl.class);

    private String authKey;
    private Map<String, String> targetLanguages = new HashMap<>();

    @Override
    public void translate(String path, String srcLanguage, String destLanguage) {
        try {
            final Locale srcLocale = LanguageCodeConverters.getLocaleFromCode(srcLanguage);
            final String destDeepLLanguage = targetLanguages.getOrDefault(destLanguage, destLanguage);
            final Locale destLocale = LanguageCodeConverters.getLocaleFromCode(destDeepLLanguage);

            if (srcLocale != null && destLocale != null) {

                final Map<String, String> translations = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, srcLocale, (JCRSessionWrapper session) -> {
                    final Map<String, String> srcValues = new HashMap<>();
                    final LazyPropertyIterator propertyIterator = (LazyPropertyIterator) session.getNode(path).getProperties();
                    while (propertyIterator.hasNext()) {
                        final JCRPropertyWrapperImpl property = (JCRPropertyWrapperImpl) propertyIterator.nextProperty();
                        final ExtendedPropertyDefinition definition = property.getDefinition();
                        if (definition.isInternationalized() && !definition.isMultiple()) {
                            srcValues.put(property.getName(), property.getValue().getString());
                        }
                    }
                    return srcValues;
                });

                final TranslatorOptions options = new TranslatorOptions().setMaxRetries(3).setTimeout(Duration.ofSeconds(3));

                final String proxyHost = System.getProperty("https.proxyHost");
                final String proxyPort = System.getProperty("https.proxyPort");
                if (proxyHost != null && proxyPort != null) {
                    final SocketAddress address = new InetSocketAddress(proxyHost, Integer.valueOf(proxyPort));
                    final Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                    options.setProxy(proxy);
                }

                final Translator translator = new Translator(authKey, options);
                for (Map.Entry<String, String> entry : translations.entrySet()) {
                    entry.setValue(translator.translateText(entry.getValue(), srcLanguage, destDeepLLanguage).getText());
                }

                final boolean result = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, destLocale, (JCRSessionWrapper session) -> {
                    final JCRNodeWrapper node = session.getNode(path);
                    for (Map.Entry<String, String> entry : translations.entrySet()) {
                        node.setProperty(entry.getKey(), entry.getValue());
                    }
                    session.save();
                    return true;
                });

                if (result && logger.isInfoEnabled()) {
                    logger.info(String.format("Translation from %s to %s done for %s", srcLanguage, destDeepLLanguage, path));
                }
            }
        } catch (InterruptedException ex) {
            logger.error("InterruptedException: ", ex);
            Thread.currentThread().interrupt();
        } catch (DeepLException | RepositoryException ex) {
            logger.error("Impossible to translate content", ex);
        }
    }

    @Activate
    public void activate(Map<String, ?> properties) {
        targetLanguages.clear();
        if (properties == null) {
            logger.warn("Missing configurations");
            return;
        }

        authKey = (String) properties.getOrDefault(PROP_API_KEY, null);
        properties.entrySet().stream()
                .filter(e -> e.getKey().startsWith(PROP_PREFIX_TARGET_LANGUAGES))
                .collect(Collectors.toMap(e -> e.getKey().substring(PROP_PREFIX_TARGET_LANGUAGES.length()), e -> (String) (e.getValue()), throwingMerger(), () -> targetLanguages));
        if (StringUtils.isBlank(authKey))
            logger.error("translation.deepl.api.key not defined. Please add it to {}", SERVICE_CONFIG_FILE_FULLNAME);
        logger.debug("{} = {}", PROP_API_KEY, authKey);
    }

    private static <T> BinaryOperator<T> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
    }

}
