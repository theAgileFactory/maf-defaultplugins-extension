package services.plugins.system.schedule1;

import java.util.Calendar;
import framework.services.custom_attribute.ICustomAttributeManagerService;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.tuple.Pair;

import akka.actor.Cancellable;
import framework.commons.message.EventMessage;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import framework.services.script.IScriptService;
import framework.services.system.ISysAdminUtils;
import play.Logger;
import play.libs.ws.WSClient;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import services.plugins.system.HookScriptUtils;

/**
 * A plugin which executes a piece of script at a defined frequency
 * @author Pierre-Yves Cloux
 */
public class HookScriptSchedulerPluginRunner implements IPluginRunner{
    private static Logger.ALogger log = Logger.of(HookScriptSchedulerPluginRunner.class);
    
    /**
     * The minimal frequency for the scheduler.
     */
    private static final int MINIMAL_FREQUENCY = 5;
    
    public static final String MAIN_PROPERTIES_CONFIGURATION_NAME = "main";
    public static final String HOOKSCRIPT_CONFIGURATION_NAME = "hook_script";
    
    public static final String FREQUENCY_IN_MINUTES_PARAMETER = "frequency.in.minutes";
    public static final String START_TIME_PARAMETER = "start.time";
    
    private static final String HOOK_METHOD="_performFromJava";
    private static final String DATE_CONVERTER_METHOD="function "+HOOK_METHOD+"(javaDate){"
    +"var d=new Date(javaDate.getTime());\n"
    +"perform(d);\n"
    +"}\n\n";
    
    private IScriptService scriptService;
    private IPluginContext pluginContext;
    private WSClient wsClient;
    private ScriptEngine scriptEngine;
    private ISysAdminUtils systAdminUtils;
    private String loadStartTime;
    private FiniteDuration loadFrequency;
    private Cancellable currentScheduler;
    private ICustomAttributeManagerService customAttributeManagerService;
    
    @Inject
    public HookScriptSchedulerPluginRunner(IPluginContext pluginContext, ISysAdminUtils systAdminUtils, WSClient wsClient, IScriptService
            scriptService, ICustomAttributeManagerService customAttributeManagerService) {
        this.pluginContext=pluginContext;
        this.systAdminUtils=systAdminUtils;
        this.wsClient=wsClient;
        this.scriptService = scriptService;
        this.customAttributeManagerService = customAttributeManagerService;
    }
    
    /**
     * Initialize a new script engine based on the plugin configuration
     * @throws PluginException 
     */
    private synchronized void initScriptEngine() throws PluginException {
        if(log.isDebugEnabled()){
            log.debug("Activating the script engine...");
        }
        this.scriptEngine = getScriptService().getEngine(getPluginContext().getPluginConfigurationName());
        this.scriptEngine.getContext().setAttribute("scriptUtils",new HookScriptUtils(getCustomAttributeManagerService(), getPluginContext(),getWsClient()), ScriptContext.ENGINE_SCOPE);
        Pair<Boolean, byte[]> hookScriptConfiguration=getPluginContext().getConfiguration(getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(HOOKSCRIPT_CONFIGURATION_NAME), true);
        if(!hookScriptConfiguration.getLeft()){
            try {
                //Evaluate the script
                this.scriptEngine.eval(DATE_CONVERTER_METHOD+new String(hookScriptConfiguration.getRight()));
            } catch (ScriptException e) {
                if(log.isDebugEnabled()){
                    log.debug("Invalid hook script",e);
                }
                throw new PluginException("Invalid hook script",e);
            }
        }else{
            throw new PluginException("WARNING: the current script might not be compatible with the version of the plugin"
                    + ", please edit it and save it before attempting a new start");
        }
        if(log.isDebugEnabled()){
            log.debug("...script engine activated");
        }
    }
    
    /**
     * Initialize the scheduler with the provided configuration
     * @throws PluginException
     */
    private synchronized void initScheduler() throws PluginException{
        PropertiesConfiguration properties = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(MAIN_PROPERTIES_CONFIGURATION_NAME)));
        try{
            properties.setThrowExceptionOnMissing(true);
            setLoadStartTime(properties.getString(START_TIME_PARAMETER));
            if (getLoadStartTime() == null || !getLoadStartTime().matches("^([01]?[0-9]|2[0-3])h[0-5][0-9]$")) {
                throw new IllegalArgumentException("Invalid time format for the " + START_TIME_PARAMETER + " parameter");
            }
            setLoadFrequency(FiniteDuration.create(properties.getLong(FREQUENCY_IN_MINUTES_PARAMETER), TimeUnit.MINUTES));
            if (properties.getLong(FREQUENCY_IN_MINUTES_PARAMETER) < MINIMAL_FREQUENCY) {
                throw new IllegalArgumentException("Invalid frequency " + FREQUENCY_IN_MINUTES_PARAMETER + " must be more than 5 minutes while it is "+properties.getLong(FREQUENCY_IN_MINUTES_PARAMETER));
            }
        }catch(Exception e){
            throw new PluginException("Invalid scheduler configuration parameters",e);
        }
        
        long howMuchMinutesUntilStartTime = howMuchMinutesUntilStartTime();
        setCurrentScheduler(getSystAdminUtils().scheduleRecurring(true,
                getPluginContext().getPluginDescriptor().getName() + " plugin " + getPluginContext().getPluginConfigurationName(),
                Duration.create(howMuchMinutesUntilStartTime, TimeUnit.MINUTES), getLoadFrequency(), new Runnable() {
                    @Override
                    public void run() {
                        executeHookScript();
                    }
                }));

        String startTimeMessage = String.format("Scheduler programmed to run in %d minutes", howMuchMinutesUntilStartTime);
        getPluginContext().log(LogLevel.INFO, startTimeMessage);
        getPluginContext().reportOnStartup(false, startTimeMessage);
    }
    
    /**
     * Execute the hook script
     */
    private synchronized void executeHookScript(){
        try{
            Invocable invocable = (Invocable) scriptEngine;
            invocable.invokeFunction(HOOK_METHOD, new Date());
            getPluginContext().reportMessage(null, false, "Hook script sucessfully executed");
        }catch(Exception e){
            getPluginContext().reportMessage(null, true, "Error while executing the hook script",e);
        }
    }
    
    private synchronized void shutDownScriptEngine(){
        this.scriptEngine=null;
    }
    
    private synchronized void shutDownScheduler(){
        if(getCurrentScheduler()!=null && !getCurrentScheduler().isCancelled()){
            try{
                getCurrentScheduler().cancel();
            }catch(Exception e){
                getPluginContext().reportOnStop(true, "Error while shutting down the scheduler", e);
                if(log.isDebugEnabled()){
                    log.debug("Error while shutting down the scheduler",e);
                }
            }
        }
    }
    
    /**
     * Return the number of minutes until the next "start time".
     */
    private long howMuchMinutesUntilStartTime() {
        String time = getLoadStartTime();
        Date today = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(time.substring(0, 2)));
        calendar.set(Calendar.MINUTE, Integer.valueOf(time.substring(3, 5)));
        if (calendar.getTime().before(today)) {
            calendar.add(Calendar.DATE, 1);
        }
        long diff = calendar.getTime().getTime() - today.getTime();
        return diff / (60 * 1000);
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        return null;
    }

    @Override
    public void handleInProvisioningMessage(EventMessage arg0) throws PluginException {
        
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage arg0) throws PluginException {
    }

    @Override
    public void start() throws PluginException {
        initScriptEngine();
        initScheduler();
    }

    @Override
    public void stop() {
        shutDownScheduler();
        shutDownScriptEngine();
    }

    private IPluginContext getPluginContext() {
        return pluginContext;
    }

    private ISysAdminUtils getSystAdminUtils() {
        return systAdminUtils;
    }

    private String getLoadStartTime() {
        return loadStartTime;
    }

    private FiniteDuration getLoadFrequency() {
        return loadFrequency;
    }

    private void setLoadStartTime(String loadStartTime) {
        this.loadStartTime = loadStartTime;
    }

    private void setLoadFrequency(FiniteDuration loadFrequency) {
        this.loadFrequency = loadFrequency;
    }

    private Cancellable getCurrentScheduler() {
        return currentScheduler;
    }

    private void setCurrentScheduler(Cancellable currentScheduler) {
        this.currentScheduler = currentScheduler;
    }

    private WSClient getWsClient() {
        return wsClient;
    }

    private IScriptService getScriptService() {
        return scriptService;
    }
    
    private ICustomAttributeManagerService getCustomAttributeManagerService() {
        return this.customAttributeManagerService;
    }

}
