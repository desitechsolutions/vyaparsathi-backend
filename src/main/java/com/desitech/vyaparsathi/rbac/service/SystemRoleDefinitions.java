package com.desitech.vyaparsathi.rbac.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical permission maps for every system-preset role. This is the
 * single source of truth: {@link RoleSeedRunner} reads it on boot and
 * overwrites the {@code role_permissions} rows for each system role so
 * we can adjust preset behaviour by editing this file and redeploying.
 *
 * <p>Shop-custom roles (created by owners) are untouched by the seeder;
 * only rows where {@code is_system = TRUE} are refreshed.
 *
 * <p>Ordering matters only for display in the role dropdown / matrix
 * (used by the FE via {@link #order}). The permission sets themselves
 * are sets, not lists.
 */
public final class SystemRoleDefinitions {

    private SystemRoleDefinitions() { }

    /**
     * Every permission code in the catalogue. Kept as a small compile-time
     * list so a typo in a preset map fails obviously via
     * {@link RoleSeedRunner#validate()}.
     */
    public static final Set<String> ALL_PERMISSIONS = new LinkedHashSet<>(Arrays.asList(
            "SALES_VIEW","SALES_CREATE","SALES_EDIT","SALES_CANCEL","SALES_DELETE",
            "INVOICE_PRINT","INVOICE_EINVOICE","INVOICE_EWAYBILL",
            "QUOTATION_VIEW","QUOTATION_CREATE",
            "SALES_ORDER_VIEW","SALES_ORDER_CREATE",
            "PROFORMA_CREATE",
            "CREDIT_NOTE_VIEW","CREDIT_NOTE_CREATE",
            "DELIVERY_CHALLAN_VIEW","DELIVERY_CHALLAN_CREATE",
            "PURCHASE_VIEW","PURCHASE_CREATE","PURCHASE_EDIT",
            "PO_VIEW","PO_CREATE","PO_APPROVE",
            "GRN_VIEW","GRN_CREATE","GRN_CONFIRM",
            "PURCHASE_RETURN_CREATE",
            "DEBIT_NOTE_VIEW","DEBIT_NOTE_CREATE",
            "ITEM_VIEW","ITEM_CREATE","ITEM_EDIT","ITEM_DELETE",
            "STOCK_VIEW","STOCK_ADJUST","STOCK_TRANSFER",
            "CATEGORY_MANAGE",
            "CUSTOMER_VIEW","CUSTOMER_CREATE","CUSTOMER_EDIT","CUSTOMER_DELETE",
            "SUPPLIER_VIEW","SUPPLIER_CREATE","SUPPLIER_EDIT","SUPPLIER_DELETE",
            "PAYMENT_VIEW","PAYMENT_RECORD","PAYMENT_REFUND",
            "LEDGER_VIEW",
            "EXPENSE_VIEW","EXPENSE_CREATE",
            "BANK_ACCOUNT_MANAGE",
            "REPORTS_VIEW","REPORTS_EXPORT","GST_REPORTS_VIEW","AUDIT_LOG_VIEW","COMPLIANCE_VIEW",
            "TEAM_VIEW","TEAM_INVITE","TEAM_MANAGE","ROLE_MANAGE",
            "SHOP_SETTINGS_VIEW","SHOP_SETTINGS_EDIT",
            "BILLING_VIEW","BILLING_MANAGE",
            "DASHBOARD_VIEW"
    ));

    /**
     * Set of every permission a role of the given system name grants.
     * OWNER always gets everything (super-user). ADMIN gets everything
     * except role management + billing management. Downstream roles are
     * modelled after Zoho Books.
     */
    public static final Map<String, RoleSpec> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("OWNER", new RoleSpec(
                "Owner", "Full access to everything, including billing and role management.", 1,
                ALL_PERMISSIONS));

        PRESETS.put("ADMIN", new RoleSpec(
                "Admin",
                "Day-to-day admin. Can manage staff, inventory, sales, purchases and settings. Cannot change billing or delete roles.", 2,
                minus(ALL_PERMISSIONS, "ROLE_MANAGE", "BILLING_MANAGE")));

        PRESETS.put("MANAGER", new RoleSpec(
                "Manager",
                "Runs operations. Full sales/purchases/inventory/contacts, view reports. No billing, no team management.", 3,
                asSet(
                        "DASHBOARD_VIEW",
                        "SALES_VIEW","SALES_CREATE","SALES_EDIT","SALES_CANCEL",
                        "INVOICE_PRINT","INVOICE_EINVOICE","INVOICE_EWAYBILL",
                        "QUOTATION_VIEW","QUOTATION_CREATE",
                        "SALES_ORDER_VIEW","SALES_ORDER_CREATE",
                        "PROFORMA_CREATE",
                        "CREDIT_NOTE_VIEW","CREDIT_NOTE_CREATE",
                        "DELIVERY_CHALLAN_VIEW","DELIVERY_CHALLAN_CREATE",
                        "PURCHASE_VIEW","PURCHASE_CREATE","PURCHASE_EDIT",
                        "PO_VIEW","PO_CREATE","PO_APPROVE",
                        "GRN_VIEW","GRN_CREATE","GRN_CONFIRM",
                        "PURCHASE_RETURN_CREATE",
                        "DEBIT_NOTE_VIEW","DEBIT_NOTE_CREATE",
                        "ITEM_VIEW","ITEM_CREATE","ITEM_EDIT",
                        "STOCK_VIEW","STOCK_ADJUST","STOCK_TRANSFER",
                        "CATEGORY_MANAGE",
                        "CUSTOMER_VIEW","CUSTOMER_CREATE","CUSTOMER_EDIT",
                        "SUPPLIER_VIEW","SUPPLIER_CREATE","SUPPLIER_EDIT",
                        "PAYMENT_VIEW","PAYMENT_RECORD",
                        "LEDGER_VIEW",
                        "EXPENSE_VIEW","EXPENSE_CREATE",
                        "REPORTS_VIEW","REPORTS_EXPORT","GST_REPORTS_VIEW"
                )));

        PRESETS.put("ACCOUNTANT", new RoleSpec(
                "Accountant",
                "Owns the books. Payments, ledger, expenses, GST reports. Read-only for sales/purchases.", 4,
                asSet(
                        "DASHBOARD_VIEW",
                        "SALES_VIEW","INVOICE_PRINT",
                        "PURCHASE_VIEW",
                        "PO_VIEW","GRN_VIEW",
                        "CUSTOMER_VIEW","SUPPLIER_VIEW",
                        "PAYMENT_VIEW","PAYMENT_RECORD","PAYMENT_REFUND",
                        "LEDGER_VIEW",
                        "EXPENSE_VIEW","EXPENSE_CREATE",
                        "BANK_ACCOUNT_MANAGE",
                        "REPORTS_VIEW","REPORTS_EXPORT","GST_REPORTS_VIEW","COMPLIANCE_VIEW",
                        "CREDIT_NOTE_VIEW","CREDIT_NOTE_CREATE",
                        "DEBIT_NOTE_VIEW","DEBIT_NOTE_CREATE"
                )));

        PRESETS.put("CASHIER", new RoleSpec(
                "Cashier",
                "POS operator. Create sales & receipts, view items and customers. No purchases, no reports.", 5,
                asSet(
                        "DASHBOARD_VIEW",
                        "SALES_VIEW","SALES_CREATE","INVOICE_PRINT",
                        "QUOTATION_CREATE",
                        "PROFORMA_CREATE",
                        "ITEM_VIEW","STOCK_VIEW",
                        "CUSTOMER_VIEW","CUSTOMER_CREATE","CUSTOMER_EDIT",
                        "PAYMENT_VIEW","PAYMENT_RECORD"
                )));

        PRESETS.put("VIEWER", new RoleSpec(
                "Viewer",
                "Read-only across the whole app. Useful for auditors and shareholders.", 6,
                asSet(
                        "DASHBOARD_VIEW",
                        "SALES_VIEW","INVOICE_PRINT",
                        "QUOTATION_VIEW","SALES_ORDER_VIEW",
                        "CREDIT_NOTE_VIEW","DELIVERY_CHALLAN_VIEW",
                        "PURCHASE_VIEW","PO_VIEW","GRN_VIEW","DEBIT_NOTE_VIEW",
                        "ITEM_VIEW","STOCK_VIEW",
                        "CUSTOMER_VIEW","SUPPLIER_VIEW",
                        "PAYMENT_VIEW","LEDGER_VIEW","EXPENSE_VIEW",
                        "REPORTS_VIEW","GST_REPORTS_VIEW","COMPLIANCE_VIEW",
                        "AUDIT_LOG_VIEW",
                        "TEAM_VIEW","SHOP_SETTINGS_VIEW","BILLING_VIEW"
                )));

        // Backwards compatibility for the legacy STAFF enum — mapped to
        // the same footprint as CASHIER so existing @PreAuthorize("STAFF")
        // callers keep working.
        PRESETS.put("STAFF", new RoleSpec(
                "Staff",
                "Legacy alias — same as Cashier. Kept for compatibility with existing users.", 7,
                PRESETS.get("CASHIER").permissions()));
    }

    /**
     * Compact record — display name, description, order, permission set.
     */
    public record RoleSpec(String displayName, String description, int order, Set<String> permissions) { }

    /**
     * @return the sort order the FE should use for this role name in
     *         dropdowns / lists. Falls back to a large value for custom
     *         roles so they sort after presets.
     */
    public static int orderOf(String roleName) {
        RoleSpec spec = PRESETS.get(roleName);
        return spec != null ? spec.order() : 1000;
    }

    public static List<String> presetNames() {
        return PRESETS.keySet().stream().toList();
    }

    private static Set<String> asSet(String... codes) {
        return new LinkedHashSet<>(Arrays.asList(codes));
    }

    private static Set<String> minus(Set<String> src, String... removes) {
        Set<String> out = new LinkedHashSet<>(src);
        for (String r : removes) out.remove(r);
        return out;
    }
}
