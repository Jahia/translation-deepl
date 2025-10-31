package org.jahia.community.translation.deepl.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.translation.deepl.service.DeepLTranslationResponse;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNodeMutation;
import org.jahia.osgi.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@GraphQLTypeExtension(GqlJcrNodeMutation.class)
@GraphQLDescription("Entry point of the mutation for the DeepL GraphQL API")
public class GqlJcrNodeMutationDeepl {
    private static final Logger logger = LoggerFactory.getLogger(GqlJcrNodeMutationDeepl.class);


    private DeepLTranslatorService deepLTranslatorService;

    private final GqlJcrNodeMutation nodeMutation;

    public GqlJcrNodeMutationDeepl(GqlJcrNodeMutation nodeMutation) {
        this.nodeMutation = nodeMutation;
        this.deepLTranslatorService = BundleUtils.getOsgiService(DeepLTranslatorService.class, null);
    }

    @GraphQLField
    @GraphQLDescription("Translate node")
    public DeepLTranslationResponse translateNode(
            @GraphQLName("sourceLocale") @GraphQLDescription("Locale to translate from") String sourceLocale,
            @GraphQLName("targetLocale") @GraphQLDescription("Locale to translate to") String targetLocale
    ) throws InterruptedException {
        if(logger.isErrorEnabled()) {
            logger.error(String.format("Translating %s from %s to %s", nodeMutation.getNode().getPath(), sourceLocale, targetLocale));
        }
        try {
            return deepLTranslatorService.translateNode(nodeMutation.getNode().getNode(), sourceLocale, targetLocale);
        } catch (RepositoryException e) {
            if(logger.isErrorEnabled()) {
                logger.error("Error when translating");
            }
        }
        return null;
    }

    @GraphQLField
    @GraphQLDescription("Translate property")
    public DeepLTranslationResponse translateProperty(
            @GraphQLName("propertyName") @GraphQLDescription("Property name to translate") String propertyName,
            @GraphQLName("sourceLocale") @GraphQLDescription("Locale to translate from") String sourceLocale,
            @GraphQLName("targetLocale") @GraphQLDescription("Locale to translate to") String targetLocale

    ) throws InterruptedException {
        if(logger.isErrorEnabled()) {
            logger.error(String.format("Translating %s, property %s, from %s to %s", nodeMutation.getNode().getPath(), propertyName, sourceLocale, targetLocale));
        }
        try {
            return deepLTranslatorService.translateProperty(nodeMutation.getNode().getNode(), propertyName, sourceLocale, targetLocale);
        } catch (RepositoryException e) {
            if(logger.isErrorEnabled()) {
                logger.error("Error when translating");
            }
        }
        return null;
    }
}