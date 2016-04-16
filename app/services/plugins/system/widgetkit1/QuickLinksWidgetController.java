package services.plugins.system.widgetkit1;

import javax.inject.Inject;

import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebControllerPath;
import framework.services.plugins.api.WidgetController;
import models.framework_models.plugin.DashboardWidgetColor;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;

/**
 * Widget which displays quick links to the BizDock app.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/quick-links")
public class QuickLinksWidgetController extends WidgetController {

    @Inject
    public QuickLinksWidgetController(ILinkGenerationService linkGenerationService, II18nMessagesPlugin i18nMessagePlugin) {
        super(linkGenerationService, i18nMessagePlugin);
    }

    @Override
    public Promise<Result> display(Long widgetId) {
        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {

                return ok(views.html.plugins.system.widgetkit1.quick_links_widget.render(widgetId, DashboardWidgetColor.INFO.getColor(), tempLinkGenerator));

            }
        });
    }

}
