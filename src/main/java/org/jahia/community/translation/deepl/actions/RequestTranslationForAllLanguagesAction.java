package org.jahia.community.translation.deepl.actions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONObject;

public class RequestTranslationForAllLanguagesAction extends Action {

    private DeepLTranslatorService deepLTranslatorService;

    @Override
    public ActionResult doExecute(final HttpServletRequest request, final RenderContext renderContext, final Resource resource, final JCRSessionWrapper session, Map<String, List<String>> parameters, final URLResolver urlResolver) throws Exception {
        final JSONObject resp = new JSONObject();
        final String currentLanguage = resource.getLocale().getLanguage();
        final String path = resource.getNodePath();
        for (Locale locale : renderContext.getSite().getLanguagesAsLocales()) {
            final String destLanguage = locale.getLanguage();
            if (!currentLanguage.equals(destLanguage)) {
                deepLTranslatorService.translate(path, currentLanguage, destLanguage);
            }
        }
        return new ActionResult(HttpServletResponse.SC_OK, null, resp);
    }

    public void setDeepLTranslatorService(DeepLTranslatorService deepLTranslatorService) {
        this.deepLTranslatorService = deepLTranslatorService;
    }

    public DeepLTranslatorService getDeepLTranslatorService() {
        return deepLTranslatorService;
    }
}
