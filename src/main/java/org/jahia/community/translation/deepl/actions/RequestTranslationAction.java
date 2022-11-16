package org.jahia.community.translation.deepl.actions;

import java.util.List;
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
        deepLTranslatorService.translate(resource.getNodePath(), resource.getLocale().getLanguage(), parameters.get("destLanguage").get(0));
        return new ActionResult(HttpServletResponse.SC_OK, null, resp);
    }

}
