package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.DeepLException;
import com.deepl.api.Translator;
import com.deepl.api.TranslatorOptions;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.jcr.RepositoryException;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapperImpl;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.LazyPropertyIterator;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = {ManagedService.class}, property = "service.pid=org.jahia.community.translationdeepl", immediate = true)
public class DeepLTranslatorServiceImpl implements DeepLTranslatorService, ManagedService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepLTranslatorServiceImpl.class);
    private String authKey;

    @Override
    public void translate(String path, String srcLanguage, String destLanguage) {
        try {
            final Locale srcLocale = LanguageCodeConverters.getLocaleFromCode(srcLanguage);
            final Locale destLocale = LanguageCodeConverters.getLocaleFromCode(destLanguage);

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
                    entry.setValue(translator.translateText(entry.getValue(), srcLanguage, destLanguage).getText());
                }

                final boolean result = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, destLocale, (JCRSessionWrapper session) -> {
                    final JCRNodeWrapper node = session.getNode(path);
                    for (Map.Entry<String, String> entry : translations.entrySet()) {
                        node.setProperty(entry.getKey(), entry.getValue());
                    }
                    session.save();
                    return true;
                });

                if (result && LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Translation from %s to %s done for %s", srcLanguage, destLanguage, path));
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.error("InterruptedException: ", ex);
            Thread.currentThread().interrupt();
        } catch (DeepLException | RepositoryException ex) {
            LOGGER.error("Impossible to translate content", ex);
        }
    }

    @Override
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if(dictionary != null)
            authKey = (String) dictionary.get("translation.deepl.api.key");
        if(!(authKey != null && !authKey.trim().isEmpty()))
            authKey = SettingsBean.getInstance().getPropertyValue("translation.deepl.api.key");
        if(!(authKey != null && !authKey.trim().isEmpty()))
            LOGGER.error("translation.deepl.api.key not defined");
        LOGGER.debug("translation.deepl.api.key = {}",authKey);
    }
}
