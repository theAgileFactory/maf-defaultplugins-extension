package services.plugins.system.genint1;

import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import framework.commons.message.EventMessage;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import play.Logger;

/**
 * A plugin which provides an URL based integration to an external system.<br/>
 * The plugin is using a simple configuration file with:
 * <ul>
 * <li>path : the complete URL to the external service</li>
 * <li>label : a label</li>
 * </ul>
 * @author Pierre-Yves Cloux
 */
public class GenericExternalIntegrationPluginRunner implements IPluginRunner{
    private static Logger.ALogger log = Logger.of(GenericExternalIntegrationPluginRunner.class);
    private IPluginContext pluginContext;
    private String path;
    private String label;
    
    private static final String MAIN_PROPERTIES="main";
    private static final String PATH_PROPERTY="external.service.path";
    private static final String LABEL_PROPERTY="external.service.label";
    
    @Inject
    public GenericExternalIntegrationPluginRunner(IPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        if(StringUtils.isBlank(getPath())){
            return null;
        }
        final String aPath=getPath();
        final String aLabel=getLabel();
        return new IPluginMenuDescriptor() {
            
            @Override
            public String getPath() {
                return aPath;
            }
            
            @Override
            public String getLabel() {
                return aLabel;
            }
        };
    }

    @Override
    public void handleInProvisioningMessage(EventMessage arg0) throws PluginException {
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage arg0) throws PluginException {       
    }

    @Override
    public void start() throws PluginException {
        Pair<Boolean, byte[]> externalServiceConfiguration=getPluginContext().getConfiguration(getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(MAIN_PROPERTIES), true);
        if(!externalServiceConfiguration.getLeft()){
            PropertiesConfiguration config=getPluginContext().getPropertiesConfigurationFromByteArray(externalServiceConfiguration.getRight());
            this.path=config.getString(PATH_PROPERTY);
            this.label=config.getString(LABEL_PROPERTY);
            if(log.isDebugEnabled()){
                log.debug("Reading external service parameters "+path+" with label "+label);
            }
        }else{
            throw new PluginException("WARNING: the current configuration might not be compatible with the version of the plugin"
                    + ", please edit it and save it before attempting a new start");
        }
    }

    @Override
    public void stop() {
    }

    private String getLabel() {
        return label;
    }

    private String getPath() {
        return path;
    }

    private IPluginContext getPluginContext() {
        return pluginContext;
    }

}
