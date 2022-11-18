package org.jahia.community.translation.deepl.actions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.community.translation.deepl.DeeplConstants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONObject;

public class RequestTranslationAction extends Action {

    private DeepLTranslatorService deepLTranslatorService;

    public void setDeepLTranslatorService(DeepLTranslatorService deepLTranslatorService) {
        this.deepLTranslatorService = deepLTranslatorService;
    }

    public DeepLTranslatorService getDeepLTranslatorService() {
        return deepLTranslatorService;
    }

    @Override
    public ActionResult doExecute(final HttpServletRequest request, final RenderContext renderContext, final Resource resource, final JCRSessionWrapper session, Map<String, List<String>> parameters, final URLResolver urlResolver) throws Exception {
        final JSONObject resp = new JSONObject();
        int resultCode = HttpServletResponse.SC_BAD_REQUEST;

        boolean allLanguages = false;
        if (parameters.containsKey(DeeplConstants.PROP_ALL_LANGUAGES) && parameters.get(DeeplConstants.PROP_ALL_LANGUAGES).size() > 0) {
            allLanguages = Boolean.valueOf(parameters.get(DeeplConstants.PROP_ALL_LANGUAGES).get(0));
        }
        boolean subTree = false;
        if (parameters.containsKey(DeeplConstants.PROP_SUB_TREE) && parameters.get(DeeplConstants.PROP_SUB_TREE).size() > 0) {
            subTree = Boolean.valueOf(parameters.get(DeeplConstants.PROP_SUB_TREE).get(0));
        }

        String destLanguage = "";
        if (parameters.containsKey(DeeplConstants.PROP_DEST_LANGUAGE) && parameters.get(DeeplConstants.PROP_DEST_LANGUAGE).size() > 0) {
            destLanguage = parameters.get(DeeplConstants.PROP_DEST_LANGUAGE).get(0);
        }

        final List<Locale> locales = renderContext.getSite().getLanguagesAsLocales();
        final String currentLanguage = resource.getLocale().getLanguage();

        resultCode = translate(locales, resource.getNode(), currentLanguage, allLanguages, subTree, destLanguage);

        return new ActionResult(resultCode, null, resp);
    }

    private int translate(List<Locale> locales, JCRNodeWrapper node, String currentLanguage, boolean allLanguages, boolean subTree, String destLanguage) throws RepositoryException {
        int resultCode = HttpServletResponse.SC_BAD_REQUEST;
        if (allLanguages) {
            for (Locale locale : locales) {
                final String language = locale.getLanguage();
                if (!currentLanguage.equals(language)) {
                    deepLTranslatorService.translate(node.getPath(), currentLanguage, language);
                }
            }
            if (subTree) {
                final JCRNodeIteratorWrapper iterator = node.getNodes();
                while (iterator.hasNext()) {
                    final JCRNodeWrapper childNode = (JCRNodeWrapper) iterator.next();
                    translate(locales, childNode, currentLanguage, allLanguages, subTree, destLanguage);
                }
            }
            resultCode = HttpServletResponse.SC_OK;
        } else {
            deepLTranslatorService.translate(node.getPath(), currentLanguage, destLanguage);
            if (subTree) {
                final JCRNodeIteratorWrapper iterator = node.getNodes();
                while (iterator.hasNext()) {
                    final JCRNodeWrapper childNode = (JCRNodeWrapper) iterator.next();
                    translate(locales, childNode, currentLanguage, allLanguages, subTree, destLanguage);
                }
            }
            resultCode = HttpServletResponse.SC_OK;
        }
        return resultCode;
    }

}
