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
package services.plugins.system.workOrderLoad;



import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import controllers.admin.UserManager;
import dao.finance.CurrencyDAO;
import dao.finance.WorkOrderDAO;
import dao.pmo.ActorDao;
import dao.pmo.OrgUnitDao;
import dao.pmo.PortfolioEntryDao;
import framework.services.plugins.loader.toolkit.ILoadableObject;
import framework.utils.Msg;
import models.finance.Currency;
import models.finance.WorkOrder;
import models.framework_models.common.CustomAttributeDefinition;
import models.framework_models.common.ICustomAttributeValue;
import models.framework_models.parent.IModelConstants;
import models.pmo.Actor;
import models.pmo.ActorType;
import models.pmo.OrgUnit;
import models.pmo.PortfolioEntry;
import play.data.Form;
import play.data.validation.Constraints.MaxLength;
import play.data.validation.Constraints.MinLength;
import play.data.validation.Constraints.Pattern;
import play.data.validation.Constraints.Required;
import services.plugins.system.timesheet1.TimesheetLoadableObject;
import play.data.validation.ValidationError;

/**
 * An object structure which is to be used to load actors data.<br/>
 * This structure is JAXB enabled so that it could support a direct XML load.
 * 
 * @author Pierre-Yves Cloux
 * @author Johann Kohler
 */
public class ExpensesLoadableObject implements ILoadableObject {

	public static String CUSTOM_ATTRIBUTE_FORM_FIELD_NAME_EXTENSION = "_custattr_";
	
    private static Form<ExpensesLoadableObject> fakeFormTemplate = Form.form(ExpensesLoadableObject.class);
	private static final String DATE_FORMAT = "yyyy-MM-dd";

    private long sourceRowNumber;

    
    @Required(message = "Governance ID must not be null or blank")
  //  @MaxLength(value = IModelConstants.SMALL_STRING)
    private String governanceId;
    
    @Required
    private String dueDate;
   
    @Required
    private String startDate;
    
    @Required(message = "name must not be null or blank")
    private String name;
    
    @Required(message = "description must not be null or blank")
    private String description; 
    
    @Required
    private String amount;
    
    @Required
    private String amountReceived;
    
    private Map<String, String> customAttributeValue;

    /**
     * Validate the non-string attributes.
     */
    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        if (this.governanceId != null && !this.governanceId.equals("") && this.getPortfolioEntry()==null) {
            errors.add(new ValidationError("governanceId", String.format("GovernanceId %s does not exist", this.governanceId)));
        }
        
        if(this.dueDate !=null && !this.dueDate.equals("") && this.getDueDateAsDate()==null){
            errors.add(new ValidationError("dueDate", "No valid due date or invalid format (must be "+DATE_FORMAT+")"));
        }
        
        if(this.startDate !=null && !this.startDate.equals("") && this.getStartDateAsDate()==null){
            errors.add(new ValidationError("startDate", "No valid start date or invalid format (must be "+DATE_FORMAT+")"));
        }
        
        if (this.amount != null && !this.amount.equals("") && this.getAmountAsBigDecimal() == null) {
            errors.add(new ValidationError("amount", "amount is not correctly formated, should be a Number"));
        }

        if (this.amountReceived != null && !this.amountReceived.equals("") && this.getAmountReceivedAsBigDecimal() == null) {
            errors.add(new ValidationError("amountReceived", "amountReceived is not correctly formated, should be a Number"));
        }

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Default constructor.
     */
    public ExpensesLoadableObject() {
    	customAttributeValue = new HashMap<String, String>();
    }

    @Override
    public long getSourceRowNumber() {
        return sourceRowNumber;
    }
    
    /**
     * Look for a portfolio entry associated with the current governanceId
     * @return
     */
    public PortfolioEntry getPortfolioEntry(){
        return PortfolioEntryDao.getPEByGovernanceId(getGovernanceId());
    }

    @Override
    public void setSourceRowNumber(long sourceRowNumber) {
        this.sourceRowNumber = sourceRowNumber;
    }

    @Override
    public Pair<Long, String> updateOrCreate() {
        return updateOrCreateWorkOrder();
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
        Form<ExpensesLoadableObject> fakeForm = null;

        try {
            Map<String,String> formData=BeanUtils.describe(this);
            fakeForm = fakeFormTemplate.bind(formData);
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
    Pair<Long, String> updateOrCreateWorkOrder() {

    	boolean isNew = false;

      //  WorkOrder workOrder = WorkOrderDAO.getWorkOrderById( 1L /*getId()*/)   ;

    	WorkOrder workOrder = new WorkOrder(); 
    	isNew = true;
      
    	PortfolioEntry pe =PortfolioEntryDao.getPEByGovernanceId(this.governanceId);
      /*  currency.setCode("CHF"); 
        currency.setConversionRate(new BigDecimal(1));*/
        
    	workOrder.deleted = false;
    	workOrder.lastUpdate = new Timestamp(System.currentTimeMillis());
    	workOrder.name = this.getName();
    	workOrder.description = this.getDescription();
    	workOrder.amount = getAmountAsBigDecimal();
    	workOrder.amountReceived = getAmountReceivedAsBigDecimal();
    	workOrder.isOpex = false;
    	workOrder.setIsEngaged(true);
    	workOrder.setCreationDate( new Date() );
    	workOrder.dueDate = this.getDueDateAsDate();
    	workOrder.startDate = this.getStartDateAsDate();      
    	workOrder.currency = getCurrencyCode();
        workOrder.currencyRate = getCurrencyRateAsBigDecimal();
        workOrder.shared = false;

      //  workOrder.setCurrencyRate(new BigDecimal("1"));;
      //  workOrder.setCurrency(currency);   
       
        workOrder.setPortfolioEntry(pe);
        
        
        
        workOrder.save();
        
        // save custom attributes values
        saveCustomAttirbutesValues(customAttributeValue, workOrder.getClass(), workOrder.id);

        if (isNew) {
            return Pair.of(workOrder.id, workOrder.name);
        }

        return null;
    }

    /**
     * 
     */
    
    public boolean saveCustomAttirbutesValues(Map<String, String> data, Class<?> clazz,  Long objectId) {
       
    	boolean hasErrors = false;
    	
        if (data != null) {
            List<ICustomAttributeValue> customAttributeValues =CustomAttributeDefinition.getOrderedCustomAttributeValues(clazz, objectId);

            if (customAttributeValues != null) {
                for (ICustomAttributeValue customAttributeValue : customAttributeValues) {

                    String fieldName = CUSTOM_ATTRIBUTE_FORM_FIELD_NAME_EXTENSION + customAttributeValue.getDefinition().uuid;
                    System.out.println(fieldName);
                    
                    String value = data.get(fieldName);
                    if (value !=null && !value.isEmpty())
                    {
	                    customAttributeValue.parse(null, value);
	                    
	                    if (customAttributeValue.hasError()) {
	                    	System.out.println("custom attribute has Errors");
	                        hasErrors = true;
	                    }
	                    else
	                    {
	                    	customAttributeValue.performSave(null, null, fieldName);
	                    }
                    }
                }
            }
        }
        
        return hasErrors;
    }
    
    /**
     * 
     */
    public void setGovernanceId(String governanceId) {
        this.governanceId = governanceId;
    }
        
    /**
     * 
     */
    @XmlElement
    public String getGovernanceId() {
        return governanceId;
    }

    
    /**
     * 
     */
    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }
    
    /**
     * 
     */
    @XmlElement
    public String getDueDate() {
        return dueDate;
    }
   
    public Date getDueDateAsDate() {
        try {
            DateFormat format = new SimpleDateFormat(DATE_FORMAT);
            return format.parse(this.dueDate);
        } catch (ParseException e) {
            return null;
        }
    }
    
    /**
     * 
     */
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }
    
    /**
     * 
     */
    @XmlElement
    public String getStartDate() {
        return startDate;
    }
   
    public Date getStartDateAsDate() {
        try {
            DateFormat format = new SimpleDateFormat(DATE_FORMAT);
            return format.parse(this.startDate);
        } catch (ParseException e) {
            return null;
        }
    }
    
    /**
     * 
     */
    @XmlElement
    public String getName() {
        return name;
    }

    /**
	 *
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    /**
     * 
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * 
     */
    @XmlElement
    public String getAmount() {
        return amount;
    }
    
    public BigDecimal getAmountAsBigDecimal() {
        try {
            return new BigDecimal(amount);
        } catch (Exception e) {
            return null;
        }
    }
   
    /**
     * Get the amountReceived attribute as a BigDecimal object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public BigDecimal getAmountReceivedAsBigDecimal() {
        try {
            return new BigDecimal(amountReceived);
        } catch (Exception e) {
            return null;
        }
    }
    
    
    /**
     * 
     */
    public void setAmount(String amount) {
        this.amount = amount;
    }

    /**
     * 
     */
    @XmlElement
    public String getAmountReceived() {
        return amountReceived;
    }
   
    /**
     * 
     */
    public void setAmountReceived(String amountReceived) {
        this.amountReceived = amountReceived;
    }
    
    /**
     * Get the currencyRate as a BigDecimal.
     */
    public BigDecimal getCurrencyRateAsBigDecimal() {
        try {
            return new BigDecimal(1);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get the the currency for the currency code.
     */
    public Currency getCurrencyCode() {
    	return CurrencyDAO.getCurrencyByCode("CHF");
    }
    
    /**
     * 
     */
    @XmlElement
    public Map<String, String> getCustomAttributeValue() {
        return this.customAttributeValue;
    }
   
    /**
     * 
     */
    public void setCustomAttributeValue(String attributeName, String attributeValue) {
        this.customAttributeValue.put(attributeName, attributeValue);
    }
    
    @Override
    public String toString() {
        return "Record [sourceRowNumber=" + sourceRowNumber + ", governanceId=" + governanceId + ", dueDate=" + dueDate + ", startDate=" + startDate + ", name="
                + name + ", description=" + description + ", amount=" + amount + ", amountReceived=" + amountReceived +  "]";
               

    }

}
