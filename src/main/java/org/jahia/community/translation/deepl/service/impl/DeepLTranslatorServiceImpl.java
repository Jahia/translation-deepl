package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.TextTranslationOptions;
import com.deepl.api.Translator;
import com.deepl.api.TranslatorOptions;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.service.DeepLTranslationResponse;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
    public DeepLTranslationResponse translate(JCRNodeWrapper pNode, boolean translateSubtree, String sourceLanguage, String targetLanguage, boolean allLanguages) throws RepositoryException {
        final JCRSessionWrapper pNodeSession = pNode.getSession();
        final Locale pNodeSrcLocale = pNodeSession.getLocale();
        final String pNodeSrcLanguage = LanguageCodeConverters.localeToLanguageTag(pNodeSrcLocale);
        final Set<String> siteLanguages = pNode.getResolveSite().getLanguages();
        if (!allLanguages) {
            if (!siteLanguages.contains(targetLanguage)) {
                return new DeepLTranslationResponseImpl(false, "The language " + targetLanguage + " is not allowed on the site");
            } else if (StringUtils.equals(sourceLanguage, targetLanguage)) {
                return new DeepLTranslationResponseImpl(false, "The language " + targetLanguage + " is both the source and target language");
            }
        }

        final JCRNodeWrapper node;
        final JCRSessionWrapper session;
        if (!StringUtils.equals(sourceLanguage, pNodeSrcLanguage)) {
            session = JCRSessionFactory.getInstance().getCurrentUserSession(pNodeSession.getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
            final String path = pNode.getPath();
            if (!session.nodeExists(path)) {
                return new DeepLTranslationResponseImpl(false, "Impossible to translate from " + sourceLanguage + " since the node doesn't exist in this language");
            }
            node = session.getNode(path);
        } else {
            node = pNode;
            session = pNodeSession;
        }

        final TranslationData data = new TranslationData();
        scanTexts(node, translateSubtree, data);

        if (allLanguages) {
            return siteLanguages.stream()
                    .filter(language -> !StringUtils.equals(language, sourceLanguage))
                    .map(lang -> translateAndSave(data, sourceLanguage, lang, session.getUser()))
                    .reduce(new DeepLTranslationResponseImpl(false, null), DeepLTranslationResponse::merge);
        } else {
            return translateAndSave(data, sourceLanguage, targetLanguage, session.getUser());
        }
    }

    private void scanTexts(JCRNodeWrapper node, boolean translateSubtree, TranslationData data) {
        analyzeNode(node, data);
        if (translateSubtree) {
            JCRContentUtils.getChildrenOfType(node, SUBTREE_ITERABLE_TYPES)
                    .forEach(child -> scanTexts(child, true, data));
        }
    }

    private void analyzeNode(JCRNodeWrapper node, TranslationData data) {
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
                final String stringValue = StringUtils.trimToNull(property.getValue().getString());
                if (stringValue == null) continue;
                data.trackText(key, stringValue);
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
    }

    private DeepLTranslationResponse translateAndSave(TranslationData data, String srcLanguage, String targetLanguage, JahiaUser user) {
        final Map<String, String> translations = generateTranslations(data, srcLanguage, targetLanguage);

        if (MapUtils.isEmpty(translations))
            return new DeepLTranslationResponseImpl(false, "Nothing to translate in " + targetLanguage);

        try {
            final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
            if (saveTranslations(session, translations)) {
                return new DeepLTranslationResponseImpl(true, "Content translated in " + targetLanguage);
            } else {
                return new DeepLTranslationResponseImpl(false, "Nothing to translate in " + targetLanguage);
            }
        } catch (RepositoryException e) {
            logger.error("", e);
            return new DeepLTranslationResponseImpl(false, "An error occurred while translating the content in " + targetLanguage);
        }
    }

    private Map<String, String> generateTranslations(TranslationData data, String srcLanguage, String destLanguage) {
        if (translator == null) {
            return null;
        }
        if (!data.hasTextToTranslate()) {
            return null;
        }

        final String destDeepLLanguage = targetLanguages.getOrDefault(destLanguage, destLanguage);
        final Map<String, String> texts = data.getTexts();
        final int nbTexts = texts.size();
        final List<String> keys = new ArrayList<>(nbTexts);
        final List<String> srcTexts = new ArrayList<>(nbTexts);
        texts.forEach((k, v) -> {
            keys.add(k);
            srcTexts.add(v);
        });
        final List<TextResult> results;
        try {
            TextTranslationOptions textTranslationOptions = new TextTranslationOptions();
            textTranslationOptions.setTagHandling("html");
            results = translator.translateText(srcTexts, srcLanguage, destDeepLLanguage, textTranslationOptions);
        } catch (DeepLException | InterruptedException e) {
            logger.error("Failed to translate content", e);
            return null;
        }

        final Map<String, String> translations = IntStream.range(0, nbTexts)
                .boxed()
                .collect(Collectors.toMap(keys::get, i -> results.get(i).getText()));
        return data.completeTranslations(translations);
    }

    private static boolean saveTranslations(JCRSessionWrapper session, Map<String, String> translations) {
        final AtomicBoolean hasSavedSomething = new AtomicBoolean(false);
        translations.forEach((path, value) -> {
            try {
                final JCRNodeWrapper node = session.getNode(StringUtils.substringBeforeLast(path, SLASH));
                final String propertyName = StringUtils.substringAfterLast(path, SLASH);
                if (node.hasProperty(propertyName) && StringUtils.equals(node.getPropertyAsString(propertyName), value)) {
                    logger.debug("{} is already translated", path);
                } else {
                    node.setProperty(propertyName, value);
                    hasSavedSomething.set(true);
                }
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        });

        try {
            session.save();
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return hasSavedSomething.get();
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

        return definition.isInternationalized()
                && !definition.isMultiple()
                && definition.getRequiredType() == PropertyType.STRING
                && !definition.isHidden()
                && !definition.isProtected();
    }
}
