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
package services.plugins.jenkins.jenk1;

import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;

import framework.commons.message.EventMessage;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;

/**
 * A plugin which manages the links with Jenkins.<br/>
 * 
 * @author Pierre-Yves Cloux
 */
public class JenkinsPluginRunner implements IPluginRunner {
    private IPluginContext pluginContext;
    protected String jenkinsGuiUrl;

    public static final String MAIN_CONFIGURATION_NAME = "config";
    public static final String JENKINS_GUI_URL_PROPERTY = "jenkins.gui.url";

    /**
     * Default constructor.
     */
    @Inject
    public JenkinsPluginRunner(IPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    @Override
    public void start() throws PluginException {
        getPluginContext().log(LogLevel.INFO, "Jenkins plugin " + getPluginContext().getPluginConfigurationName() + " is starting : loading properties");
        PropertiesConfiguration properties = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(JenkinsPluginRunner.MAIN_CONFIGURATION_NAME)));
        properties.setThrowExceptionOnMissing(true);
        this.jenkinsGuiUrl = properties.getString(JENKINS_GUI_URL_PROPERTY);
        getPluginContext().log(LogLevel.INFO, "Jenkins plugin " + getPluginContext().getPluginConfigurationName() + " started !");
    }

    @Override
    public void stop() {
        getPluginContext().log(LogLevel.INFO, "Jenkins plugin stopped");
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public void handleInProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        final String menuLabel = getPluginContext().getPluginConfigurationName();
        return new IPluginMenuDescriptor() {

            @Override
            public String getPath() {
                return getJenkinsGuiUrl();
            }

            @Override
            public String getLabel() {
                return menuLabel;
            }
        };
    }

    /**
     * Get the plugin context.
     */
    private IPluginContext getPluginContext() {
        return pluginContext;
    }

    private String getJenkinsGuiUrl() {
        return jenkinsGuiUrl;
    }
}
