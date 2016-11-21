package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebControllerPath;
import framework.services.plugins.api.WidgetController;
import framework.utils.Table;
import models.framework_models.plugin.DashboardWidgetColor;
import models.governance.PlannedLifeCycleMilestoneInstance;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import utils.table.PortfolioMilestoneListView;
import controllers.core.PortfolioController;
import dao.governance.LifeCyclePlanningDao;
/**
 * Widget which displays late planned milestones.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/LatePlannedMilestones")
public class LatePlannedMilestonesController extends WidgetController {

    @Inject
    public LatePlannedMilestonesController(ILinkGenerationService linkGenerationService, II18nMessagesPlugin i18nMessagePlugin) {
        super(linkGenerationService, i18nMessagePlugin);
    }

    @Override
    public Promise<Result> display(Long widgetId) {
        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
            	
            	Long id = 1;
            	// get the late milestones
                List<PortfolioMilestoneListView> portfolioMilestoneListView = new ArrayList<PortfolioMilestoneListView>();
                for (PlannedLifeCycleMilestoneInstance plannedMilestoneInstance : LifeCyclePlanningDao
                        .getPlannedLCMilestoneInstanceNotApprovedAsListOfPortfolio(id)) {
                    portfolioMilestoneListView.add(new PortfolioMilestoneListView(plannedMilestoneInstance));
                }
                Table<PortfolioMilestoneListView> filledMilestoneTable = this.getTableProvider().get().portfolioMilestone.templateTable
                        .fill(portfolioMilestoneListView);
                
                return ok(views.html.plugins.system.widgetkit1.late_planned_milestones_widget.render(widgetId, DashboardWidgetColor.INFO.getColor(), tempLinkGenerator));

            }
        });
    }

}
