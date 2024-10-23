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
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = Action.class, immediate = true)
public class RequestTranslationAction extends Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTranslationAction.class);

    @Activate
    public void activate() {
        setName("requestTranslationAction");
        setRequireAuthenticatedUser(true);
        setRequiredPermission("jcr:write_default");
        setRequiredWorkspace("default");
        setRequiredMethods("GET,POST");
    }

    private DeepLTranslatorService deepLTranslatorService;

    @Reference(service = DeepLTranslatorService.class)
    public void setDeepLTranslatorService(DeepLTranslatorService deepLTranslatorService) {
        this.deepLTranslatorService = deepLTranslatorService;
    }

    public DeepLTranslatorService getDeepLTranslatorService() {
        return deepLTranslatorService;
    }

    @Override
    public ActionResult doExecute(final HttpServletRequest request, final RenderContext renderContext, final Resource resource, final JCRSessionWrapper session, Map<String, List<String>> parameters, final URLResolver urlResolver) throws Exception {
        JSONObject resp = new JSONObject();
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

        String currentLanguage = "";
        if (parameters.containsKey(DeeplConstants.PROP_SRC_LANGUAGE) && !parameters.get(DeeplConstants.PROP_SRC_LANGUAGE).isEmpty()) {
            currentLanguage = parameters.get(DeeplConstants.PROP_SRC_LANGUAGE).get(0);
        } else {
            currentLanguage = resource.getLocale().getLanguage();
        }

        boolean threeDotsMenu = false;
        if (parameters.containsKey(DeeplConstants.PROP_3DOTSMENU) && !parameters.get(DeeplConstants.PROP_3DOTSMENU).isEmpty()) {
            threeDotsMenu = Boolean.valueOf(parameters.get(DeeplConstants.PROP_3DOTSMENU).get(0));
        }

        final List<Locale> locales = renderContext.getSite().getLanguagesAsLocales();
        
        try {
            resp = translate(locales, resource.getNode(), currentLanguage, allLanguages, subTree, destLanguage, threeDotsMenu);
            if (resp != null) {
                resultCode = HttpServletResponse.SC_OK;
            }
        } catch (Exception e) {
            LOGGER.error("Translation error: ", e);
            resp.put("error", e.getMessage());
        }

        LOGGER.info(resp.toString());
        return new ActionResult(resultCode, null, resp);
    }

    private JSONObject translate(List<Locale> locales, JCRNodeWrapper node, String currentLanguage, boolean allLanguages, boolean subTree, String destLanguage, boolean threeDotsMenu) throws RepositoryException, JSONException {
        JSONObject resp = new JSONObject();

        if (allLanguages) {
            for (Locale locale : locales) {
                final String language = locale.getLanguage();
                if (!currentLanguage.equals(language)) {
                    resp.put(language, deepLTranslatorService.translate(node.getPath(), currentLanguage, language, threeDotsMenu));
                }
            }
        } else {
            resp.put("properties", deepLTranslatorService.translate(node.getPath(), currentLanguage, destLanguage, threeDotsMenu));
        }

        if (subTree) {
            final JCRNodeIteratorWrapper iterator = node.getNodes();
            while (iterator.hasNext()) {
                final JCRNodeWrapper childNode = (JCRNodeWrapper) iterator.next();
                JSONObject childResp = translate(locales, childNode, currentLanguage, allLanguages, subTree, destLanguage, threeDotsMenu);
                resp.put(childNode.getName(), childResp);
            }
        }

        resp.put("resultCode", HttpServletResponse.SC_OK);
        return resp;
    }
}
