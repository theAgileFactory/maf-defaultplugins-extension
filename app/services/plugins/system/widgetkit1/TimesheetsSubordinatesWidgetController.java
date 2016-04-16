package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import dao.pmo.ActorDao;
import dao.timesheet.TimesheetDao;
import framework.security.ISecurityService;
import framework.services.account.IPreferenceManagerPlugin;
import framework.services.account.IUserAccount;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebControllerPath;
import framework.services.plugins.api.WidgetController;
import framework.utils.Table;
import models.framework_models.plugin.DashboardWidgetColor;
import models.pmo.Actor;
import models.timesheet.TimesheetReport;
import play.Logger;
import play.Play;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import services.tableprovider.ITableProvider;
import utils.table.TimesheetReportListView;

/**
 * Widget which displays the timesheets to review and the late timesheets of the
 * suborinates of the sign-in user.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/timesheets-subordinates")
public class TimesheetsSubordinatesWidgetController extends WidgetController {

    private static Logger.ALogger log = Logger.of(TimesheetsSubordinatesWidgetController.class);

    private ISecurityService securityService;

    private IPreferenceManagerPlugin preferenceManagerPlugin;

    @Inject
    public TimesheetsSubordinatesWidgetController(ILinkGenerationService linkGenerationService, ISecurityService securityService,
            II18nMessagesPlugin i18nMessagePlugin, IPreferenceManagerPlugin preferenceManagerPlugin) {
        super(linkGenerationService, i18nMessagePlugin);
        this.securityService = securityService;
        this.preferenceManagerPlugin = preferenceManagerPlugin;
    }

    @Override
    public Promise<Result> display(Long widgetId) {

        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {

                    // get the current actor
                    IUserAccount userAccount = getSecurityService().getCurrentUser();
                    Actor actor = ActorDao.getActorByUid(userAccount.getUid());
                    if (actor == null) {
                        return ok(views.html.plugins.system.widgetkit1.no_actor.render(widgetId, DashboardWidgetColor.WARNING.getColor(), tempLinkGenerator,
                                getI18nMessagePlugin().get("plugin.widget_kit.timesheets_subordinates.title")));
                    }
                    Long actorId = actor.id;

                    // create missing timesheet reports
                    List<Actor> actors = ActorDao.getActorAsListByManager(actorId);
                    for (Actor a : actors) {
                        TimesheetDao.createMissingTimesheetReport(TimesheetReport.Type.WEEKLY, a, getPreferenceManagerPlugin());
                    }

                    /**
                     * Construct the submitted timesheets table (only if the
                     * timeshee should be approved).
                     */

                    Table<TimesheetReportListView> submittedReportsTable = null;
                    if (TimesheetDao.getTimesheetReportMustApprove(getPreferenceManagerPlugin())) {

                        List<TimesheetReport> submittedReports = TimesheetDao.getTimesheetReportSubmittedAsListByManager(actorId);

                        List<TimesheetReportListView> submittedReportListView = new ArrayList<TimesheetReportListView>();
                        for (TimesheetReport r : submittedReports) {
                            submittedReportListView.add(new TimesheetReportListView(r));
                        }

                        Set<String> hideColumnsForSubmittedReports = new HashSet<String>();
                        hideColumnsForSubmittedReports.add("reminderActionLink");

                        submittedReportsTable = getTableProvider().get().timesheetReport.templateTable.fill(submittedReportListView,
                                hideColumnsForSubmittedReports);

                    }

                    /**
                     * Construct the late timesheets table
                     */

                    List<TimesheetReport> lateReports = TimesheetDao.getTimesheetReportLateAsListByManager(actorId, getPreferenceManagerPlugin());

                    List<TimesheetReportListView> lateReportListView = new ArrayList<TimesheetReportListView>();
                    for (TimesheetReport r : lateReports) {
                        lateReportListView.add(new TimesheetReportListView(r));
                    }

                    Set<String> hideColumnsForLateReports = new HashSet<String>();
                    hideColumnsForLateReports.add("approveActionLink");

                    Table<TimesheetReportListView> lateReportsTable = getTableProvider().get().timesheetReport.templateTable.fill(lateReportListView,
                            hideColumnsForLateReports);

                    return ok(views.html.plugins.system.widgetkit1.timesheets_subordinates_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                            tempLinkGenerator, "submitted", submittedReportsTable, lateReportsTable));

                } catch (Exception e) {
                    log.error("Error while displaying the timessgets of the subordinates of the user", e);
                    return displayErrorWidget(widgetId);
                }
            }
        });

    }

    /**
     * Get the security service.
     */
    private ISecurityService getSecurityService() {
        return securityService;
    }

    /**
     * Get the table provider.
     */
    private ITableProvider getTableProvider() {
        return Play.application().injector().instanceOf(ITableProvider.class);
    }

    /**
     * Get the preference manager service.
     */
    private IPreferenceManagerPlugin getPreferenceManagerPlugin() {
        return this.preferenceManagerPlugin;
    }

}
