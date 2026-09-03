package org.jahia.community.translation.assisted.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.utils.collections.MapToDictionary;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.PublicationInfo;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.SelectorType;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;

@Component(configurationPid = SERVICE_CONFIG_FILE_NAME, immediate = true, service = TranslationServicesManager.class)
public class TranslationServicesManagerImpl implements TranslationServicesManager {
    private static final Logger logger = LoggerFactory.getLogger(TranslationServicesManagerImpl.class);
    private static final Pattern VALUE_IDX_IN_FIELD = Pattern.compile("(.*)___(\\d+)___$");
    private  List<String> translatableNodeTypes;
    private List<String> translatableNodeTypesForSubtree;
    private ConfigurationAdmin configurationAdmin;
    private JCRPublicationService publicationService;
    private Map<String, String> targetLanguages;

    @Reference
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Reference
    public void setPublicationService(JCRPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @Activate
    protected void activate(Map<String, String> properties) {
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }
        final String deeplAIKey = properties.getOrDefault(DEEPL_API_KEY, "");
        dropLegacyOpenAiConfiguration(properties);
        validateDeepLKey(deeplAIKey);
        targetLanguages = transformTargetLanguagesPropertiesToMap(properties);
        String nodetypes = properties.getOrDefault("translation.nodetypes","jnt:page,jmix:mainResource,jmix:editorialContent,jnt:content,jnt:category");
        translatableNodeTypes = Arrays.asList(nodetypes.split(","));
        logger.info("Found translation nodetypes: {}", translatableNodeTypes.stream().map(StringUtils::trimToEmpty).collect(Collectors.joining(", ")));
        String treeNodetypes = properties.getOrDefault("translation.subtree.nodetypes","jnt:category");
        translatableNodeTypesForSubtree = Arrays.asList(treeNodetypes.split(","));
        configureDeepL(deeplAIKey);
    }

    @Override
    public Map<String, String> getTargetLanguages() {
        return targetLanguages;
    }

    private Map<String, String> transformTargetLanguagesPropertiesToMap(Map<String, String> properties) {
        Map<String, String> map = new HashMap<>();
        properties.entrySet().stream().filter(e -> e.getKey().startsWith(PROP_PREFIX_TARGET_LANGUAGES)).forEach(e -> map.put(e.getKey().substring(PROP_PREFIX_TARGET_LANGUAGES.length()), e.getValue()));
        return map;
    }

    @Override
    public void buildDataToTranslate(JCRNodeWrapper node, TranslationData data, boolean forceTranslation, boolean subtree) throws RepositoryException {
        if (subtree) {
            Set<String> languages = new HashSet<>();
            languages.add(node.getSession().getLocale().toString());
            // Use publication service to get the list of nodes part of this node publication
            Set<String> uuids = new HashSet<>();
            // in case of nodetypes like jnt:category we need to get the whole subtree
            boolean allsubtree = translatableNodeTypesForSubtree.stream().anyMatch(type -> {
                try {
                    return node.isNodeType(type);
                } catch (RepositoryException e) {
                    throw new RuntimeException(e);
                }
            });
            List<PublicationInfo> publicationInfo = publicationService.getPublicationInfo(node.getIdentifier(), languages, false, true, allsubtree, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE);
            publicationInfo.forEach(p -> uuids.addAll(p.getAllUuids(false, true)));

            JCRSessionWrapper session = node.getSession();
            for (String uuid : uuids) {
                try {
                    JCRNodeWrapper relatedNode = session.getNodeByIdentifier(uuid);
                    if (isTranslatableNode(relatedNode)) {
                        translatePropertiesOfNode(relatedNode, data);
                    }
                } catch (RepositoryException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("Could not find node", e);
                    }
                }
            }
        } else if (isTranslatableNode(node)) {
            translatePropertiesOfNode(node, data);
        }
    }

    private void translatePropertiesOfNode(JCRNodeWrapper relatedNode, TranslationData data) throws RepositoryException {
        final PropertyIterator properties;
        try {
            properties = relatedNode.getProperties();
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error reading properties for translation", e);
            }
            return;
        }
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            analyzeProperty(relatedNode, data, property);
        }
    }

    @Override
    public void buildDataToTranslate(JCRNodeWrapper node, String propertyName, TranslationData data) throws RepositoryException {
        if (!isTranslatableNode(node)) {
            return;
        }
        if (node.hasProperty(propertyName)) {
            analyzeProperty(node, data, node.getProperty(propertyName));
        }
    }

    @Override
    public Map<String, TranslatedField> getTranslatedFieldMap(List<TranslatedField> translatedFields) {
        Map<String, TranslatedField> translatedFieldMap = new HashMap<>();
        translatedFields.forEach(field -> {
            Matcher matcher = VALUE_IDX_IN_FIELD.matcher(field.getFieldName());
            if (matcher.matches()) {
                String fieldName = matcher.group(1);
                int index = Integer.parseInt(matcher.group(2));
                if (translatedFieldMap.containsKey(fieldName)) {
                    translatedFieldMap.get(fieldName).addTranslatedValue(field.getTranslatedValue(), index);
                } else {
                    TranslatedField translatedField = new TranslatedField(fieldName, new ArrayList<>());
                    translatedField.addTranslatedValue(field.getTranslatedValue(), index);
                    translatedFieldMap.put(fieldName, translatedField);
                }
            } else {
                translatedFieldMap.put(field.getFieldName(), field);
            }
        });
        return translatedFieldMap;
    }

    @Override
    public List<TranslatedField> getTranslatedFieldList(TranslationData data, boolean subtree, Map<String, String> translatedValues) {
        return data.completeTranslations(translatedValues).entrySet().stream()
                .map(e -> new TranslatedField((subtree ? e.getKey() : StringUtils.substringAfterLast(e.getKey(), SLASH)), e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void copyFieldValue(TranslatedField field, JCRSessionWrapper sessionTarget) {
        try {
            String path = field.getFieldName();
            final JCRNodeWrapper targetNode = sessionTarget.getNode(StringUtils.substringBeforeLast(path, SLASH));
            final String propertyName = StringUtils.substringAfterLast(path, SLASH);
            if (field.getTranslatedValues() != null) {
                targetNode.setProperty(propertyName, field.getTranslatedValues().toArray(new String[0]));
            } else if (targetNode.hasProperty(propertyName) && StringUtils.equals(targetNode.getPropertyAsString(propertyName), field.getTranslatedValue())) {
                logger.warn("{} is already translated", path);
            } else {
                targetNode.setProperty(propertyName, field.getTranslatedValue());
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error while copying field", e);
            }
        }
    }

    private void analyzeProperty(JCRNodeWrapper node, TranslationData data, Property property) throws RepositoryException {
        int valueIdx = 0;
        switch (getPropertyAction(property)) {
            case TRANSLATE:
                trackProperty(property, node, data::trackText);
                break;
            case TRANSLATE_ARRAY:
                for (Value value : property.getValues()) {
                    trackProperty(property, node, value.getString(), valueIdx++, data::trackText);
                }
                break;
            case COPY:
                trackProperty(property, node, data::trackCopiedValue);
                break;
            case COPY_ARRAY:
                for (Value value : property.getValues()) {
                    trackProperty(property, node, value.getString(), valueIdx++, data::trackCopiedValue);
                }
                break;
            case IGNORE:
        }
    }

    private boolean isTranslatableNode(JCRNodeWrapper node) {
        try {
            // Skip the node if it has no I18N node in this language
            if (node.hasI18N(node.getSession().getLocale())) {
                // The node is translated let's see if we accept the type
                return translatableNodeTypes.stream().anyMatch(type -> {
                    try {
                        return node.isNodeType(type);
                    } catch (RepositoryException e) {
                        if (logger.isErrorEnabled()) {
                            logger.error("Error while looking for node matching type", e);
                        }
                        throw new JahiaRuntimeException(e);
                    }
                });
            }
        } catch (RepositoryException e) {
            throw new JahiaRuntimeException(e);
        }
        return false;
    }

    private PropertyAction getPropertyAction(Property property) {
        final ExtendedPropertyDefinition definition;
        try {
            definition = (ExtendedPropertyDefinition) property.getDefinition();
        } catch (RepositoryException e) {
            logger.error("No definition found", e);
            return PropertyAction.IGNORE;
        }

        if (!definition.isInternationalized()
                || definition.isHidden()
                || definition.isProtected()) {
            return PropertyAction.IGNORE;
        }

        // If multiple I18N String with selector in SMALL_TEXT, TEXT_AREA, RICH_TEXT return PropertyAction.TRANSLATE_ARRAY
        if ((definition.getRequiredType() == PropertyType.STRING)
                && (definition.getSelector() == SelectorType.SMALLTEXT
                || definition.getSelector() == SelectorType.TEXTAREA
                || definition.getSelector() == SelectorType.RICHTEXT)) {
            return definition.isMultiple() ? PropertyAction.TRANSLATE_ARRAY : PropertyAction.TRANSLATE;
        }

        // Any other I18N String should be copied
        if (definition.getRequiredType() == PropertyType.STRING || definition.getRequiredType() == PropertyType.WEAKREFERENCE) {
            return definition.isMultiple() ? PropertyAction.COPY_ARRAY : PropertyAction.COPY;
        }

        return PropertyAction.IGNORE;
    }

    private void trackProperty(Property property, JCRNodeWrapper node, BiConsumer<String, String> tracker) {
        try {
            trackProperty(property, node, property.getValue().getString(), -1, tracker);
        } catch (RepositoryException e) {
            logger.error("Error while tracking property", e);
        }
    }

    private void trackProperty(Property property, JCRNodeWrapper node, String value, int index, BiConsumer<String, String> tracker) {
        try {
            final String key = index >= 0
                    ? String.format("%s/%s___%d___", node.getPath(), property.getName(), index)
                    : String.format("%s/%s", node.getPath(), property.getName());
            final String stringValue = StringUtils.trimToNull(value);
            if (stringValue != null) tracker.accept(key, stringValue);
        } catch (RepositoryException e) {
            logger.error("Error while tracking property", e);
        }
    }

    private void configureDeepL(String deeplAIKey) {
        if (StringUtils.isNotEmpty(deeplAIKey)) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_DEEPL);
                Map<String, String> configProperties = new HashMap<>();
                configProperties.put(DEEPL_API_KEY, deeplAIKey);
                configuration.updateIfDifferent(new MapToDictionary(configProperties));

            } catch (IOException e) {
                throw new JahiaRuntimeException(e);
            }
        }
    }

    // Private configuration methods

    private void validateDeepLKey(String deeplAIKey) {
        // DeepL is gated by ConfigurationPolicy.REQUIRE on its own PID: deleting that configuration
        // when no key is set is what keeps its component from registering.
        try {
            if (StringUtils.isEmpty(deeplAIKey)) {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_DEEPL);
                if (configuration != null) {
                    configuration.delete();
                }
            }

        } catch (IOException e) {
            logger.error("Error while deleting configuration for DeepL translator service: {}", e.getMessage());
        }
        if (StringUtils.isEmpty(deeplAIKey)) {
            logger.info("No DeepL API key provided. Translations will only be available if a provider is configured "
                    + "in the genai-connector module.");
        } else {
            logger.info("API key provided for the DeepL translator service.");
        }
    }

    /**
     * Handles configurations written before the LLM path moved to the genai-connector module
     * (jahia-private#5366): the OpenAI API key is no longer used here, and the secondary
     * configuration it used to be pushed into no longer backs any component.
     */
    private void dropLegacyOpenAiConfiguration(Map<String, String> properties) {
        if (StringUtils.isNotEmpty(properties.get(LEGACY_OPENAI_API_KEY))) {
            logger.warn("The key {} found in {} is IGNORED since the migration to the genai-connector module: "
                            + "configure the provider, its API key and its model in org.jahia.modules.genai.cfg instead.",
                    LEGACY_OPENAI_API_KEY, SERVICE_CONFIG_FILE_FULLNAME);
        }
        try {
            // listConfigurations, not getConfiguration: the latter would create the very
            // configuration we are trying to get rid of when it is already gone.
            Configuration[] configurations = configurationAdmin.listConfigurations(
                    "(service.pid=" + LEGACY_OPENAI_CONFIG_FILE_NAME + ")");
            if (configurations == null) {
                return;
            }
            for (Configuration configuration : configurations) {
                configuration.delete();
            }
            logger.info("Deleted the obsolete {} configuration left over by an earlier version, "
                    + "along with the API key it held.", LEGACY_OPENAI_CONFIG_FILE_NAME);
        } catch (IOException | InvalidSyntaxException e) {
            logger.error("Error while deleting the obsolete configuration {}: {}", LEGACY_OPENAI_CONFIG_FILE_NAME, e.getMessage());
        }
    }

    private enum PropertyAction {TRANSLATE, TRANSLATE_ARRAY, COPY, COPY_ARRAY, IGNORE}

}
