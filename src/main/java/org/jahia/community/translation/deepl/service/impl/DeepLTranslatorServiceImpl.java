package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;
import com.deepl.api.TranslatorOptions;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jahia.community.translation.deepl.DeeplConstants.PROP_API_KEY;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_PREFIX_TARGET_LANGUAGES;
import static org.jahia.community.translation.deepl.DeeplConstants.SERVICE_CONFIG_FILE_FULLNAME;
import static org.jahia.community.translation.deepl.DeeplConstants.SERVICE_CONFIG_FILE_NAME;
import static org.jahia.community.translation.deepl.DeeplConstants.SUBTREE_ITERABLE_TYPES;

@Component(service = DeepLTranslatorService.class, configurationPid = SERVICE_CONFIG_FILE_NAME)
public class DeepLTranslatorServiceImpl implements DeepLTranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(DeepLTranslatorServiceImpl.class);
    private static final String SLASH = "/";

    private Translator translator;
    private final Map<String, String> targetLanguages = new HashMap<>();

    @Activate
    public void activate(Map<String, ?> properties) {
        translator = null;
        targetLanguages.clear();
        if (properties == null) {
            logger.error("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }

        final String authKey = (String) properties.getOrDefault(PROP_API_KEY, null);
        logger.debug("{} = {}", PROP_API_KEY, authKey);
        translator = initializeTranslator(authKey);
        if (translator == null) return;

        properties.entrySet().stream()
                .filter(e -> e.getKey().startsWith(PROP_PREFIX_TARGET_LANGUAGES))
                .forEach(e -> targetLanguages.put(e.getKey().substring(PROP_PREFIX_TARGET_LANGUAGES.length()), (String) e.getValue()));

    }

    private Translator initializeTranslator(String authKey) {
        if (StringUtils.isBlank(authKey)) {
            logger.error("{} not defined. Please add it to {}", PROP_API_KEY, SERVICE_CONFIG_FILE_FULLNAME);
            return null;
        }

        final TranslatorOptions options = new TranslatorOptions().setMaxRetries(3).setTimeout(Duration.ofSeconds(3));

        final String proxyHost = System.getProperty("https.proxyHost");
        final String proxyPort = System.getProperty("https.proxyPort");
        if (proxyHost != null && proxyPort != null) {
            final SocketAddress address = new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort));
            final Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
            options.setProxy(proxy);
        }

        return new Translator(authKey, options);
    }

    @Override
    public void translate(JCRNodeWrapper node, boolean translateSubtree, String targetLanguage, boolean allLanguages) throws RepositoryException {
        final Map<String, String> texts = new HashMap<>();
        scanTexts(node, translateSubtree, texts);

        final JCRSessionWrapper session = node.getSession();
        final Locale srcLocale = session.getLocale();
        final String srcLanguage = LanguageCodeConverters.localeToLanguageTag(srcLocale);
        if (allLanguages) {
            node.getResolveSite().getLanguages().stream()
                    .filter(language -> !StringUtils.equals(language, srcLanguage))
                    .forEach(lang -> translateAndSave(texts, srcLanguage, lang, session.getUser()));
        } else {
            translateAndSave(texts, srcLanguage, targetLanguage, session.getUser());
        }
    }

    private void scanTexts(JCRNodeWrapper node, boolean translateSubtree, Map<String, String> texts) {
        analyzeNode(node, texts);
        if (translateSubtree) {
            JCRContentUtils.getChildrenOfType(node, SUBTREE_ITERABLE_TYPES)
                    .forEach(child -> scanTexts(child, true, texts));
        }
    }

    private void analyzeNode(JCRNodeWrapper node, Map<String, String> texts) {
        if (!isTranslatableNode(node)) return;

        final PropertyIterator properties;
        try {
            properties = node.getProperties();
        } catch (RepositoryException e) {
            logger.error("", e);
            return;
        }
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            if (!isTranslatableProperty(property)) continue;
            try {
                final String key = node.getPath() + SLASH + property.getName();
                texts.put(key, property.getValue().getString());
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
    }

    private void translateAndSave(Map<String, String> texts, String srcLanguage, String targetLanguage, JahiaUser user) {
        final Map<String, String> translations = generateTranslations(texts, srcLanguage, targetLanguage);

        if (MapUtils.isEmpty(translations)) return;

        try {
            JCRTemplate.getInstance().doExecute(user, Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage), session -> saveTranslations(session, translations));
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private Map<String, String> generateTranslations(Map<String, String> texts, String srcLanguage, String destLanguage) {
        if (translator == null) {
            return null;
        }
        if (MapUtils.isEmpty(texts)) {
            return null;
        }

        final String destDeepLLanguage = targetLanguages.getOrDefault(destLanguage, destLanguage);
        final int nbTexts = texts.size();
        final List<String> keys = new ArrayList<>(nbTexts);
        final List<String> srcTexts = new ArrayList<>(nbTexts);
        texts.forEach((k, v) -> {
            keys.add(k);
            srcTexts.add(v);
        });
        final List<TextResult> results;
        try {
            results = translator.translateText(srcTexts, srcLanguage, destDeepLLanguage);
        } catch (DeepLException | InterruptedException e) {
            logger.error("Failed to translate content", e);
            return null;
        }

        return IntStream.range(0, nbTexts)
                .boxed()
                .collect(Collectors.toMap(keys::get, i -> results.get(i).getText()));
    }

    private static Object saveTranslations(JCRSessionWrapper session, Map<String, String> translations) {
        translations.forEach((path, value) -> {
            try {
                session.getNode(StringUtils.substringBeforeLast(path, SLASH)).setProperty(StringUtils.substringAfterLast(path, SLASH), value);
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        });

        try {
            session.save();
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return null;
    }

    private boolean isTranslatableNode(JCRNodeWrapper node) {
        try {
            if (node.isNodeType(Constants.JAHIANT_PAGE)) return true;
            if (node.isNodeType(Constants.JAHIANT_CONTENT)) return true;
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return false;
    }

    private boolean isTranslatableProperty(Property property) {
        final ExtendedPropertyDefinition definition;
        try {
            definition = (ExtendedPropertyDefinition) property.getDefinition();
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }

        return definition.isInternationalized() && !definition.isMultiple();
    }
}
