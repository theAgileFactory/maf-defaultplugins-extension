/*! LICENSE
 *
 * Copyright (c) 2015, The Agile Factory SA and/or its affiliates. All rights
 * reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package services.plugins.system.notification1;

import java.util.ArrayList;
import framework.services.custom_attribute.ICustomAttributeManagerService;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.lang3.tuple.Pair;

import framework.commons.DataType;
import framework.commons.message.EventMessage;
import framework.services.database.ModificationPair;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import framework.services.script.IScriptService;
import play.Logger;
import play.libs.ws.WSClient;
import services.plugins.system.HookScriptUtils;
import services.plugins.system.HookScriptUtils.EventType;

/**
 * A plugin which connects the master data engine of BizDock to the outside
 * world. This plugins register to events regarding some BizDock master data.
 * When such events are raised, the plugin can convert it into:
 * <ul>
 * <li>An e-mail sent to a dedicated destination</li>
 * <li>An HTTP post or get to defined WebService endpoint</li>
 * <li>Any other action defined by the hook script</li>
 * </ul>
 * 
 * @author Pierre-Yves Cloux
 */
public class EventNotificationPluginRunner implements IPluginRunner {
    private static Logger.ALogger log = Logger.of(EventNotificationPluginRunner.class);
    
    public static final String HOOKSCRIPT_CONFIGURATION_NAME = "hook_script";
    
    private IScriptService scriptService;
    private IPluginContext pluginContext;
    private ScriptEngine scriptEngine;
    private WSClient wsClient;
    private List<DataType> supportedDataTypes;
    private ICustomAttributeManagerService customAttributeManagerService;
    
    /**
     * Default constructor.
     */
    @Inject
    public EventNotificationPluginRunner(IPluginContext pluginContext, WSClient wsClient, IScriptService scriptService, ICustomAttributeManagerService customAttributeManagerService) {
        this.pluginContext = pluginContext;
        this.wsClient=wsClient;
        this.scriptService=scriptService;
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
        this.supportedDataTypes = new ArrayList<DataType>();
        this.scriptEngine = getScriptService().getEngine(getPluginContext().getPluginConfigurationName());
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
            
            //Get the registered data types
            Invocable invocable = (Invocable) this.scriptEngine;
            try {
                List<String> supportedDataTypeNames=new ArrayList<String>();
                invocable.invokeFunction("register", supportedDataTypeNames);
                for(String supportedDataTypeName : supportedDataTypeNames){
                    DataType dataType=DataType.getDataType(supportedDataTypeName);
                    if(dataType==null){
                        throw new PluginException("Invalid data type "+supportedDataTypeName+" in the \"register\" method");
                    }
                    this.supportedDataTypes.add(dataType);
                }
            } catch (NoSuchMethodException e) {
                throw new PluginException("No method \"register\" in this hook script",e);
            } catch (ScriptException e) {
                throw new PluginException("No method \"register\" in this hook script or invalid method",e);
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

    @Override
    public void handleInProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {
        if(log.isDebugEnabled()){
            log.debug("Received an event message "+eventMessage);
        }
        if(getSupportedDataTypes().contains(eventMessage.getDataType())){
            executeHook(eventMessage);
        }
    }

    /**
     * Execute the hook.<br/>
     * The access is synchronized since the Nashorn scripting engine is not thread safe
     * @param eventMessage
     */
    private synchronized void executeHook(EventMessage eventMessage) {
        Invocable invocable = (Invocable) getScriptEngine();
        switch(eventMessage.getMessageType()){
        case OBJECT_CREATED:
            try {
                invocable.invokeFunction("notify", eventMessage.getDataType().getDataName(),eventMessage.getInternalId(), EventType.CREATE.name());
                if(log.isDebugEnabled()){
                    log.debug("Script executed for OBJECT_CREATED");
                }
            } catch (Exception e) {
                getPluginContext().reportOnEventHandling(eventMessage.getTransactionId(), true, eventMessage, "Error while executing the hook script", e);
            }
            break;
        case OBJECT_DELETED:
            try {
                invocable.invokeFunction("notify", eventMessage.getDataType().getDataName(),eventMessage.getInternalId(), EventType.DELETE.name());
                if(log.isDebugEnabled()){
                    log.debug("Script executed for OBJECT_DELETED");
                }
            } catch (Exception e) {
                getPluginContext().reportOnEventHandling(eventMessage.getTransactionId(), true, eventMessage, "Error while executing the hook script", e);
            }
            break;
        case OBJECT_UPDATED:
            try {
                @SuppressWarnings("unchecked")
                Map<String, ModificationPair> modifiedAttributes=(Map<String, ModificationPair>) eventMessage.getPayload();
                invocable.invokeFunction("notify", eventMessage.getDataType().getDataName(),eventMessage.getInternalId(), EventType.UPDATE.name(), modifiedAttributes);
                if(log.isDebugEnabled()){
                    log.debug("Script executed for OBJECT_UPDATED");
                }
            } catch (Exception e) {
                getPluginContext().reportOnEventHandling(eventMessage.getTransactionId(), true, eventMessage, "Error while executing the hook script", e);
            }
            break;
        default:
            if(log.isDebugEnabled()){
                log.debug("Invalid message type received by the plugin "+eventMessage);
            }
            break;
        }
    }

    @Override
    public void start() throws PluginException {
        initScriptEngine();
    }

    @Override
    public void stop() {
        shutDownScriptEngine();
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        return null;
    }

    private IPluginContext getPluginContext() {
        return pluginContext;
    }

    private ScriptEngine getScriptEngine() {
        return scriptEngine;
    }

    private synchronized List<DataType> getSupportedDataTypes() {
        return supportedDataTypes;
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
