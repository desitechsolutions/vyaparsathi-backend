-- =====================================================================
-- V109 — Permission-based RBAC (Phase 5A)
-- =====================================================================
-- Introduces the three-table foundation that replaces hardcoded role
-- checks with a granular, per-shop permission model:
--
--   permissions       — canonical catalogue of every gate (~50 codes).
--                       Global; not per-shop.
--   roles             — one row per (shop, role-name).
--                       * system-preset roles (OWNER, ADMIN, MANAGER,
--                         ACCOUNTANT, CASHIER, VIEWER, STAFF) live in
--                         EVERY shop but are marked is_system=TRUE and
--                         cannot be edited.
--                       * shop-custom roles have is_system=FALSE and can
--                         be edited / deleted by shop OWNER/ADMIN.
--   role_permissions  — many-to-many between roles and permissions.
--
-- The user.role enum stays for now — the RBAC bridge (PermissionResolver)
-- maps a user's legacy role to the matching system-preset role in their
-- active shop and expands to its permission set on every request. When
-- the multi-shop membership arrives in V110, per-membership roles win
-- over the legacy enum.
-- =====================================================================

CREATE TABLE IF NOT EXISTS permissions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(64) NOT NULL UNIQUE,
    module       VARCHAR(32) NOT NULL,   -- SALES / INVENTORY / CONTACTS / …
    description  VARCHAR(255) NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_permissions_module (module)
);

CREATE TABLE IF NOT EXISTS roles (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT NULL,             -- NULL only for platform-level SUPER_ADMIN/TECH_ADMIN roles (future)
    name         VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NULL,
    description  VARCHAR(255) NULL,
    is_system    BOOLEAN NOT NULL DEFAULT FALSE,   -- system-preset (uneditable) vs shop-custom
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_roles_shop
        FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE,
    CONSTRAINT uk_roles_shop_name UNIQUE (shop_id, name),
    INDEX idx_roles_shop (shop_id),
    INDEX idx_roles_system (is_system)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id         BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    granted_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_code) REFERENCES permissions(code) ON DELETE CASCADE,
    INDEX idx_role_permissions_permission (permission_code)
);

-- ---------------------------------------------------------------------
-- Seed the permission catalogue. Everything is grouped by module for
-- easy display in the permission-matrix UI.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO permissions (code, module, description) VALUES
  -- Sales / Invoicing
  ('SALES_VIEW',            'SALES',      'View sales and invoices'),
  ('SALES_CREATE',          'SALES',      'Create sales invoices'),
  ('SALES_EDIT',            'SALES',      'Edit unposted sales invoices'),
  ('SALES_CANCEL',          'SALES',      'Cancel sales invoices'),
  ('SALES_DELETE',          'SALES',      'Delete draft sales invoices'),
  ('INVOICE_PRINT',         'SALES',      'Print / share invoices as PDF'),
  ('INVOICE_EINVOICE',      'SALES',      'Generate e-invoice IRN & QR'),
  ('INVOICE_EWAYBILL',      'SALES',      'Generate e-way bill'),
  ('QUOTATION_VIEW',        'SALES',      'View quotations'),
  ('QUOTATION_CREATE',      'SALES',      'Create / edit quotations'),
  ('SALES_ORDER_VIEW',      'SALES',      'View sales orders'),
  ('SALES_ORDER_CREATE',    'SALES',      'Create / edit sales orders'),
  ('PROFORMA_CREATE',       'SALES',      'Create proforma invoices'),
  ('CREDIT_NOTE_VIEW',      'SALES',      'View credit notes'),
  ('CREDIT_NOTE_CREATE',    'SALES',      'Create credit notes'),
  ('DELIVERY_CHALLAN_VIEW', 'SALES',      'View delivery challans'),
  ('DELIVERY_CHALLAN_CREATE','SALES',     'Create delivery challans'),

  -- Purchases
  ('PURCHASE_VIEW',         'PURCHASES',  'View purchase invoices'),
  ('PURCHASE_CREATE',       'PURCHASES',  'Create purchase invoices'),
  ('PURCHASE_EDIT',         'PURCHASES',  'Edit purchase invoices'),
  ('PO_VIEW',               'PURCHASES',  'View purchase orders'),
  ('PO_CREATE',             'PURCHASES',  'Create / edit purchase orders'),
  ('PO_APPROVE',            'PURCHASES',  'Approve purchase orders above threshold'),
  ('GRN_VIEW',              'PURCHASES',  'View goods-receipt notes'),
  ('GRN_CREATE',            'PURCHASES',  'Create / edit GRNs'),
  ('GRN_CONFIRM',           'PURCHASES',  'Confirm GRNs (posts stock)'),
  ('PURCHASE_RETURN_CREATE','PURCHASES',  'Create purchase returns'),
  ('DEBIT_NOTE_VIEW',       'PURCHASES',  'View debit notes'),
  ('DEBIT_NOTE_CREATE',     'PURCHASES',  'Create debit notes'),

  -- Inventory
  ('ITEM_VIEW',             'INVENTORY',  'View items and variants'),
  ('ITEM_CREATE',           'INVENTORY',  'Create items and variants'),
  ('ITEM_EDIT',             'INVENTORY',  'Edit items and variants'),
  ('ITEM_DELETE',           'INVENTORY',  'Delete items'),
  ('STOCK_VIEW',            'INVENTORY',  'View stock levels'),
  ('STOCK_ADJUST',          'INVENTORY',  'Adjust stock (increase/decrease)'),
  ('STOCK_TRANSFER',        'INVENTORY',  'Transfer stock between locations'),
  ('CATEGORY_MANAGE',       'INVENTORY',  'Manage categories'),

  -- Contacts
  ('CUSTOMER_VIEW',         'CONTACTS',   'View customers'),
  ('CUSTOMER_CREATE',       'CONTACTS',   'Create customers'),
  ('CUSTOMER_EDIT',         'CONTACTS',   'Edit customers'),
  ('CUSTOMER_DELETE',       'CONTACTS',   'Delete customers'),
  ('SUPPLIER_VIEW',         'CONTACTS',   'View suppliers'),
  ('SUPPLIER_CREATE',       'CONTACTS',   'Create suppliers'),
  ('SUPPLIER_EDIT',         'CONTACTS',   'Edit suppliers'),
  ('SUPPLIER_DELETE',       'CONTACTS',   'Delete suppliers'),

  -- Accounting
  ('PAYMENT_VIEW',          'ACCOUNTING', 'View payments'),
  ('PAYMENT_RECORD',        'ACCOUNTING', 'Record customer / supplier payments'),
  ('PAYMENT_REFUND',        'ACCOUNTING', 'Issue refunds'),
  ('LEDGER_VIEW',           'ACCOUNTING', 'View ledgers'),
  ('EXPENSE_VIEW',          'ACCOUNTING', 'View expenses'),
  ('EXPENSE_CREATE',        'ACCOUNTING', 'Create expenses'),
  ('BANK_ACCOUNT_MANAGE',   'ACCOUNTING', 'Manage bank accounts'),

  -- Reports & compliance
  ('REPORTS_VIEW',          'REPORTS',    'View business reports'),
  ('REPORTS_EXPORT',        'REPORTS',    'Export reports (CSV / Excel / PDF)'),
  ('GST_REPORTS_VIEW',      'REPORTS',    'View GST returns data'),
  ('AUDIT_LOG_VIEW',        'REPORTS',    'View audit logs'),
  ('COMPLIANCE_VIEW',       'REPORTS',    'View compliance dashboard'),

  -- Team & settings
  ('TEAM_VIEW',             'TEAM',       'View team members'),
  ('TEAM_INVITE',           'TEAM',       'Invite team members'),
  ('TEAM_MANAGE',           'TEAM',       'Activate / deactivate team members'),
  ('ROLE_MANAGE',           'TEAM',       'Create / edit custom roles'),
  ('SHOP_SETTINGS_VIEW',    'SETTINGS',   'View shop settings'),
  ('SHOP_SETTINGS_EDIT',    'SETTINGS',   'Edit shop settings'),
  ('BILLING_VIEW',          'SETTINGS',   'View subscription & billing'),
  ('BILLING_MANAGE',        'SETTINGS',   'Manage subscription plan'),
  ('DASHBOARD_VIEW',        'DASHBOARD',  'View business dashboard');
