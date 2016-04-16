package services.plugins.system.listener1;

import java.util.Map;
import framework.services.custom_attribute.ICustomAttributeManagerService;

import javax.inject.Inject;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.lang3.tuple.Pair;

import framework.commons.message.EventMessage;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import play.Logger;
import play.libs.ws.WSClient;
import services.plugins.system.HookScriptUtils;

/**
 * A plugin which allow to "expose" some web services to receive events from an
 * external system to perform some operations with BizDock.
 * @author Pierre-Yves Cloux
 */
public class ListenerHookScriptPluginRunner implements IPluginRunner {
    private static Logger.ALogger log = Logger.of(ListenerHookScriptPluginRunner.class);
    
    public static final String HOOKSCRIPT_CONFIGURATION_NAME = "hook_script";
    
    private NashornScriptEngineFactory factory;
    private IPluginContext pluginContext;
    private ScriptEngine scriptEngine;
    private WSClient wsClient;
    private ICustomAttributeManagerService customAttributeManagerService;
    
    @Inject
    public ListenerHookScriptPluginRunner(IPluginContext pluginContext, WSClient wsClient, ICustomAttributeManagerService customAttributeManagerService) {
        this.pluginContext=pluginContext;
        this.wsClient=wsClient;
        this.customAttributeManagerService = customAttributeManagerService;
        factory = new NashornScriptEngineFactory();
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
    public void handleInProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public void start() throws PluginException {
        initScriptEngine();
    }

    @Override
    public void stop() {
        shutDownScriptEngine();
    }
    
    /**
     * Initialize a new script engine based on the plugin configuration
     * @throws PluginException 
     */
    private synchronized void initScriptEngine() throws PluginException {
        if(log.isDebugEnabled()){
            log.debug("Activating the script engine...");
        }
        this.scriptEngine = getFactory().getScriptEngine(new ClassFilter() {
            @Override
            public boolean exposeToScripts(String className) {
                return true;
            }
        });
        this.scriptEngine.getContext().removeAttribute("JavaImporter", ScriptContext.ENGINE_SCOPE);
        this.scriptEngine.getContext().removeAttribute("Java", ScriptContext.ENGINE_SCOPE);
        this.scriptEngine.getContext().setAttribute("scriptUtils",new HookScriptUtils(getCustomAttributeManagerService(), getPluginContext(), getWsClient()), ScriptContext.ENGINE_SCOPE);
        Pair<Boolean, byte[]> hookScriptConfiguration=getPluginContext().getConfiguration(getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(HOOKSCRIPT_CONFIGURATION_NAME), true);
        if(!hookScriptConfiguration.getLeft()){
            try {
                //Evaluate the script
                this.scriptEngine.eval(new String(hookScriptConfiguration.getRight()));
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
    
    private synchronized void shutDownScriptEngine(){
        this.scriptEngine=null;
    }

    private NashornScriptEngineFactory getFactory() {
        return factory;
    }

    private IPluginContext getPluginContext() {
        return pluginContext;
    }

    private WSClient getWsClient() {
        return wsClient;
    }
    
    private ICustomAttributeManagerService getCustomAttributeManagerService() {
        return this.customAttributeManagerService;
    }

}
