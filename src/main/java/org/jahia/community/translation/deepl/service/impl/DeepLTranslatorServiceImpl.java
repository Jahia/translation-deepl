package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.DeepLException;
import com.deepl.api.Translator;
import com.deepl.api.TranslatorOptions;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.*;
import javax.jcr.RepositoryException;

import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapperImpl;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.LazyPropertyIterator;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.utils.LanguageCodeConverters;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jahia.community.translation.deepl.DeeplConstants.PROP_TARGET_LANGUAGES;

@Component(service = { DeepLTranslatorService.class,
        ManagedService.class }, property = "service.pid=org.jahia.community.translationdeepl", immediate = true)
public class DeepLTranslatorServiceImpl implements DeepLTranslatorService, ManagedService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepLTranslatorServiceImpl.class);
    private String authKey;
    private Map<String, String> targetLanguages;

    @Override
    public JSONArray translate(String path, String srcLanguage, String destLanguage, boolean threeDotsMenu) {
        JSONArray propsArray = new JSONArray();

        try {
            final Locale srcLocale = LanguageCodeConverters.getLocaleFromCode(srcLanguage);
            final String destDeepLLanguage = targetLanguages.containsKey(destLanguage)
                    ? targetLanguages.get(destLanguage)
                    : destLanguage;
            final Locale destLocale = LanguageCodeConverters.languageCodeToLocale(destLanguage);
            LOGGER.info("destDeepLLanguage: " + destDeepLLanguage + " - srcLocale: " + srcLocale + " - destLocale: "
                    + destLocale + " destLanguage: " + destLanguage);
            if (srcLocale != null && destLocale != null) {

                final Map<String, String> translations = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(
                        null, Constants.EDIT_WORKSPACE, srcLocale, (JCRSessionWrapper session) -> {
                            final Map<String, String> srcValues = new HashMap<>();
                            final LazyPropertyIterator propertyIterator = (LazyPropertyIterator) session.getNode(path)
                                    .getProperties();
                            while (propertyIterator.hasNext()) {
                                final JCRPropertyWrapperImpl property = (JCRPropertyWrapperImpl) propertyIterator
                                        .nextProperty();
                                final ExtendedPropertyDefinition definition = property.getDefinition();
                                if (definition.isInternationalized() && !definition.isMultiple()) {
                                    srcValues.put(property.getName(), property.getValue().getString());
                                }
                            }
                            return srcValues;
                        });

                final TranslatorOptions options = new TranslatorOptions().setMaxRetries(3)
                        .setTimeout(Duration.ofSeconds(3));

                final String proxyHost = System.getProperty("https.proxyHost");
                final String proxyPort = System.getProperty("https.proxyPort");
                if (proxyHost != null && proxyPort != null) {
                    final SocketAddress address = new InetSocketAddress(proxyHost, Integer.valueOf(proxyPort));
                    final Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                    options.setProxy(proxy);
                }

                final Translator translator = new Translator(authKey, options);
                for (Map.Entry<String, String> entry : translations.entrySet()) {
                    entry.setValue(
                            translator.translateText(entry.getValue(), srcLanguage, destDeepLLanguage).getText());
                    // entry.setValue(translator.translateText(entry.getValue(), srcLanguage,
                    // destLanguage).getText());

                }

                final boolean result = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null,
                        Constants.EDIT_WORKSPACE, destLocale, (JCRSessionWrapper session) -> {
                            final JCRNodeWrapper node = session.getNode(path);

                            for (Map.Entry<String, String> entry : translations.entrySet()) {

                                try {
                                    JSONObject propJson = new JSONObject();
                                    String nodeType = node.getPrimaryNodeTypeName();

                                    propJson.put("name", nodeType + "_" + entry.getKey());
                                    propJson.put("value", entry.getValue());
                                    propsArray.put(propJson);

                                } catch (JSONException e) {
                                    LOGGER.error("Error JSONException" + e);
                                }
                                if (!threeDotsMenu) {
                                    node.setProperty(entry.getKey(), entry.getValue());
                                    node.saveSession();
                                    session.save();
                                    LOGGER.info(
                                            "Entry key : " + entry.getKey() + " -- translated Value: "
                                                    + entry.getValue());
                                }
                            }

                            return true;
                        });

                if (result && LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Translation from %s to %s done for %s", srcLanguage, destDeepLLanguage,
                            path));
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.error(
                    "InterruptedException: "
                            + String.format("Translation from %s to %s done for %s", srcLanguage, destLanguage, path),
                    ex);
            Thread.currentThread().interrupt();
        } catch (DeepLException | RepositoryException ex) {
            LOGGER.error(
                    "Impossible to translate content: "
                            + String.format("Translation from %s to %s done for %s", srcLanguage, destLanguage, path),
                    ex);
        }
        return propsArray;
    }

    @Override
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (targetLanguages == null)
            targetLanguages = new HashMap<>();
        if (dictionary != null) {
            authKey = (String) dictionary.get("translation.deepl.api.key");
            for (Enumeration<String> keys = dictionary.keys(); keys.hasMoreElements();) {
                String key = keys.nextElement();
                if (key.startsWith(PROP_TARGET_LANGUAGES)) {
                    targetLanguages.put(key.substring(PROP_TARGET_LANGUAGES.length()), (String) dictionary.get(key));
                }
            }
        }
        if (!(authKey != null && !authKey.trim().isEmpty()))
            LOGGER.error(
                    "translation.deepl.api.key not defined. Please add it to org.jahia.community.translationdeepl.cfg");
        LOGGER.debug("translation.deepl.api.key = {}", authKey);
    }

    public static String encode(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '&':
                    encoded.append("&amp;");
                    break;
                case '<':
                    encoded.append("&lt;");
                    break;
                case '>':
                    encoded.append("&gt;");
                    break;
                case '"':
                    encoded.append("&quot;");
                    break;
                case '\'':
                    encoded.append("&#39;");
                    break;
                case '/':
                    encoded.append("&#x2F;");
                    break;
                default:
                    encoded.append(c);
                    break;
            }
        }
        return encoded.toString();
    }

}
