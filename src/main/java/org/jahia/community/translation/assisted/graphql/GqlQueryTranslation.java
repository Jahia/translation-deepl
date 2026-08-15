package org.jahia.community.translation.assisted.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.TranslatorServiceResolver;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.List;


@GraphQLTypeExtension(GqlJcrNode.class)
@GraphQLDescription("Entry point of the query for the Translation GraphQL API")
public class GqlQueryTranslation {
    private static final Logger logger = LoggerFactory.getLogger(GqlQueryTranslation.class);
    private final GqlJcrNode node;

    public GqlQueryTranslation(GqlJcrNode node) {
        this.node = node;
    }

    @GraphQLField
    @GraphQLDescription("Translate node")
    public List<TranslatedField> translationSuggestions(
            @GraphQLName("sourceLanguage") @GraphQLDescription("Language to translate from") String sourceLocale,
            @GraphQLName("targetLanguage") @GraphQLDescription("Language to translate to") String targetLocale
    ) {
        try {
            // The resolver already filters on availability, so anything it returns is usable.
            TranslatorService translatorService = TranslatorServiceResolver.resolve().orElse(null);
            if (translatorService == null) {
                logger.warn("No TranslatorService available – no translation provider is configured");
                return Collections.emptyList();
            }
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Translating %s from %s to %s", node.getPath(), sourceLocale, targetLocale));
            }
            logger.info("Translating from {} to {} with provider {}", sourceLocale, targetLocale, translatorService.getProviderKey());
            return translatorService.suggestTranslationForNode(node.getNode(), sourceLocale, targetLocale);
        } catch (RepositoryException e) {
            throw new DataFetchingException("Error when suggesting translation", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataFetchingException("Translation interrupted", e);
        }
    }
}
