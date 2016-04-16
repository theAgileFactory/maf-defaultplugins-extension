package services.plugins.system.timesheet1;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;

import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.loader.toolkit.AbstractJavaScriptFileLoaderMapper;
import framework.services.plugins.loader.toolkit.IGenericFileLoaderMapper;
import framework.services.plugins.loader.toolkit.LoadableObjectPluginRunner;
import framework.services.script.IScriptService;
import framework.services.system.ISysAdminUtils;

/**
 * A loader for loading timesheets
 * @author Pierre-Yves Cloux
 */
public class TimesheetLoaderPluginRunner extends LoadableObjectPluginRunner<TimesheetLoadableObject> {
    private IScriptService scriptService;

    @Inject
    public TimesheetLoaderPluginRunner(IPluginContext pluginContext, ISysAdminUtils sysAdminUtils, IScriptService scriptService) {
        super(pluginContext, sysAdminUtils);
        this.scriptService = scriptService;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        return null;
    }

    @Override
    public IGenericFileLoaderMapper<TimesheetLoadableObject> createGenericFileLoaderMapper(final String javaScriptMappingScript) {
        return new AbstractJavaScriptFileLoaderMapper<TimesheetLoadableObject>(TimesheetLoadableObject.class, javaScriptMappingScript, getScriptService()) {

            @Override
            public Pair<String, List<String>> beforeSave(List<TimesheetLoadableObject> listOfValidLoadedObjects) throws IOException {
                return null;
            }

            @Override
            public Pair<String, List<String>> afterSave(List<TimesheetLoadableObject> listOfValidLoadedObjects) throws IOException {
                return null;
            }
            
            @Override
            public String getLoadedObjectName() {
                return "Timesheet loader";
            }
            
        };
    }

    @Override
    public List<String> getAllowedFieldsForUnactivationWhereClause() {
        return Arrays.asList();
    }
    
    private IScriptService getScriptService() {
        return scriptService;
    }
}
