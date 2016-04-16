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
package services.plugins.system.orgunitsload1;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.tuple.Pair;

import dao.pmo.ActorDao;
import dao.pmo.OrgUnitDao;
import framework.services.plugins.loader.toolkit.ILoadableObject;
import framework.utils.Msg;
import models.framework_models.parent.IModelConstants;
import models.pmo.Actor;
import models.pmo.OrgUnit;
import models.pmo.OrgUnitType;
import play.data.Form;
import play.data.validation.Constraints.MaxLength;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;

/**
 * An object structure which is to be used to load org units data.<br/>
 * This structure is JAXB enabled so that it could support a direct XML load.
 * 
 * @author Johann Kohler
 */
public class OrgUnitLoadableObject implements ILoadableObject {

    private static Form<OrgUnitLoadableObject> fakeFormTemplate = Form.form(OrgUnitLoadableObject.class);

    private long sourceRowNumber;

    private String isActive;

    @Required(message = "refId must not be null or blank")
    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "refId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String refId;

    @Required(message = "name must not be null or blank")
    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "name is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String name;

    @MaxLength(value = IModelConstants.LARGE_STRING, message = "managerRefId is too long " + IModelConstants.LARGE_STRING + " chars max")
    private String managerRefId;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "parentRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String parentRefId;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "typeRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String typeRefId;

    /**
     * Validate the non-string attributes.
     */
    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        // isActive
        if (this.isActive != null && !this.isActive.equals("") && this.getIsActiveAsBoolean() == null) {
            errors.add(new ValidationError("isActive", "isActive is not correctly formated, should be a true or false"));
        }

        // typeRefId
        if (this.typeRefId != null && !this.typeRefId.equals("") && this.getOrgUnitType() == null) {
            errors.add(new ValidationError("typeRefId", String.format("OrgUnitType %s does not exist", this.typeRefId)));
        }

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Default constructor.
     */
    public OrgUnitLoadableObject() {
    }

    @Override
    public long getSourceRowNumber() {
        return sourceRowNumber;
    }

    @Override
    public void setSourceRowNumber(long sourceRowNumber) {
        this.sourceRowNumber = sourceRowNumber;
    }

    @Override
    public Pair<Long, String> updateOrCreate() {
        return updateOrCreateOrgUnit();
    }

    /**
     * Validate the object consistency against various rules.
     */
    public Pair<Boolean, String> validateAndComplete() {

        // Perform a validation using Play form features
        Form<OrgUnitLoadableObject> fakeForm = null;

        try {
            fakeForm = fakeFormTemplate.bind(BeanUtils.describe(this));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Unable to validate the structure of the object\n" + toString());
        }

        if (fakeForm.hasErrors()) {
            StringBuffer validationErrors = new StringBuffer();
            for (String fieldName : fakeForm.errors().keySet()) {
                validationErrors.append("Validation Error [").append(fieldName).append("]:");
                for (ValidationError error : fakeForm.errors().get(fieldName)) {
                    validationErrors.append('\t').append(Msg.get(error.message())).append('\n');
                }
            }
            String exceptionMessage = validationErrors.toString() + toString() + "\n";
            return Pair.of(false, exceptionMessage);
        }

        return Pair.of(true, "");
    }

    /**
     * Update or create an org unit.<br/>
     * If a new org unit is created then the method returns the newly created
     * Id.<br/>
     * This method must be called after "validateAndComplete"
     * 
     * @return a tuple (id of Org unit object, refId) or null
     */
    Pair<Long, String> updateOrCreateOrgUnit() {

        boolean isNew = false;

        OrgUnit orgUnit = OrgUnitDao.getOrgUnitByRefId(getRefId());

        if (orgUnit == null) {
            orgUnit = new OrgUnit();
            isNew = true;
        }

        orgUnit.refId = getRefId();
        orgUnit.isActive = getIsActiveAsBoolean();
        orgUnit.name = getName();
        orgUnit.manager = getManager();
        orgUnit.orgUnitType = getOrgUnitType();

        orgUnit.save();

        if (isNew) {
            return Pair.of(orgUnit.id, orgUnit.refId);
        }

        return null;
    }

    /**
     * Look for the parent and update the org unit.<br/>
     * This must be done after all the org unit have been created or updated in
     * order to avoid missing parent.<br/>
     * If the parent is not found, this method returns a pair with the refId and
     * the parentRefId
     * 
     * @return a tuple (refId, parentRefId)
     */
    Pair<String, String> addParent() {
        if (getParentRefId() != null && !getParentRefId().equals("")) {
            OrgUnit foundParent = OrgUnitDao.getOrgUnitByRefId(getParentRefId());
            if (foundParent != null) {
                OrgUnit currentOrgUnit = OrgUnitDao.getOrgUnitByRefId(getRefId());
                currentOrgUnit.parent = foundParent;
                currentOrgUnit.save();
            } else {
                return Pair.of(getRefId(), getParentRefId());
            }
        }
        return null;
    }

    /**
     * Get is active.
     */
    @XmlElement
    public String getIsActive() {
        return isActive;
    }

    /**
     * Get the isActive attribute as a Boolean object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public Boolean getIsActiveAsBoolean() {
        if (this.isActive == null) {
            return null;
        } else if (this.isActive.toLowerCase().equals("true")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Set is active.
     * 
     * @param isActive
     *            set to true if active
     */
    public void setIsActive(String isActive) {
        this.isActive = isActive;
    }

    /**
     * Get the ref id.
     */
    @XmlElement
    public String getRefId() {
        return refId;
    }

    /**
     * Set the ref id.
     * 
     * @param refId
     *            the ref id
     */
    public void setRefId(String refId) {
        this.refId = refId;
    }

    /**
     * Get the name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the name.
     * 
     * @param name
     *            the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the manager ref id.
     */
    @XmlElement
    public String getManagerRefId() {
        return managerRefId;
    }

    /**
     * Get the actor for the manager ref id.
     */
    public Actor getManager() {
        return ActorDao.getActorByRefId(getManagerRefId());
    }

    /**
     * Set the manager ref id.
     * 
     * @param managerRefId
     *            the manager ref id
     */
    public void setManagerRefId(String managerRefId) {
        this.managerRefId = managerRefId;
    }

    /**
     * Get the parent ref id.
     */
    @XmlElement
    public String getParentRefId() {
        return this.parentRefId;
    }

    /**
     * Get the org unit for the parent ref id.
     */
    public OrgUnit getParent() {
        return OrgUnitDao.getOrgUnitByRefId(getParentRefId());
    }

    /**
     * Set the parent ref id.
     * 
     * @param parentRefId
     *            the parent ref id
     */
    public void setParentRefId(String parentRefId) {
        this.parentRefId = parentRefId;
    }

    /**
     * Get the type ref id.
     */
    @XmlElement
    public String getTypeRefId() {
        return this.typeRefId;
    }

    /**
     * Get the org unit type for the type ref id.
     */
    public OrgUnitType getOrgUnitType() {
        return OrgUnitDao.getOrgUnitTypeByRefId(getTypeRefId());
    }

    /**
     * Set the type ref id.
     * 
     * @param typeRefId
     *            the type ref id
     */
    public void setTypeRefId(String typeRefId) {
        this.typeRefId = typeRefId;
    }

    @Override
    public String toString() {
        return "Record [sourceRowNumber=" + sourceRowNumber + ", isActive=" + isActive + ", refId=" + refId + ", name=" + name + ", managerRefId="
                + managerRefId + ", parentRefId=" + parentRefId + ", typeRefId=" + typeRefId + "]";
    }

}
