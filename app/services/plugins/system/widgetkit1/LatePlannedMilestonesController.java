package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import com.avaje.ebean.ExpressionList;

import framework.security.ISecurityService;
import framework.services.account.AccountManagementException;
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
import models.governance.PlannedLifeCycleMilestoneInstance;
import models.pmo.Actor;
import models.pmo.Portfolio;
import models.pmo.PortfolioEntry;
import play.Configuration;
import play.Logger;
import play.Play;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;

import services.tableprovider.ITableProvider;
import utils.table.PortfolioEntryListView;
import utils.table.PortfolioMilestoneListView;
import controllers.core.PortfolioController;
import dao.governance.LifeCyclePlanningDao;
import dao.pmo.ActorDao;
import dao.pmo.PortfolioDao;
import dao.pmo.PortfolioEntryDao;
/**
 * Widget which displays late planned milestones.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/LatePlannedMilestones")
public class LatePlannedMilestonesController extends WidgetController {

	private static Logger.ALogger log = Logger.of(MyInitiativesWidgetController.class);

    private ISecurityService securityService;

    private IPreferenceManagerPlugin preferenceManagerPlugin;

    private Configuration configuration;
    	     
    @Inject
    public LatePlannedMilestonesController(Configuration configuration, ILinkGenerationService linkGenerationService, ISecurityService securityService,
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
            	
            	// get the current actor
                IUserAccount userAccount = getSecurityService().getCurrentUser();
                Actor actor = ActorDao.getActorByUid(userAccount.getUid());
                if (actor == null) {
                    return ok(views.html.plugins.system.widgetkit1.no_actor.render(widgetId, DashboardWidgetColor.WARNING.getColor(), tempLinkGenerator,
                            getI18nMessagePlugin().get("plugin.widget_kit.late_planned_milestones_widget.title")));
                }
                Long actorId = actor.id;

                MyPageData myPageData = getTables(actorId, 0);
            	
                return ok(views.html.plugins.system.widgetkit1.late_planned_milestones_widget.render(widgetId, DashboardWidgetColor.INFO.getColor(), tempLinkGenerator, "as-manager", myPageData.asManagerTable, myPageData.asManagerPagination));

            }
        });
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
    
    @WebCommandPath(path = "/paginate/:id", id = "my-paginate")
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

                    MyPageData myPageData = getTables(actorId, asManagerPage);
                    
                     return ok(views.html.plugins.system.widgetkit1.late_planned_milestones_fragment_widget.render(id, tempLinkGenerator, tab, myPageData.asManagerTable, myPageData.asManagerPagination));
              
                } catch (Exception e) {
                    log.error("Error while displaying the initiatives of the user", e);
                    return displayErrorWidget(id);
                }
            }
        });
    }
    
    
    /**
     * 
     */
    private List<PortfolioMilestoneListView> getListOfObjects(Pagination<PlannedLifeCycleMilestoneInstance> page, List<PortfolioMilestoneListView> list) {
        
    	int lower = page.getCurrentPage() * page.getPageSize();
        int upper = (lower + page.getPageSize()) > page.getRowCount() ? page.getRowCount()  : (lower + page.getPageSize());
        if (lower <= upper) {
            return list.subList(lower, upper);
        }
        return list;
    }
   
    /**
     * get the portfolio entries for which the current actor is the manager
     */
    private MyPageData getTables(Long actorId, Integer asManagerPage) {
    	
    	List<PortfolioMilestoneListView> portfolioMilestoneListView = getportfolioMilestoneList();
		
		Pagination<PlannedLifeCycleMilestoneInstance> asManagerPagination  =new Pagination<>(portfolioMilestoneListView.size(), 5, Play.application().configuration().getInt("maf.number_page_links"));
		asManagerPagination.setPageQueryName("asManagerPage");
        asManagerPagination.setCurrentPage(asManagerPage);
        
        portfolioMilestoneListView=getListOfObjects(asManagerPagination, portfolioMilestoneListView);
    	
        Table<PortfolioMilestoneListView> asManagerTable = getTableProvider().get().portfolioMilestone.templateTable.fill(portfolioMilestoneListView);

        return new MyPageData(asManagerTable, asManagerPagination);
    }
    
    /**
     * 
     * @return
     */
    private List<PortfolioMilestoneListView> getportfolioMilestoneList()
    {
    	 // get the current actor
        IUserAccount userAccount = null;
        Actor actor = null;
        Long actorId = 0L; //TODO
        
		try {
			userAccount = this.getSecurityService().getCurrentUser();
			actor = ActorDao.getActorByUid(userAccount.getUid());
		} catch (AccountManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
		if (actor != null)
			actorId = actor.id;
		
    	// Get all active portfolios for which an actor is the manager
    	List<Portfolio> portfolioList = PortfolioDao.findPortfolio.where().eq("deleted", false).eq("manager.id", actorId).findList();
    			
    	// get the late milestones for those portfolios
        List<PortfolioMilestoneListView> portfolioMilestoneListView = new ArrayList<PortfolioMilestoneListView>();
        List<Long> myList = new ArrayList<>();
             
        for(Portfolio portfolio : portfolioList)
        {
        
	        for (PlannedLifeCycleMilestoneInstance plannedMilestoneInstance : LifeCyclePlanningDao
	                .getPlannedLCMilestoneInstanceNotApprovedAsListOfPortfolio(portfolio.id)) 
	        {
	        	if (!myList.contains(plannedMilestoneInstance.id))
	        	{
	        		portfolioMilestoneListView.add(new PortfolioMilestoneListView(plannedMilestoneInstance));
	        		myList.add(plannedMilestoneInstance.id);
	        	}
	        }     
        }    	
                
        return portfolioMilestoneListView;
    }
    
    /**
     * 
     */
    private static class MyPageData {

        public Table<PortfolioMilestoneListView> asManagerTable;
        public Pagination<PlannedLifeCycleMilestoneInstance> asManagerPagination;

        public MyPageData(Table<PortfolioMilestoneListView> asManagerTable, Pagination<PlannedLifeCycleMilestoneInstance> asManagerPagination) {
            this.asManagerTable = asManagerTable;
            this.asManagerPagination = asManagerPagination;
        }
    }
    
    /**
     * Get the table provider.
     */
    private ITableProvider getTableProvider() {
        return Play.application().injector().instanceOf(ITableProvider.class);
    }
    
    /**
     * Get the security service.
     */
    private ISecurityService getSecurityService() {
        return securityService;
    }
}
