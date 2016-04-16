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
package services.plugins.system.finance1;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.tuple.Pair;

import dao.finance.CostCenterDAO;
import dao.finance.CurrencyDAO;
import dao.finance.PurchaseOrderDAO;
import dao.finance.SupplierDAO;
import dao.pmo.ActorDao;
import dao.pmo.PortfolioEntryDao;
import framework.services.plugins.loader.toolkit.ILoadableObject;
import framework.utils.Msg;
import models.finance.CostCenter;
import models.finance.Currency;
import models.finance.PurchaseOrder;
import models.finance.PurchaseOrderLineItem;
import models.finance.Supplier;
import models.framework_models.parent.IModelConstants;
import models.pmo.Actor;
import models.pmo.PortfolioEntry;
import play.data.Form;
import play.data.validation.Constraints.MaxLength;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;

/**
 * An object structure which is to be used to load financial data.<br/>
 * This structure is JAXB enabled so that it could support a direct XML load.
 * 
 * @author Johann Kohler
 */
public class FinanceErpIntegrationLoadableObject implements ILoadableObject {

    private static Form<FinanceErpIntegrationLoadableObject> fakeFormTemplate = Form.form(FinanceErpIntegrationLoadableObject.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private long sourceRowNumber;

    @Required(message = "refId must not be null or blank")
    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "refId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String refId;

    @Required(message = "description must not be null or blank")
    @MaxLength(value = IModelConstants.VLARGE_STRING, message = "description is too long " + IModelConstants.VLARGE_STRING + " chars max")
    private String description;

    private String lineId;

    private String quantity;

    private String quantityTotalReceived;

    private String quantityBilled;

    @Required(message = "amount must not be null or blank")
    private String amount;

    private String amountReceived;

    private String amountBilled;

    private String unitPrice;

    @MaxLength(value = IModelConstants.SMALL_STRING, message = "materialCode is too long " + IModelConstants.SMALL_STRING + " chars max")
    private String materialCode;

    @MaxLength(value = IModelConstants.SMALL_STRING, message = "glAccount is too long " + IModelConstants.SMALL_STRING + " chars max")
    private String glAccount;

    @Required(message = "isOpex must not be null or blank")
    private String isOpex;

    @Required(message = "creationDate must not be null or blank")
    private String creationDate;

    private String dueDate;

    @Required(message = "currencyCode must not be null or blank")
    private String currencyCode;

    @Required(message = "currencyRate must not be null or blank")
    private String currencyRate;

    @Required(message = "isCancelled must not be null or blank")
    private String isCancelled;

    @Required(message = "poRefId must not be null or blank")
    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "poRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String poRefId;

    @Required(message = "poDescription must not be null or blank")
    @MaxLength(value = IModelConstants.VLARGE_STRING, message = "poDescription is too long " + IModelConstants.VLARGE_STRING + " chars max")
    private String poDescription;

    @MaxLength(value = IModelConstants.LARGE_STRING, message = "requesterErpRefId is too long " + IModelConstants.LARGE_STRING + " chars max")
    private String requesterErpRefId;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "costCenterRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String costCenterRefId;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "supplierRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String supplierRefId;

    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "poPortfolioEntryErpRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String poPortfolioEntryErpRefId;

    /**
     * Validate the non-string attributes.
     */
    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        if (this.lineId != null && !this.lineId.equals("") && this.getLineIdAsInteger() == null) {
            errors.add(new ValidationError("lineId", "lineId is not correctly formated, should be an Integer"));
        }

        if (this.quantity != null && !this.quantity.equals("") && this.getQuantityAsBigDecimal() == null) {
            errors.add(new ValidationError("quantity", "quantity is not correctly formated, should be a Number"));
        }

        if (this.quantityTotalReceived != null && !this.quantityTotalReceived.equals("") && this.getQuantityTotalReceivedAsBigDecimal() == null) {
            errors.add(new ValidationError("quantityTotalReceived", "quantityTotalReceived is not correctly formated, should be a Number"));
        }

        if (this.quantityBilled != null && !this.quantityBilled.equals("") && this.getQuantityBilledAsBigDecimal() == null) {
            errors.add(new ValidationError("quantityBilled", "quantityBilled is not correctly formated, should be a Number"));
        }

        if (this.amount != null && !this.amount.equals("") && this.getAmountAsBigDecimal() == null) {
            errors.add(new ValidationError("amount", "amount is not correctly formated, should be a Number"));
        }

        if (this.amountReceived != null && !this.amountReceived.equals("") && this.getAmountReceivedAsBigDecimal() == null) {
            errors.add(new ValidationError("amountReceived", "amountReceived is not correctly formated, should be a Number"));
        }

        if (this.amountBilled != null && !this.amountBilled.equals("") && this.getAmountBilledAsBigDecimal() == null) {
            errors.add(new ValidationError("amountBilled", "amountBilled is not correctly formated, should be a Number"));
        }

        if (this.unitPrice != null && !this.unitPrice.equals("") && this.getUnitPriceAsBigDecimal() == null) {
            errors.add(new ValidationError("unitPrice", "unitPrice is not correctly formated, should be a Number"));
        }

        if (this.isOpex != null && !this.isOpex.equals("") && this.getIsOpexAsBoolean() == null) {
            errors.add(new ValidationError("isOpex", "isOpex is not correctly formated, should be a true or false"));
        }

        if (this.creationDate != null && !this.creationDate.equals("") && this.getCreationDateAsDate() == null) {
            errors.add(new ValidationError("creationDate", "creationDate is not correctly formated, use the format " + DATE_FORMAT));
        }

        if (this.dueDate != null && !this.dueDate.equals("") && this.getDueDateAsDate() == null) {
            errors.add(new ValidationError("dueDate", "dueDate is not correctly formated, use the format " + DATE_FORMAT));
        }

        if (this.isCancelled != null && !this.isCancelled.equals("") && this.getIsCancelledAsBoolean() == null) {
            errors.add(new ValidationError("isCancelled", "isCancelled is not correctly formated, should be a true or false"));
        }

        if (this.currencyCode != null && !this.currencyCode.equals("") && this.getCurrency() == null) {
            errors.add(new ValidationError("currencyCode", String.format("Currency %s does not exist", this.currencyCode)));
        }

        if (this.currencyRate != null && !this.currencyRate.equals("") && this.getCurrencyRateAsBigDecimal() == null) {
            errors.add(new ValidationError("currencyRate", "currencyRate is not correctly formated, should be a Number"));
        }

        if (this.requesterErpRefId != null && !this.requesterErpRefId.equals("") && this.getRequester() == null) {
            errors.add(new ValidationError("requesterErpRefId", String.format("Actor %s does not exist", this.requesterErpRefId)));
        }

        if (this.costCenterRefId != null && !this.costCenterRefId.equals("") && this.getCostCenter() == null) {
            errors.add(new ValidationError("costCenterRefId", String.format("CostCenter %s does not exist", this.costCenterRefId)));
        }

        if (this.supplierRefId != null && !this.supplierRefId.equals("") && this.getSupplier() == null) {
            errors.add(new ValidationError("supplierRefId", String.format("Supplier %s does not exist", this.supplierRefId)));
        }

        if (this.poPortfolioEntryErpRefId != null && !this.poPortfolioEntryErpRefId.equals("") && this.getPoPortfolioEntry() == null) {
            errors.add(new ValidationError("poPortfolioEntryErpRefId", String.format("PortfolioEntry %s does not exist", this.poPortfolioEntryErpRefId)));
        }

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Default constructor.
     */
    public FinanceErpIntegrationLoadableObject() {
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
        return updateOrCreatePurchaseOrderLineItem();
    }

    /**
     * Validate the object consistency against various rules.
     */
    public Pair<Boolean, String> validateAndComplete() {

        // Perform a validation using Play form features
        Form<FinanceErpIntegrationLoadableObject> fakeForm = null;

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
     * Update or create the line item.<br/>
     * If a new line item is created then the method returns the newly created
     * Id.<br/>
     * This method must be called after "validateAndComplete"
     * 
     * @return a tuple (id of line item, refId) or null
     */
    Pair<Long, String> updateOrCreatePurchaseOrderLineItem() {

        boolean isNew = false;

        PurchaseOrder purchaseOrder = PurchaseOrderDAO.getPurchaseOrderByRefId(this.getPoRefId());
        PurchaseOrderLineItem lineItem = PurchaseOrderDAO.getPurchaseOrderLineItemByRefId(this.getRefId());

        // purchase order
        if (purchaseOrder == null) { // new
            purchaseOrder = new PurchaseOrder();
            purchaseOrder.refId = this.getPoRefId();
        }
        purchaseOrder.isCancelled = false;
        purchaseOrder.description = this.getPoDescription();
        purchaseOrder.portfolioEntry = this.getPoPortfolioEntry();
        purchaseOrder.save();

        // line item
        if (lineItem == null) { // new
            isNew = true;
            lineItem = new PurchaseOrderLineItem();
            lineItem.refId = this.getRefId();
            lineItem.shipmentType = null;
        }
        lineItem.description = this.getDescription();
        lineItem.currency = this.getCurrency();
        lineItem.currencyRate = this.getCurrencyRateAsBigDecimal();
        lineItem.lineId = this.getLineIdAsInteger();
        lineItem.supplier = this.getSupplier();
        lineItem.quantity = this.getQuantityAsBigDecimal();
        lineItem.quantityTotalReceived = this.getQuantityTotalReceivedAsBigDecimal();
        lineItem.quantityBilled = this.getQuantityBilledAsBigDecimal();
        lineItem.amount = this.getAmountAsBigDecimal();
        lineItem.amountReceived = this.getAmountReceivedAsBigDecimal();
        lineItem.amountBilled = this.getAmountBilledAsBigDecimal();
        lineItem.unitPrice = this.getUnitPriceAsBigDecimal();
        lineItem.materialCode = this.getMaterialCode();
        lineItem.glAccount = this.getGlAccount();
        lineItem.isOpex = this.getIsOpexAsBoolean();
        lineItem.creationDate = this.getCreationDateAsDate();
        lineItem.dueDate = this.getDueDateAsDate();
        lineItem.requester = this.getRequester();
        lineItem.costCenter = this.getCostCenter();
        lineItem.purchaseOrder = purchaseOrder;
        lineItem.isCancelled = this.getIsCancelledAsBoolean();
        lineItem.save();

        if (isNew) {
            return Pair.of(lineItem.id, lineItem.refId);
        }

        return null;

    }

    /**
     * @return the refId
     */
    @XmlElement
    public String getRefId() {
        return refId;
    }

    /**
     * @param refId
     *            the refId to set
     */
    public void setRefId(String refId) {
        this.refId = refId;
    }

    /**
     * @return the description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    /**
     * @param description
     *            the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the lineId
     */
    @XmlElement
    public String getLineId() {
        return lineId;
    }

    /**
     * Get the lineId attribute as an Integer object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public Integer getLineIdAsInteger() {
        try {
            return Integer.valueOf(lineId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param lineId
     *            the lineId to set
     */
    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    /**
     * @return the quantity
     */
    @XmlElement
    public String getQuantity() {
        return quantity;
    }

    /**
     * Get the quantity attribute as a BigDecimal object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public BigDecimal getQuantityAsBigDecimal() {
        try {
            return new BigDecimal(quantity);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param quantity
     *            the quantity to set
     */
    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    /**
     * @return the quantityTotalReceived
     */
    @XmlElement
    public String getQuantityTotalReceived() {
        return quantityTotalReceived;
    }

    /**
     * Get the quantityTotalReceived attribute as a BigDecimal object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public BigDecimal getQuantityTotalReceivedAsBigDecimal() {
        try {
            return new BigDecimal(quantityTotalReceived);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param quantityTotalReceived
     *            the quantityTotalReceived to set
     */
    public void setQuantityTotalReceived(String quantityTotalReceived) {
        this.quantityTotalReceived = quantityTotalReceived;
    }

    /**
     * @return the quantityBilled
     */
    @XmlElement
    public String getQuantityBilled() {
        return quantityBilled;
    }

    /**
     * Get the quantityBilled attribute as a BigDecimal object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public BigDecimal getQuantityBilledAsBigDecimal() {
        try {
            return new BigDecimal(quantityBilled);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param quantityBilled
     *            the quantityBilled to set
     */
    public void setQuantityBilled(String quantityBilled) {
        this.quantityBilled = quantityBilled;
    }

    /**
     * @return the amount
     */
    @XmlElement
    public String getAmount() {
        return amount;
    }

    /**
     * Get the amount attribute as a BigDecimal object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public BigDecimal getAmountAsBigDecimal() {
        try {
            return new BigDecimal(amount);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param amount
     *            the amount to set
     */
    public void setAmount(String amount) {
        this.amount = amount;
    }

    /**
     * @return the amountReceived
     */
    @XmlElement
    public String getAmountReceived() {
        return amountReceived;
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
     * @param amountReceived
     *            the amountReceived to set
     */
    public void setAmountReceived(String amountReceived) {
        this.amountReceived = amountReceived;
    }

    /**
     * @return the amountBilled
     */
    @XmlElement
    public String getAmountBilled() {
        return amountBilled;
    }

    /**
     * Get the amountBilled attribute as a BigDecimal object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public BigDecimal getAmountBilledAsBigDecimal() {
        try {
            return new BigDecimal(amountBilled);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param amountBilled
     *            the amountBilled to set
     */
    public void setAmountBilled(String amountBilled) {
        this.amountBilled = amountBilled;
    }

    /**
     * @return the unitPrice
     */
    @XmlElement
    public String getUnitPrice() {
        return unitPrice;
    }

    /**
     * Get the unitPrice attribute as a BigDecimal object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public BigDecimal getUnitPriceAsBigDecimal() {
        try {
            return new BigDecimal(unitPrice);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param unitPrice
     *            the unitPrice to set
     */
    public void setUnitPrice(String unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * @return the materialCode
     */
    @XmlElement
    public String getMaterialCode() {
        return materialCode;
    }

    /**
     * @param materialCode
     *            the materialCode to set
     */
    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    /**
     * @return the glAccount
     */
    @XmlElement
    public String getGlAccount() {
        return glAccount;
    }

    /**
     * @param glAccount
     *            the glAccount to set
     */
    public void setGlAccount(String glAccount) {
        this.glAccount = glAccount;
    }

    /**
     * @return the isOpex
     */
    @XmlElement
    public String getIsOpex() {
        return isOpex;
    }

    /**
     * Get the isOpex attribute as a Boolean object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public Boolean getIsOpexAsBoolean() {
        if (this.isOpex == null) {
            return null;
        } else if (this.isOpex.toLowerCase().equals("true")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param isOpex
     *            the isOpex to set
     */
    public void setIsOpex(String isOpex) {
        this.isOpex = isOpex;
    }

    /**
     * @return the creationDate
     */
    @XmlElement
    public String getCreationDate() {
        return creationDate;
    }

    /**
     * Get the creationDate attribute as a Date object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public Date getCreationDateAsDate() {
        try {
            DateFormat format = new SimpleDateFormat(DATE_FORMAT);
            return format.parse(this.creationDate);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * @param creationDate
     *            the creationDate to set
     */
    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * @return the dueDate
     */
    @XmlElement
    public String getDueDate() {
        return dueDate;
    }

    /**
     * Get the dueDate attribute as a Date object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public Date getDueDateAsDate() {
        try {
            DateFormat format = new SimpleDateFormat(DATE_FORMAT);
            return format.parse(this.dueDate);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * @param dueDate
     *            the dueDate to set
     */
    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    /**
     * @return the currencyCode
     */
    @XmlElement
    public String getCurrencyCode() {
        return currencyCode;
    }

    /**
     * Get the the currency for the currency code.
     */
    public Currency getCurrency() {
        return CurrencyDAO.getCurrencyByCode(this.getCurrencyCode());
    }

    /**
     * @param currencyCode
     *            the currencyCode to set
     */
    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    /**
     * @return the currencyRate
     */
    @XmlElement
    public String getCurrencyRate() {
        return this.currencyRate;
    }

    /**
     * Get the currencyRate as a BigDecimal.
     */
    public BigDecimal getCurrencyRateAsBigDecimal() {
        try {
            return new BigDecimal(this.currencyRate);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param currencyRate
     *            the currencyRate to set
     */
    public void setCurrencyRate(String currencyRate) {
        this.currencyRate = currencyRate;
    }

    /**
     * @return the isCancelled
     */
    @XmlElement
    public String getIsCancelled() {
        return isCancelled;
    }

    /**
     * Get the isCancelled attribute as a Boolean object.
     * 
     * Return null if null/empty or if an error occurs.
     */
    public Boolean getIsCancelledAsBoolean() {
        if (this.isCancelled == null) {
            return null;
        } else if (this.isCancelled.toLowerCase().equals("true")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param isCancelled
     *            the isCancelled to set
     */
    public void setIsCancelled(String isCancelled) {
        this.isCancelled = isCancelled;
    }

    /**
     * @return the poRefId
     */
    @XmlElement
    public String getPoRefId() {
        return poRefId;
    }

    /**
     * @param poRefId
     *            the poRefId to set
     */
    public void setPoRefId(String poRefId) {
        this.poRefId = poRefId;
    }

    /**
     * @return the poDescription
     */
    @XmlElement
    public String getPoDescription() {
        return poDescription;
    }

    /**
     * @param poDescription
     *            the poDescription to set
     */
    public void setPoDescription(String poDescription) {
        this.poDescription = poDescription;
    }

    /**
     * @return the requesterErpRefId
     */
    @XmlElement
    public String getRequesterErpRefId() {
        return requesterErpRefId;
    }

    /**
     * Get the requester for the requester erp ref id.
     */
    public Actor getRequester() {
        return ActorDao.getActorByErpRefId(this.getRequesterErpRefId());
    }

    /**
     * @param requesterErpRefId
     *            the requesterErpRefId to set
     */
    public void setRequesterErpRefId(String requesterErpRefId) {
        this.requesterErpRefId = requesterErpRefId;
    }

    /**
     * @return the costCenterRefId
     */
    @XmlElement
    public String getCostCenterRefId() {
        return costCenterRefId;
    }

    /**
     * Get the cost center for the cost center ref id.
     */
    public CostCenter getCostCenter() {
        return CostCenterDAO.getCostCenterByRefId(this.getCostCenterRefId());
    }

    /**
     * @param costCenterRefId
     *            the costCenterRefId to set
     */
    public void setCostCenterRefId(String costCenterRefId) {
        this.costCenterRefId = costCenterRefId;
    }

    /**
     * @return the supplierRefId
     */
    @XmlElement
    public String getSupplierRefId() {
        return supplierRefId;
    }

    /**
     * Get the supplier for the supplier ref id.
     */
    public Supplier getSupplier() {
        return SupplierDAO.getSupplierByRefId(this.getSupplierRefId());
    }

    /**
     * @param supplierRefId
     *            the supplierRefId to set
     */
    public void setSupplierRefId(String supplierRefId) {
        this.supplierRefId = supplierRefId;
    }

    /**
     * @return the poPortfolioEntryErpRefId
     */
    @XmlElement
    public String getPoPortfolioEntryErpRefId() {
        return poPortfolioEntryErpRefId;
    }

    /**
     * Get the portfolio entry of the PO for the po portfolio entry erp ref id.
     */
    public PortfolioEntry getPoPortfolioEntry() {
        return PortfolioEntryDao.getPEByErpRefId(this.getPoPortfolioEntryErpRefId());
    }

    /**
     * @param poPortfolioEntryErpRefId
     *            the poPortfolioEntryErpRefId to set
     */
    public void setPoPortfolioEntryErpRefId(String poPortfolioEntryErpRefId) {
        this.poPortfolioEntryErpRefId = poPortfolioEntryErpRefId;
    }

    @Override
    public String toString() {
        return "Record [sourceRowNumber=" + sourceRowNumber + ", refId=" + refId + ", description=" + description + ", lineId=" + lineId + ", quantity="
                + quantity + ", quantityTotalReceived=" + quantityTotalReceived + ", quantityBilled=" + quantityBilled + ", amount=" + amount
                + ", amountReceived=" + amountReceived + ", amountBilled=" + amountBilled + ", unitPrice=" + unitPrice + ", materialCode=" + materialCode
                + ", glAccount=" + glAccount + ", isOpex=" + isOpex + ", creationDate=" + creationDate + ", dueDate=" + dueDate + ", currencyCode="
                + currencyCode + ", currencyRate=" + currencyRate + ", isCancelled=" + isCancelled + ", poRefId=" + poRefId + ", poDescription="
                + poDescription + ", requesterErpRefId=" + requesterErpRefId + ", costCenterRefId=" + costCenterRefId + ", supplierRefId=" + supplierRefId
                + ", poPortfolioEntryErpRefId=" + poPortfolioEntryErpRefId + "]";
    }

}
