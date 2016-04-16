package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import dao.pmo.ActorDao;
import dao.pmo.PortfolioDao;
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
import models.pmo.Portfolio;
import play.Logger;
import play.Play;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import services.tableprovider.ITableProvider;
import utils.table.PortfolioListView;

/**
 * Widget which displays the active portfolios (as manager and as stakeholder)
 * of the current sign-in user.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/my-portfolios")
public class MyPortfoliosWidgetController extends WidgetController {

    private static Logger.ALogger log = Logger.of(MyPortfoliosWidgetController.class);

    private ISecurityService securityService;

    private IPreferenceManagerPlugin preferenceManagerPlugin;

    @Inject
    public MyPortfoliosWidgetController(ILinkGenerationService linkGenerationService, ISecurityService securityService, II18nMessagesPlugin i18nMessagePlugin,
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
                                getI18nMessagePlugin().get("plugin.widget_kit.my_portfolios.title")));
                    }
                    Long actorId = actor.id;

                    MyPortfoliosData myPortfoliosData = getTables(actorId, 0, 0);

                    return ok(views.html.plugins.system.widgetkit1.my_portfolios_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                            tempLinkGenerator, "as-manager", myPortfoliosData.asManagerTable, myPortfoliosData.asManagerPagination,
                            myPortfoliosData.asStakeholderTable, myPortfoliosData.asStakeholderPagination));
                } catch (Exception e) {
                    log.error("Error while displaying the portfolios of the user", e);
                    return displayErrorWidget(widgetId);
                }
            }
        });

    }

    @WebCommandPath(path = "/paginate/:id", id = "my-portfolios-paginate")
    public Promise<Result> paginate(@WebParameter(name = "id") Long id) {

        // get the query params
        Integer asManagerPage = getQueryParamAsPage("asManagerPage");
        Integer asStakeholderPage = getQueryParamAsPage("asStakeholderPage");
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

                    MyPortfoliosData myPortfoliosData = getTables(actorId, asManagerPage, asStakeholderPage);

                    return ok(views.html.plugins.system.widgetkit1.my_portfolios_fragment_widget.render(id, tempLinkGenerator, tab,
                            myPortfoliosData.asManagerTable, myPortfoliosData.asManagerPagination, myPortfoliosData.asStakeholderTable,
                            myPortfoliosData.asStakeholderPagination));
                } catch (Exception e) {
                    log.error("Error while displaying the portfolios of the user", e);
                    return displayErrorWidget(id);
                }
            }
        });

    }

    private MyPortfoliosData getTables(Long actorId, Integer asManagerPage, Integer asStakeholderPage) {

        /**
         * get the portfolios for which the current actor is the manager
         */

        Pagination<Portfolio> asManagerPagination = PortfolioDao.getPortfolioAsPaginationByManager(this.getPreferenceManagerPlugin(), actorId, false);
        asManagerPagination.setPageQueryName("asManagerPage");
        asManagerPagination.setCurrentPage(asManagerPage);

        List<PortfolioListView> portfolioListView = new ArrayList<PortfolioListView>();
        for (Portfolio portfolio : asManagerPagination.getListOfObjects()) {
            portfolioListView.add(new PortfolioListView(portfolio, StakeholderDao.getStakeholderAsListByActorAndPortfolio(actorId, portfolio.id)));
        }

        Set<String> hideColumnsForPortfolios = new HashSet<String>();
        hideColumnsForPortfolios.add("isActive");
        hideColumnsForPortfolios.add("manager");
        hideColumnsForPortfolios.add("stakeholderTypes");

        Table<PortfolioListView> asManagerTable = this.getTableProvider().get().portfolio.templateTable.fill(portfolioListView, hideColumnsForPortfolios);

        /**
         * get the portfolios for which the current actor is a stakeholder
         */

        Pagination<Portfolio> asStakeholderPagination = PortfolioDao.getPortfolioActiveAsPaginationByStakeholder(this.getPreferenceManagerPlugin(), actorId);
        asStakeholderPagination.setPageQueryName("asStakeholderPage");
        asStakeholderPagination.setCurrentPage(asStakeholderPage);

        portfolioListView = new ArrayList<PortfolioListView>();
        for (Portfolio portfolio : asStakeholderPagination.getListOfObjects()) {
            portfolioListView.add(new PortfolioListView(portfolio, StakeholderDao.getStakeholderAsListByActorAndPortfolio(actorId, portfolio.id)));
        }

        hideColumnsForPortfolios = new HashSet<String>();
        hideColumnsForPortfolios.add("isActive");

        Table<PortfolioListView> asStakeholderTable = this.getTableProvider().get().portfolio.templateTable.fill(portfolioListView, hideColumnsForPortfolios);

        return new MyPortfoliosData(asManagerTable, asManagerPagination, asStakeholderTable, asStakeholderPagination);

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
     * The data of MyPortfolios widget (tables and paginations objects).
     * 
     * @author Johann Kohler
     *
     */
    private static class MyPortfoliosData {

        public Table<PortfolioListView> asManagerTable;
        public Pagination<Portfolio> asManagerPagination;
        public Table<PortfolioListView> asStakeholderTable;
        public Pagination<Portfolio> asStakeholderPagination;

        /**
         * Default constructor.
         * 
         * @param asManagerTable
         *            the manager table
         * @param asManagerPagination
         *            the manager pagination object
         * @param asStakeholderTable
         *            the stakeholder table
         * @param asStakeholderPagination
         *            the stakeholder pagination object
         */
        public MyPortfoliosData(Table<PortfolioListView> asManagerTable, Pagination<Portfolio> asManagerPagination,
                Table<PortfolioListView> asStakeholderTable, Pagination<Portfolio> asStakeholderPagination) {
            this.asManagerTable = asManagerTable;
            this.asManagerPagination = asManagerPagination;
            this.asStakeholderTable = asStakeholderTable;
            this.asStakeholderPagination = asStakeholderPagination;
        }

    }

}
