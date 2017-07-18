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


import controllers.admin.UserManager;
import dao.finance.CurrencyDAO;
import dao.pmo.PortfolioEntryDao;
import framework.services.plugins.loader.toolkit.ILoadableObject;
import framework.utils.Msg;
import models.finance.Currency;
import models.finance.WorkOrder;
import models.framework_models.common.CustomAttributeDefinition;
import models.framework_models.common.ICustomAttributeValue;
import models.pmo.Actor;
import models.pmo.ActorType;
import models.pmo.OrgUnit;
import models.pmo.PortfolioEntry;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.tuple.Pair;
import play.data.Form;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;

import javax.xml.bind.annotation.XmlElement;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.*;
import java.util.*;

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

    private long sourceRowNumber;

    private String dateFormat = "yyyy-MM-dd";

    private char numberGroupingSeparator = ',';

    private char numberDecimalSeparator = '.';

    @Required(message = "Governance ID must not be null or blank")
    private String governanceId;
    
    @Required
    private String dueDate;
   
    @Required
    private String startDate;
    
    @Required(message = "name must not be null or blank")
    private String name;
    
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
            errors.add(new ValidationError("dueDate", "No valid due date or invalid format (must be "+getDateFormat()+")"));
        }
        
        if(this.startDate !=null && !this.startDate.equals("") && this.getStartDateAsDate()==null){
            errors.add(new ValidationError("startDate", "No valid start date or invalid format (must be "+getDateFormat()+")"));
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
    	customAttributeValue = new HashMap<>();
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
        WorkOrder workOrder = new WorkOrder();

        PortfolioEntry pe =PortfolioEntryDao.getPEByGovernanceId(this.governanceId);

        workOrder.deleted = false;
        workOrder.lastUpdate = new Timestamp(System.currentTimeMillis());
        workOrder.name = this.getName();
        workOrder.description = this.getDescription();
        workOrder.amount = getAmountAsBigDecimal();
        workOrder.amountReceived = getAmountReceivedAsBigDecimal();
        workOrder.isOpex = false;
        workOrder.isEngaged = true;
        workOrder.creationDate = new Date();
        workOrder.dueDate = this.getDueDateAsDate();
        workOrder.startDate = this.getStartDateAsDate();
        workOrder.currency = getCurrencyCode();
        workOrder.currencyRate = getCurrencyRateAsBigDecimal();
        workOrder.shared = false;

        workOrder.portfolioEntry = pe;

        workOrder.save();

        // save custom attributes values
        saveCustomAttirbutesValues(customAttributeValue, workOrder.getClass(), workOrder.id);

        return Pair.of(workOrder.id, workOrder.name);
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

    public boolean saveCustomAttirbutesValues(Map<String, String> data, Class<?> clazz,  Long objectId) {
       
    	boolean hasErrors = false;
    	
        if (data != null) {
            List<ICustomAttributeValue> customAttributeValues =CustomAttributeDefinition.getOrderedCustomAttributeValues(clazz, objectId);

            if (customAttributeValues != null) {
                for (ICustomAttributeValue customAttributeValue : customAttributeValues) {

                    String fieldName = CUSTOM_ATTRIBUTE_FORM_FIELD_NAME_EXTENSION + customAttributeValue.getDefinition().uuid;

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
    
    public void setGovernanceId(String governanceId) {
        this.governanceId = governanceId;
    }
        
    @XmlElement
    public String getGovernanceId() {
        return governanceId;
    }

    
    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }
    
    @XmlElement
    public String getDueDate() {
        return dueDate;
    }
   
    public Date getDueDateAsDate() {
        try {
            DateFormat format = new SimpleDateFormat(getDateFormat());
            return format.parse(this.dueDate);
        } catch (ParseException e) {
            return null;
        }
    }
    
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }
    
    @XmlElement
    public String getStartDate() {
        return startDate;
    }
   
    public Date getStartDateAsDate() {
        try {
            DateFormat format = new SimpleDateFormat(getDateFormat());
            return format.parse(this.startDate);
        } catch (ParseException e) {
            return null;
        }
    }
    
    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
    @XmlElement
    public String getAmount() {
        return amount;
    }
    
    public BigDecimal getAmountAsBigDecimal() {
        try {
            return new BigDecimal(parseNumber(amount).doubleValue());
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
            return new BigDecimal(parseNumber(amountReceived).doubleValue());
        } catch (Exception e) {
            return null;
        }
    }

    private Number parseNumber(String stringNumber) throws ParseException {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(getNumberGroupingSeparator());
        symbols.setDecimalSeparator(getNumberDecimalSeparator());
        DecimalFormat df = new DecimalFormat("###,###.##", symbols);
        return df.parse(stringNumber);
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

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public char getNumberDecimalSeparator() {
        return numberDecimalSeparator;
    }

    public void setNumberDecimalSeparator(char numberDecimalSeparator) {
        this.numberDecimalSeparator = numberDecimalSeparator;
    }

    public char getNumberGroupingSeparator() {
        return numberGroupingSeparator;
    }

    public void setNumberGroupingSeparator(char numberGroupingSeparator) {
        this.numberGroupingSeparator = numberGroupingSeparator;
    }
}
