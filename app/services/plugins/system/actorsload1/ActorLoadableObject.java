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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.tuple.Pair;

import controllers.admin.UserManager;
import dao.pmo.ActorDao;
import dao.pmo.OrgUnitDao;
import framework.services.plugins.loader.toolkit.ILoadableObject;
import framework.utils.Msg;
import models.framework_models.parent.IModelConstants;
import models.pmo.Actor;
import models.pmo.ActorType;
import models.pmo.OrgUnit;
import play.data.Form;
import play.data.validation.Constraints.MaxLength;
import play.data.validation.Constraints.MinLength;
import play.data.validation.Constraints.Pattern;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;

/**
 * An object structure which is to be used to load actors data.<br/>
 * This structure is JAXB enabled so that it could support a direct XML load.
 * 
 * @author Pierre-Yves Cloux
 * @author Johann Kohler
 */
public class ActorLoadableObject implements ILoadableObject {

    private static Form<ActorLoadableObject> fakeFormTemplate = Form.form(ActorLoadableObject.class);

    private long sourceRowNumber;

    private String isActive;

    @Required(message = "refId must not be null or blank")
    @MaxLength(value = IModelConstants.LARGE_STRING, message = "refId is too long " + IModelConstants.LARGE_STRING + " chars max")
    private String refId;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "erpRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String erpRefId;

    @Required(message = "firstName must not be null or blank")
    @MinLength(value = IModelConstants.MIN_NAME_LENGTH, message = "object.user_account.first_name.invalid")
    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "object.user_account.first_name.invalid")
    private String firstName;

    @Required(message = "lastName must not be null or blank")
    @MinLength(value = IModelConstants.MIN_NAME_LENGTH, message = "object.user_account.last_name.invalid")
    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "object.user_account.last_name.invalid")
    private String lastName;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "title is too long " + IModelConstants.MEDIUM_STRING + " characters max")
    private String title;

    @Pattern(value = IModelConstants.EMAIL_VALIDATION_PATTERN, message = "object.user_account.email.invalid")
    private String mail;

    @MaxLength(value = IModelConstants.PHONE_NUMBER, message = "mobilePhone is too long " + IModelConstants.PHONE_NUMBER + " characters max")
    private String mobilePhone;

    @MaxLength(value = IModelConstants.PHONE_NUMBER, message = "fixPhone is too long " + IModelConstants.PHONE_NUMBER + " characters max")
    private String fixPhone;

    @MaxLength(value = IModelConstants.SMALL_STRING, message = "employeId is too long " + IModelConstants.SMALL_STRING + " characters max")
    private String employeId;

    @MinLength(value = IModelConstants.MIN_UID_LENGTH, message = "object.user_account.uid.invalid")
    @MaxLength(value = IModelConstants.LARGE_STRING, message = "object.user_account.uid.invalid")
    @Pattern(value = IModelConstants.UID_VALIDATION_PATTERN, message = "object.user_account.uid.invalid")
    private String login;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "actorTypeRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String actorTypeRefId;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "orgUnitRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String orgUnitRefId;

    @MaxLength(value = IModelConstants.LARGE_STRING, message = "managerRefId is too long " + IModelConstants.LARGE_STRING + " chars max")
    private String managerRefId;

    /**
     * Validate the non-string attributes.
     */
    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        // isActive
        if (this.isActive != null && !this.isActive.equals("") && this.getIsActiveAsBoolean() == null) {
            errors.add(new ValidationError("isActive", "isActive is not correctly formated, should be a true or false"));
        }

        // actorTypeRefId
        if (this.actorTypeRefId != null && !this.actorTypeRefId.equals("") && this.getActorType() == null) {
            errors.add(new ValidationError("actorTypeRefId", String.format("ActorType %s does not exist", this.actorTypeRefId)));
        }

        // login
        if (this.login != null && !this.login.equals("")) {
            Actor testActor = ActorDao.getActorByUid(this.login);
            if (testActor != null) {
                Actor actor = ActorDao.getActorByRefId(this.refId);
                if (actor != null) { // edit
                    if (!testActor.id.equals(actor.id)) {
                        errors.add(new ValidationError("login", "The login \"" + this.login + "\" is already used by another actor"));
                    }
                } else { // create
                    errors.add(new ValidationError("login", "The login \"" + this.login + "\" is already used by another actor"));
                }
            }
        }

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Default constructor.
     */
    public ActorLoadableObject() {
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
        return updateOrCreateActor();
    }

    /**
     * Validate the object consistency against various rules.<br/>
     * WARNING: these rules must be consistent with the {@link Actor} object as
     * well as {@link UserManager} ones. <br/>
     * This method also looks for {@link ActorType} and {@link OrgUnit}
     * according to the provided values.
     */
    public Pair<Boolean, String> validateAndComplete() {

        // Perform a validation using Play form features
        Form<ActorLoadableObject> fakeForm = null;

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
     * Update or create an actor type.<br/>
     * If a new actor is created then the method returns the newly created Id.
     * <br/>
     * This method must be called after "validateAndComplete"
     * 
     * @return a tuple (id of Actor object, refId) or null
     */
    Pair<Long, String> updateOrCreateActor() {

        boolean isNew = false;

        Actor actor = ActorDao.getActorByRefId(getRefId());

        if (actor == null) {
            actor = new Actor();
            isNew = true;
        }
        actor.refId = getRefId();
        actor.firstName = getFirstName();
        actor.lastName = getLastName();
        actor.erpRefId = getErpRefId();
        actor.employeeId = getEmployeId();
        actor.fixPhone = getFixPhone();
        actor.mobilePhone = getMobilePhone();
        actor.title = getTitle();
        actor.mail = getMail();
        actor.orgUnit = getOrgUnit();
        actor.actorType = getActorType();
        actor.uid = getLogin();
        actor.isActive = getIsActiveAsBoolean();
        actor.save();

        if (isNew) {
            return Pair.of(actor.id, actor.refId);
        }

        return null;
    }

    /**
     * Look for the manager and update the actor.<br/>
     * This must be done after all the users have been created or updated in
     * order to avoid missing managers.<br/>
     * If the manager is not found, this method returns a pair with the refId
     * and the managerRefId
     * 
     * @return a tuple (refId, managerRefId)
     */
    Pair<String, String> addManager() {
        if (getManagerRefId() != null && !getManagerRefId().equals("")) {
            Actor foundManager = ActorDao.getActorByRefId(getManagerRefId());
            if (foundManager != null) {
                Actor currentActor = ActorDao.getActorByRefId(getRefId());
                currentActor.manager = foundManager;
                currentActor.save();
            } else {
                return Pair.of(getRefId(), getManagerRefId());
            }
        }
        return null;
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
     * Get the ERP ref id.
     */
    @XmlElement
    public String getErpRefId() {
        return erpRefId;
    }

    /**
     * Set the ERP ref id.
     * 
     * @param erpRefId
     *            the ERP ref id
     */
    public void setErpRefId(String erpRefId) {
        this.erpRefId = erpRefId;
    }

    /**
     * Get the first name.
     */
    @XmlElement
    public String getFirstName() {
        return firstName;
    }

    /**
     * Set the first name.
     * 
     * @param firstName
     *            the first name
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Get the last name.
     */
    @XmlElement
    public String getLastName() {
        return lastName;
    }

    /**
     * Set the last name.
     * 
     * @param lastName
     *            the last name
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Get the title.
     */
    @XmlElement
    public String getTitle() {
        return title;
    }

    /**
     * Set the title.
     * 
     * @param title
     *            the title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get the mail.
     */
    @XmlElement
    public String getMail() {
        return mail;
    }

    /**
     * Set the mail.
     * 
     * @param mail
     *            the mail
     */
    public void setMail(String mail) {
        this.mail = mail;
    }

    /**
     * Get the mobile phone.
     */
    @XmlElement
    public String getMobilePhone() {
        return mobilePhone;
    }

    /**
     * Set the mobile phone.
     * 
     * @param mobilePhone
     *            the mobile phone
     */
    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    /**
     * Get the fix phone.
     */
    @XmlElement
    public String getFixPhone() {
        return fixPhone;
    }

    /**
     * Set the fix phone.
     * 
     * @param fixPhone
     *            the fix phone
     */
    public void setFixPhone(String fixPhone) {
        this.fixPhone = fixPhone;
    }

    /**
     * Get the employee id.
     */
    @XmlElement
    public String getEmployeId() {
        return employeId;
    }

    /**
     * Set the employee id.
     * 
     * @param employeId
     *            the employee id
     */
    public void setEmployeId(String employeId) {
        this.employeId = employeId;
    }

    /**
     * Get the login.
     */
    @XmlElement
    public String getLogin() {
        return login;
    }

    /**
     * Set the login.
     * 
     * @param login
     *            the login
     */
    public void setLogin(String login) {
        this.login = login;
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
    public void setActive(String isActive) {
        this.isActive = isActive;
    }

    /**
     * Set the manager ref id.
     */
    @XmlElement
    public String getManagerRefId() {
        return managerRefId;
    }

    /**
     * Get the manager for the manager ref id.
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
     * Get the actor type ref id.
     */
    @XmlElement
    public String getActorTypeRefId() {
        return actorTypeRefId;
    }

    /**
     * Get the actor type for the actor type ref id.
     */
    public ActorType getActorType() {
        return ActorDao.getActorTypeByRefId(getActorTypeRefId());
    }

    /**
     * Set the actor type ref id.
     * 
     * @param actorTypeRefId
     *            the actor type ref id
     */
    public void setActorTypeRefId(String actorTypeRefId) {
        this.actorTypeRefId = actorTypeRefId;
    }

    /**
     * Get the org unit ref id.
     */
    @XmlElement
    public String getOrgUnitRefId() {
        return orgUnitRefId;
    }

    /**
     * Get the org unit for the org unit ref id.
     */
    public OrgUnit getOrgUnit() {
        return OrgUnitDao.getOrgUnitByRefId(getOrgUnitRefId());
    }

    /**
     * Set the org unit ref id.
     * 
     * @param orgUnitRefId
     *            the org unit ref id
     */
    public void setOrgUnitRefId(String orgUnitRefId) {
        this.orgUnitRefId = orgUnitRefId;
    }

    @Override
    public String toString() {
        return "Record [sourceRowNumber=" + sourceRowNumber + ", isActive=" + isActive + ", refId=" + refId + ", erpRefId=" + erpRefId + ", firstName="
                + firstName + ", lastName=" + lastName + ", title=" + title + ", mail=" + mail + ", mobilePhone=" + mobilePhone + ", fixPhone=" + fixPhone
                + ", employeId=" + employeId + ", login=" + login + ", actorTypeRefId=" + actorTypeRefId + ", orgUnitRefId=" + orgUnitRefId
                + ", managerRefId=" + managerRefId + "]";
    }

}
