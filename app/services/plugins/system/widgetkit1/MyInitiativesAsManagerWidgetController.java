package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dao.pmo.ActorDao;
import dao.pmo.PortfolioEntryDao;
import dao.pmo.StakeholderDao;
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
import models.pmo.PortfolioEntry;
import play.Configuration;
import play.Logger;
import play.Play;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import services.tableprovider.ITableProvider;
import utils.table.PortfolioEntryListView;

/**
 * Widget which displays the active initiatives (as manager and as stakeholder)
 * of the current sign-in user.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/my-initiativesAsManager")
public class MyInitiativesAsManagerWidgetController extends WidgetController {

    private static Logger.ALogger log = Logger.of(MyInitiativesWidgetController.class);

    private ISecurityService securityService;

    private IPreferenceManagerPlugin preferenceManagerPlugin;

    private Configuration configuration;

    @Inject
    public MyInitiativesAsManagerWidgetController(Configuration configuration, ILinkGenerationService linkGenerationService, ISecurityService securityService,
            II18nMessagesPlugin i18nMessagePlugin, IPreferenceManagerPlugin preferenceManagerPlugin) {
        super(linkGenerationService, i18nMessagePlugin);
        this.configuration = configuration;
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
                                getI18nMessagePlugin().get("plugin.widget_kit.my_initiatives_as_manager.title")));
                    }
                    Long actorId = actor.id;

                    MyInitiativesData myInitiativesData = getTables(actorId, 0);

                    return ok(views.html.plugins.system.widgetkit1.my_initiatives_as_manager_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                            tempLinkGenerator, "as-manager", myInitiativesData.asManagerTable, myInitiativesData.asManagerPagination));
                } catch (Exception e) {
                    log.error("Error while displaying the initiatives as mananger of the user", e);
                    return displayErrorWidget(widgetId);
                }
            }
        });

    }

    @WebCommandPath(path = "/paginate/:id", id = "my-initiatives-paginate")
    public Promise<Result> paginate(@WebParameter(name = "id") Long id) {

        // get the query params
        Integer asManagerPage = getQueryParamAsPage("asManagerPage");

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

                    MyInitiativesData myInitiativesData = getTables(actorId, asManagerPage);

                    return ok(views.html.plugins.system.widgetkit1.my_initiatives_as_manager_fragment_widget.render(id, tempLinkGenerator, tab,
                            myInitiativesData.asManagerTable, myInitiativesData.asManagerPagination));
                } catch (Exception e) {
                    log.error("Error while displaying the initiatives of the user", e);
                    return displayErrorWidget(id);
                }
            }
        });

    }

    /**
     * get the portfolio entries for which the current actor is the manager
     */
    private MyInitiativesData getTables(Long actorId, Integer asManagerPage) {

        Pagination<PortfolioEntry> asManagerPagination = new Pagination<>(PortfolioEntryDao.getPEAsExpr(false).eq("manager.id", actorId), 5,
                this.getConfiguration().getInt("maf.number_page_links"));

        asManagerPagination.setPageQueryName("asManagerPage");
        asManagerPagination.setCurrentPage(asManagerPage);

        List<PortfolioEntryListView> portfolioEntryListView = new ArrayList<PortfolioEntryListView>();
        for (PortfolioEntry portfolioEntry : asManagerPagination.getListOfObjects()) {
            portfolioEntryListView.add(new PortfolioEntryListView(portfolioEntry));
        }

        Table<PortfolioEntryListView> asManagerTable = this.getTableProvider().get().portfolioEntry.templateTable.fill(portfolioEntryListView,
                PortfolioEntryListView.getHideNonDefaultColumns(true, true));

        return new MyInitiativesData(asManagerTable, asManagerPagination);

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
     * Get the Play configuration service.
     */
    private Configuration getConfiguration() {
        return this.configuration;
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
     * The data of MyInitiativesAsManager widget (tables and paginations objects).
     * 
     * @author Johann Kohler
     *
     */
    private static class MyInitiativesData {

        public Table<PortfolioEntryListView> asManagerTable;
        public Pagination<PortfolioEntry> asManagerPagination;

        /**
         * Default constructor.
         * 
         * @param asManagerTable
         *            the manager table
         * @param asManagerPagination
         *            the manager pagination object
         */
        public MyInitiativesData(Table<PortfolioEntryListView> asManagerTable, Pagination<PortfolioEntry> asManagerPagination) {
            this.asManagerTable = asManagerTable;
            this.asManagerPagination = asManagerPagination;
        }

    }

}
