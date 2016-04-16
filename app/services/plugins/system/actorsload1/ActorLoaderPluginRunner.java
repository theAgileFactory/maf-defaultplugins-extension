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
package services.plugins.system.actorsload1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;

import dao.pmo.ActorDao;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.loader.toolkit.AbstractJavaScriptFileLoaderMapper;
import framework.services.plugins.loader.toolkit.IGenericFileLoaderMapper;
import framework.services.plugins.loader.toolkit.LoadableObjectPluginRunner;
import framework.services.script.IScriptService;
import framework.services.system.ISysAdminUtils;

/**
 * Actor loader plugin.
 * 
 * @author Johann Kohler
 */
public class ActorLoaderPluginRunner extends LoadableObjectPluginRunner<ActorLoadableObject> {
    private IScriptService scriptService;

    /**
     * Default constructor.
     */
    @Inject
    public ActorLoaderPluginRunner(IPluginContext pluginContext, ISysAdminUtils sysAdminUtils, IScriptService scriptService) {
        super(pluginContext, sysAdminUtils);
        this.scriptService = scriptService;
    }

    @Override
    public List<String> getAllowedFieldsForUnactivationWhereClause() {
        return Arrays.asList("mail", "ref_id", "erp_ref_id", "org_unit_ref_id", "actor_type_ref_id");
    }

    @Override
    public IGenericFileLoaderMapper<ActorLoadableObject> createGenericFileLoaderMapper(final String javaScriptMappingScript) {
        return new AbstractJavaScriptFileLoaderMapper<ActorLoadableObject>(ActorLoadableObject.class, javaScriptMappingScript, getScriptService()) {

            @Override
            public String getLoadedObjectName() {
                return "Employees loader";
            }

            @Override
            public Pair<String, List<String>> beforeSave(List<ActorLoadableObject> listOfValidLoadedObjects) throws IOException {

                if (isUnactivateNotFoundObjects()) {
                    String unactivationSelectionClause = getUnactivationSelectionClause();
                    unactivationSelectionClause = unactivationSelectionClause
                            .replace("actor_type_ref_id", "(select actor_type.ref_id from actor_type where actor_type.id=actor_type_id)")
                            .replace("org_unit_ref_id", "(select org_unit.ref_id from org_unit where org_unit.id=org_unit_id)");
                    ActorDao.unactivateActors(unactivationSelectionClause);
                }

                List<String> notFoundRelations = new ArrayList<String>();
                for (ActorLoadableObject actorLoadableObject : listOfValidLoadedObjects) {
                    if (actorLoadableObject.getOrgUnitRefId() != null && !actorLoadableObject.getOrgUnitRefId().equals("")
                            && actorLoadableObject.getOrgUnit() == null) {
                        notFoundRelations.add("Actor " + actorLoadableObject.getRefId() + ": "
                                + String.format("org unit %s does not exist", actorLoadableObject.getOrgUnitRefId()));
                    }
                }

                return Pair.of("Not found relations", notFoundRelations);
            }

            @Override
            public Pair<String, List<String>> afterSave(List<ActorLoadableObject> listOfValidLoadedObjects) throws IOException {
                List<String> managerNotFound = new ArrayList<String>();
                for (ActorLoadableObject actorLoadObject : listOfValidLoadedObjects) {
                    Pair<String, String> pair = actorLoadObject.addManager();
                    if (pair != null) {
                        managerNotFound.add("Employee " + pair.getLeft() + ": impossible to find his manager with refId " + pair.getRight());
                    }
                }
                return Pair.of("Manager association", managerNotFound);
            }
        };
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        return null;
    }

    private IScriptService getScriptService() {
        return scriptService;
    }

}
