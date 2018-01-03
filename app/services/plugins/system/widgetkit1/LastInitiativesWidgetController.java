package services.plugins.system.widgetkit1;

import java.util.List;

import javax.inject.Inject;

import com.avaje.ebean.OrderBy;

import framework.security.ISecurityService;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebControllerPath;
import framework.services.plugins.api.WidgetController;
import models.framework_models.plugin.DashboardWidgetColor;
import models.pmo.PortfolioEntry;
import play.Logger;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import security.dynamic.PortfolioEntryDynamicHelper;

/**
 * Widget which displays last created initiatives.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/last-initiatives")
public class LastInitiativesWidgetController extends WidgetController {
    private static Logger.ALogger log = Logger.of(LastInitiativesWidgetController.class);

    private ISecurityService securityService;

    @Inject
    public LastInitiativesWidgetController(ILinkGenerationService linkGenerationService, ISecurityService securityService,
            II18nMessagesPlugin i18nMessagePlugin) {
        super(linkGenerationService, i18nMessagePlugin);
        this.securityService = securityService;
    }

    @Override
    public Promise<Result> display(Long widgetId) {
        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(() -> {

            List<PortfolioEntry> portfolioEntries;
            OrderBy<PortfolioEntry> orderBy = new OrderBy<PortfolioEntry>();
            orderBy.desc("creationDate");
            orderBy.desc("id");
            try {
                portfolioEntries = PortfolioEntryDynamicHelper.getPortfolioEntriesViewAllowedAsQuery(getSecurityService()).setMaxRows(5)
                        .setOrderBy(orderBy)
                        .findList();
                return ok(views.html.plugins.system.widgetkit1.last_initiatives_widget.render(widgetId, DashboardWidgetColor.DANGER.getColor(),
                        tempLinkGenerator, portfolioEntries));

            } catch (Exception e) {
                log.error("Error while displaying the last initiatives widget", e);
                return displayErrorWidget(widgetId);
            }

        });
    }

    private ISecurityService getSecurityService() {
        return securityService;
    }

}
