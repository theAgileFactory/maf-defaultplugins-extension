package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import dao.pmo.ActorDao;
import dao.pmo.OrgUnitDao;
import framework.security.ISecurityService;
import framework.services.account.IPreferenceManagerPlugin;
import framework.services.account.IUserAccount;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebCommandPath;
import framework.services.ext.api.WebControllerPath;
import framework.services.ext.api.WebParameter;
import framework.services.plugins.api.WidgetController;
import framework.utils.Pagination;
import framework.utils.Table;
import models.framework_models.plugin.DashboardWidgetColor;
import models.pmo.Actor;
import models.pmo.OrgUnit;
import play.Logger;
import play.Play;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import services.tableprovider.ITableProvider;
import utils.table.ActorListView;
import utils.table.OrgUnitListView;

/**
 * Widget which displays the active subordinates and org units of the current
 * sign-in user.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/my-staff")
public class MyStaffWidgetController extends WidgetController {

    private static Logger.ALogger log = Logger.of(MyStaffWidgetController.class);

    private ISecurityService securityService;

    private IPreferenceManagerPlugin preferenceManagerPlugin;

    @Inject
    public MyStaffWidgetController(ILinkGenerationService linkGenerationService, ISecurityService securityService, II18nMessagesPlugin i18nMessagePlugin,
            IPreferenceManagerPlugin preferenceManagerPlugin) {
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
                                getI18nMessagePlugin().get("plugin.widget_kit.my_staff.title")));
                    }
                    Long actorId = actor.id;

                    MyStaffData myStaffData = getTables(actorId, 0, 0);

                    return ok(views.html.plugins.system.widgetkit1.my_staff_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                            tempLinkGenerator, "subordinates", myStaffData.subordinatesTable, myStaffData.subordinatesPagination, myStaffData.orgUnitsTable,
                            myStaffData.orgUnitsPagination));
                } catch (Exception e) {
                    log.error("Error while displaying the staff of the user", e);
                    return displayErrorWidget(widgetId);
                }
            }
        });

    }

    @WebCommandPath(path = "/paginate/:id", id = "my-staff-paginate")
    public Promise<Result> paginate(@WebParameter(name = "id") Long id) {

        // get the query params
        Integer subordinatesPage = getQueryParamAsPage("subordinatesPage");
        Integer orgUnitsPage = getQueryParamAsPage("orgUnitsPage");
        String tab = ctx().request().getQueryString("tab");

        final ILinkGenerator tempLinkGenerator = this;

        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {

                    // get the current actor
                    IUserAccount userAccount = getSecurityService().getCurrentUser();
                    Actor actor = ActorDao.getActorByUid(userAccount.getUid());
                    Long actorId = actor.id;

                    MyStaffData myStaffData = getTables(actorId, subordinatesPage, orgUnitsPage);

                    return ok(views.html.plugins.system.widgetkit1.my_staff_fragment_widget.render(id, tempLinkGenerator, tab, myStaffData.subordinatesTable,
                            myStaffData.subordinatesPagination, myStaffData.orgUnitsTable, myStaffData.orgUnitsPagination));
                } catch (Exception e) {
                    log.error("Error while displaying the staff of the user", e);
                    return displayErrorWidget(id);
                }
            }
        });

    }

    private MyStaffData getTables(Long actorId, Integer subordinatesPage, Integer orgUnitsPage) {

        Set<String> columnsToHide = new HashSet<String>();
        columnsToHide.add("manager");
        columnsToHide.add("isActive");

        /**
         * get the subordinates for which the current user is the manager
         */

        Pagination<Actor> subordinatesPagination = ActorDao.getActorActiveAsPaginationByManager(this.getPreferenceManagerPlugin(), actorId);
        subordinatesPagination.setPageQueryName("subordinatesPage");
        subordinatesPagination.setCurrentPage(subordinatesPage);

        List<ActorListView> actorsListView = new ArrayList<ActorListView>();
        for (Actor a : subordinatesPagination.getListOfObjects()) {
            actorsListView.add(new ActorListView(a));
        }

        Table<ActorListView> subordinatesTable = this.getTableProvider().get().actor.templateTable.fill(actorsListView, columnsToHide);

        /**
         * get the org units for which the current user is the manager
         */

        Pagination<OrgUnit> orgUnitsPagination = OrgUnitDao.getOrgUnitAsPaginationByActor(this.getPreferenceManagerPlugin(), actorId, false);
        orgUnitsPagination.setPageQueryName("orgUnitsPage");
        orgUnitsPagination.setCurrentPage(orgUnitsPage);

        List<OrgUnitListView> orgUnitListView = new ArrayList<OrgUnitListView>();
        for (OrgUnit orgUnit : orgUnitsPagination.getListOfObjects()) {
            orgUnitListView.add(new OrgUnitListView(orgUnit));
        }

        Table<OrgUnitListView> orgUnitsTable = this.getTableProvider().get().orgUnit.templateTable.fill(orgUnitListView, columnsToHide);

        return new MyStaffData(subordinatesTable, subordinatesPagination, orgUnitsTable, orgUnitsPagination);

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

    /**
     * Get the corresponding page number according to a query param.
     * 
     * @param name
     *            the query param name
     */
    private int getQueryParamAsPage(String name) {

        String valueAsString = ctx().request().getQueryString(name);
        if (valueAsString != null && !valueAsString.equals("")) {
            try {
                return Integer.parseInt(valueAsString);
            } catch (Exception e) {
                Logger.warn("impossible to convert the string '" + valueAsString + "' to an integer", e);
            }
        }

        return 0;

    }

    /**
     * The data of MyStaff widget (tables and paginations objects).
     * 
     * @author Johann Kohler
     *
     */
    private static class MyStaffData {

        public Table<ActorListView> subordinatesTable;
        public Pagination<Actor> subordinatesPagination;
        public Table<OrgUnitListView> orgUnitsTable;
        public Pagination<OrgUnit> orgUnitsPagination;

        /**
         * Default constructor.
         * 
         * @param subordinatesTable
         *            the subordinates table
         * @param subordinatesPagination
         *            the subordinates pagination object
         * @param orgUnitsTable
         *            the org units table
         * @param orgUnitsPagination
         *            the org units pagination object
         */
        public MyStaffData(Table<ActorListView> subordinatesTable, Pagination<Actor> subordinatesPagination, Table<OrgUnitListView> orgUnitsTable,
                Pagination<OrgUnit> orgUnitsPagination) {
            this.subordinatesTable = subordinatesTable;
            this.subordinatesPagination = subordinatesPagination;
            this.orgUnitsTable = orgUnitsTable;
            this.orgUnitsPagination = orgUnitsPagination;
        }

    }

}
