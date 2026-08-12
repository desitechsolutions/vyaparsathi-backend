package com.desitech.vyaparsathi.einvoice.nic;

import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.gst.service.GstJurisdictionService;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link Sale} to the canonical NIC IRP Schema 1.1 payload.
 * The exact shape is dictated by the NIC IRN generation API — see
 * https://einv-apisandbox.nic.in/ for the official schema.
 *
 * The DTO here is a nested Map/List structure so we don't need dozens of
 * one-off classes; Jackson serializes it directly with the JSON field
 * names the IRP expects. Field names match the schema verbatim, so this
 * builder is where NIC vocabulary meets our domain vocabulary.
 */
@Component
public class EInvoicePayloadBuilder {

    private final GstJurisdictionService jurisdictionService;

    public EInvoicePayloadBuilder(GstJurisdictionService jurisdictionService) {
        this.jurisdictionService = jurisdictionService;
    }

    public Map<String, Object> build(Sale sale) {
        Shop shop = sale.getShop();
        Customer customer = sale.getCustomer();
        String sellerStateCode = jurisdictionService.resolveStateCode(shop).orElse("00");
        String buyerStateCode = jurisdictionService.resolveStateCode(customer).orElse(sellerStateCode);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", "1.1");

        Map<String, Object> tranDtls = new LinkedHashMap<>();
        tranDtls.put("TaxSch", "GST");
        tranDtls.put("SupTyp", supplyType(sellerStateCode, buyerStateCode, customer));
        tranDtls.put("RegRev", sale.isReverseCharge() ? "Y" : "N");
        tranDtls.put("IgstOnIntra", "N");
        root.put("TranDtls", tranDtls);

        Map<String, Object> docDtls = new LinkedHashMap<>();
        docDtls.put("Typ", "INV"); // INV | CRN | DBN
        docDtls.put("No", sale.getInvoiceNo());
        docDtls.put("Dt", sale.getDate().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        root.put("DocDtls", docDtls);

        root.put("SellerDtls", partyDetails(shop, sellerStateCode));
        root.put("BuyerDtls", buyerDetails(customer, buyerStateCode));

        List<Map<String, Object>> items = new ArrayList<>();
        int slNo = 1;
        BigDecimal totalAssVal = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalIgst = BigDecimal.ZERO;
        BigDecimal totalCess = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (SaleItem line : sale.getSaleItems()) {
            BigDecimal qty = nz(line.getQty());
            BigDecimal unitPrice = nz(line.getUnitPrice());
            BigDecimal taxable = nz(line.getTaxableValue());
            BigDecimal discount = nz(line.getDiscount());
            BigDecimal cgst = nz(line.getCgstAmt());
            BigDecimal sgst = nz(line.getSgstAmt()).add(nz(line.getUtgstAmt()));
            BigDecimal igst = nz(line.getIgstAmt());
            double rate = line.getGstType() != null ? line.getGstType().getRate() : 0.0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("SlNo", String.valueOf(slNo++));
            item.put("PrdDesc", productDescription(line));
            item.put("IsServc", line.isCatalogLine() ? "N" : "Y");
            item.put("HsnCd", hsn(line));
            item.put("Qty", qty.setScale(3, RoundingMode.HALF_UP));
            item.put("Unit", unit(line));
            item.put("UnitPrice", unitPrice.setScale(2, RoundingMode.HALF_UP));
            item.put("TotAmt", qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
            item.put("Discount", discount.setScale(2, RoundingMode.HALF_UP));
            item.put("AssAmt", taxable.setScale(2, RoundingMode.HALF_UP));
            item.put("GstRt", rate);
            item.put("IgstAmt", igst.setScale(2, RoundingMode.HALF_UP));
            item.put("CgstAmt", cgst.setScale(2, RoundingMode.HALF_UP));
            item.put("SgstAmt", sgst.setScale(2, RoundingMode.HALF_UP));
            item.put("CesRt", 0);
            item.put("CesAmt", BigDecimal.ZERO);
            item.put("CesNonAdvlAmt", BigDecimal.ZERO);
            item.put("StateCesRt", 0);
            item.put("StateCesAmt", BigDecimal.ZERO);
            item.put("StateCesNonAdvlAmt", BigDecimal.ZERO);
            item.put("OthChrg", BigDecimal.ZERO);
            item.put("TotItemVal", taxable.add(cgst).add(sgst).add(igst).setScale(2, RoundingMode.HALF_UP));
            items.add(item);

            totalAssVal = totalAssVal.add(taxable);
            totalCgst = totalCgst.add(cgst);
            totalSgst = totalSgst.add(sgst);
            totalIgst = totalIgst.add(igst);
            totalDiscount = totalDiscount.add(discount);
        }
        root.put("ItemList", items);

        Map<String, Object> valDtls = new LinkedHashMap<>();
        valDtls.put("AssVal", totalAssVal.setScale(2, RoundingMode.HALF_UP));
        valDtls.put("CgstVal", totalCgst.setScale(2, RoundingMode.HALF_UP));
        valDtls.put("SgstVal", totalSgst.setScale(2, RoundingMode.HALF_UP));
        valDtls.put("IgstVal", totalIgst.setScale(2, RoundingMode.HALF_UP));
        valDtls.put("CesVal", totalCess.setScale(2, RoundingMode.HALF_UP));
        valDtls.put("StCesVal", BigDecimal.ZERO);
        valDtls.put("Discount", totalDiscount.setScale(2, RoundingMode.HALF_UP));
        valDtls.put("OthChrg", BigDecimal.ZERO);
        valDtls.put("RndOffAmt", nz(sale.getRoundOff()).setScale(2, RoundingMode.HALF_UP));
        valDtls.put("TotInvVal", nz(sale.getGrandTotal()).setScale(2, RoundingMode.HALF_UP));
        root.put("ValDtls", valDtls);

        return root;
    }

    // ── party details ──────────────────────────────────────────

    private Map<String, Object> partyDetails(Shop shop, String stateCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Gstin", shop.getGstin() != null ? shop.getGstin() : "URP");
        m.put("LglNm", shop.getName());
        m.put("Addr1", shop.getAddress() != null ? shop.getAddress() : "");
        m.put("Loc", shop.getAddress() != null ? shop.getAddress() : "");
        m.put("Pin", 0); // Shop entity doesn't carry postal code today
        m.put("Stcd", stateCode);
        return m;
    }

    private Map<String, Object> buyerDetails(Customer customer, String stateCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (customer == null) {
            m.put("Gstin", "URP");
            m.put("LglNm", "URP");
            m.put("Pos", stateCode);
            m.put("Addr1", "");
            m.put("Loc", "");
            m.put("Pin", 0);
            m.put("Stcd", stateCode);
            return m;
        }
        m.put("Gstin", (customer.getGstNumber() != null && !customer.getGstNumber().isBlank())
                ? customer.getGstNumber() : "URP");
        m.put("LglNm", customer.getName() != null ? customer.getName() : "URP");
        m.put("Pos", stateCode);
        m.put("Addr1", customer.getAddressLine1() != null ? customer.getAddressLine1() : "");
        m.put("Loc", customer.getCity() != null ? customer.getCity() : "");
        m.put("Pin", parsePin(customer.getPostalCode()));
        m.put("Stcd", stateCode);
        return m;
    }

    private static int parsePin(String pin) {
        if (pin == null) return 0;
        try { return Integer.parseInt(pin.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── item accessors ────────────────────────────────────────

    private String productDescription(SaleItem line) {
        if (line.getCustomItemName() != null && !line.getCustomItemName().isBlank()) return line.getCustomItemName();
        if (line.getItemVariant() != null && line.getItemVariant().getItem() != null) {
            return line.getItemVariant().getItem().getName();
        }
        return "Item";
    }

    private String hsn(SaleItem line) {
        if (line.getCustomHsnSac() != null && !line.getCustomHsnSac().isBlank()) return line.getCustomHsnSac();
        if (line.getItemVariant() != null && line.getItemVariant().getHsn() != null) return line.getItemVariant().getHsn();
        return "";
    }

    private String unit(SaleItem line) {
        if (line.getCustomUnit() != null && !line.getCustomUnit().isBlank()) return line.getCustomUnit();
        if (line.getItemVariant() != null && line.getItemVariant().getUnit() != null) return line.getItemVariant().getUnit();
        return "OTH";
    }

    /**
     * Supply-type classification used by the IRP for tax split validation.
     *   B2B — normal buyer with GSTIN
     *   SEZWP — SEZ supply, with payment of tax   (not modeled)
     *   SEZWOP — SEZ supply, without payment      (not modeled)
     *   EXPWP — export with payment               (not modeled)
     *   EXPWOP — export without payment           (not modeled)
     *   DEXP — deemed export                      (not modeled)
     */
    private String supplyType(String sellerStateCode, String buyerStateCode, Customer customer) {
        // For B2C invoices IRP is not typically used; but we still classify for completeness.
        return "B2B";
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
