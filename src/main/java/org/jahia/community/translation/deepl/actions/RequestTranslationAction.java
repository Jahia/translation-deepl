package org.jahia.community.translation.deepl.actions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jahia.api.Constants;
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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = Action.class)
public class RequestTranslationAction extends Action {

    @Activate
    public void activate() {
        setName("requestTranslationAction");
        setRequireAuthenticatedUser(true);
        setRequiredPermission("jcr:write_default");
        setRequiredWorkspace(Constants.EDIT_WORKSPACE);
        setRequiredMethods("GET,POST");
    }

    @Reference
    private DeepLTranslatorService deepLTranslatorService;

    @Override
    public ActionResult doExecute(final HttpServletRequest request, final RenderContext renderContext, final Resource resource, final JCRSessionWrapper session, Map<String, List<String>> parameters, final URLResolver urlResolver) throws Exception {
        final JSONObject resp = new JSONObject();
        int resultCode = HttpServletResponse.SC_BAD_REQUEST;

        boolean allLanguages = false;
        if (parameters.containsKey(DeeplConstants.PROP_ALL_LANGUAGES) && !parameters.get(DeeplConstants.PROP_ALL_LANGUAGES).isEmpty()) {
            allLanguages = Boolean.valueOf(parameters.get(DeeplConstants.PROP_ALL_LANGUAGES).get(0));
        }
        boolean subTree = false;
        if (parameters.containsKey(DeeplConstants.PROP_SUB_TREE) && !parameters.get(DeeplConstants.PROP_SUB_TREE).isEmpty()) {
            subTree = Boolean.valueOf(parameters.get(DeeplConstants.PROP_SUB_TREE).get(0));
        }

        String destLanguage = "";
        if (parameters.containsKey(DeeplConstants.PROP_DEST_LANGUAGE) && !parameters.get(DeeplConstants.PROP_DEST_LANGUAGE).isEmpty()) {
            destLanguage = parameters.get(DeeplConstants.PROP_DEST_LANGUAGE).get(0);
        }

        final List<Locale> locales = renderContext.getSite().getLanguagesAsLocales();
        final String currentLanguage = resource.getLocale().getLanguage();

        resultCode = translate(locales, resource.getNode(), currentLanguage, allLanguages, subTree, destLanguage);

        return new ActionResult(resultCode, null, resp);
    }

    private int translate(List<Locale> locales, JCRNodeWrapper node, String currentLanguage, boolean allLanguages, boolean subTree, String destLanguage) throws RepositoryException {
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
        } else {
            deepLTranslatorService.translate(node.getPath(), currentLanguage, destLanguage);
            if (subTree) {
                final JCRNodeIteratorWrapper iterator = node.getNodes();
                while (iterator.hasNext()) {
                    final JCRNodeWrapper childNode = (JCRNodeWrapper) iterator.next();
                    translate(locales, childNode, currentLanguage, allLanguages, subTree, destLanguage);
                }
            }
        }
        return HttpServletResponse.SC_OK;
    }

}
