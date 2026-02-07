VyaparSathi Backend
===================

### Overview

This is the backend for **VyaparSathi**, a powerful, offline-first shop management application. It is designed to serve as the core business logic for shops in India, particularly in village and city environments. The system handles all critical aspects of retail business management, including sales, inventory, customer relationships, and financial reporting.

### Features

*   **Offline-First Architecture**: Uses a local SQLite database for uninterrupted operation, with future plans for data synchronization.

*   **Secure Authentication**: Implements a robust JSON Web Token (**JWT**) based authentication system to secure API endpoints.

*   **Comprehensive Inventory Management**: Advanced inventory tracking with cost-per-unit, FIFO-based COGS calculation, stock movement history, low stock alerts, and manual stock adjustments with full audit trails.

*   **Sales and Billing**: Enhanced sales system with automatic COGS calculation, sales returns, complete sale cancellations, and profit reporting capabilities. Generates professional PDF invoices using **OpenPDF**.

*   **Customer & Supplier Management**: Manages detailed information for both customers and suppliers with complete ledger transaction tracking.

*   **Financial Tracking**: Records and manages business expenses with strict validation to separate operational costs from inventory purchases. Features comprehensive Cost of Goods Sold (COGS) tracking using FIFO method for accurate profit analysis.

*   **Advanced Financial Reports**: Comprehensive reporting including sales summaries, profit analysis with gross margins, COGS tracking, daily reports, and GST breakdowns with proper distinction between inventory costs and operational expenses.

*   **API Documentation**: Provides clear and interactive API documentation with **Swagger UI**.

*   **Database Migrations**: Manages database schema evolution using **Flyway**.


### Technologies

*   **Java 17**: The primary programming language.

*   **Spring Boot 3.x**: The application framework for building the backend services.

*   **SQLite**: The embedded database for offline-first data storage.

*   **Spring Data JPA & Hibernate**: For database interaction and object-relational mapping.

*   **Spring Security**: Handles user authentication and authorization.

*   **JWT (jjwt)**: For creating and verifying secure access tokens.

*   **Flyway**: Manages database schema versions and migrations.

*   **OpenPDF**: For generating professional PDF invoices.

*   **Springdoc OpenAPI**: Automatically generates and serves API documentation.

*   **Lombok**: Reduces boilerplate code in Java classes.

*   **MapStruct**: A code generator for creating type-safe mappers.


### Running the Project

#### Prerequisites

*   Java Development Kit (JDK) 17 or higher

*   Maven 3.8+


#### Run Locally

1.  Clone the repository.

2.  Navigate to the project root directory in your terminal.

3.  mvn clean spring-boot:run


#### API Documentation

Once the application is running, you can access the API documentation:

*   **Swagger UI**: http://localhost:8080/swagger-ui.html

*   **OpenAPI JSON**: http://localhost:8080/v3/api-docs


### Project Structure

The project is organized into logical modules, each handling a specific business function.

*   auth: Manages user authentication, roles, and password reset tokens.

*   shop: Handles shop-related data and configuration.

*   catalog: Deals with item and product variant definitions.

*   inventory: **ENHANCED**: Comprehensive stock management with cost tracking, FIFO COGS calculation, stock movement history, low stock alerts, and manual adjustments with audit trails.

*   customer: Manages customer information and their financial ledger.

*   sales: **ENHANCED**: Advanced sales processing with COGS calculation, sales returns, cancellations, and profit reporting capabilities including invoice generation.

*   expense: Tracks and manages operational business expenses. **IMPORTANT**: Includes validation to prevent inventory/stock purchases from being recorded as expenses - use Purchase Orders instead.

*   reports: **ENHANCED**: Advanced financial reporting with COGS calculation, profit margins, corrected net profit calculations, and clear distinction between operational expenses and inventory costs.

*   changelog: Provides an audit trail for all data modifications.


### Database & Logging

*   **Database File**: The SQLite database file is located at ./data/shop\_data.db.

*   **Logs**: Application logs are written to logs/vyaparsathi.log.


### Configuration

*   **JWT**: The JWT secret key and expiration time are configured in src/main/resources/application.properties.

*   **Flyway**: Migration scripts are located in src/main/resources/db/migration and are executed automatically on application startup.

### Enhanced Inventory & Sales Features

This version introduces comprehensive improvements to inventory and sales management with advanced cost tracking and business intelligence capabilities:

#### 🔄 Advanced Stock Management

*   **Cost-Per-Unit Tracking**: Every stock entry now tracks the actual cost, enabling accurate COGS calculation
*   **FIFO COGS Calculation**: Uses First-In-First-Out method to calculate cost of goods sold for precise profit analysis
*   **Complete Stock Movement History**: Comprehensive audit trail for all stock operations (additions, deductions, adjustments)
*   **Low Stock Alerts**: Configurable threshold-based alerts with severity levels (LOW/CRITICAL)
*   **Manual Stock Adjustments**: Controlled stock adjustments with mandatory reason tracking for audit compliance

#### 💰 Enhanced Sales Processing

*   **Automatic COGS Calculation**: Real-time cost of goods sold calculation using FIFO methodology
*   **Sales Returns**: Partial and complete return processing with automatic stock restoration and payment reversal
*   **Sale Cancellations**: Complete transaction reversal including stock, payments, and ledger entries
*   **Profit Analysis**: Real-time gross profit and margin calculations with detailed reporting

#### 📊 Business Intelligence & Reporting

*   **Profit Reporting**: Comprehensive profit analysis with revenue, COGS, and margin percentages
*   **Stock Movement Reports**: Detailed movement history for inventory auditing and trend analysis
*   **Alert System**: Proactive low stock notifications to prevent stockouts
*   **Complete Audit Trail**: Full transaction history for compliance and business analysis

#### 🔒 Enhanced Security & Compliance

*   **Audit Logging**: All operations logged through changelog system for complete traceability
*   **Reason Tracking**: Mandatory reason codes for all manual adjustments and cancellations
*   **Transaction Integrity**: Complete rollback capabilities for cancelled operations
*   **Role-Based Access**: Existing security model extended to all new endpoints

### Financial Reporting Improvements

This version includes significant improvements to financial reporting and business logic:

#### Key Changes

*   **Expense Validation**: The system now prevents inventory/stock purchases from being recorded as business expenses. Only operational expenses (rent, utilities, salary, marketing, etc.) can be recorded in the expense module.

*   **COGS Calculation**: Implemented Cost of Goods Sold (COGS) calculation using FIFO method based on actual purchase costs from purchase orders, providing accurate profit analysis.

*   **Corrected Net Profit**: Net profit is now calculated as: `Total Sales - COGS - Operational Expenses` (excludes inventory purchases).

*   **Outstanding Receivables**: Added proper calculation of outstanding receivables as `Total Sales - Total Paid`.

*   **Enhanced Reports**: All financial reports now include COGS, corrected net profit calculations, profit margins, and clear field descriptions in API documentation.

*   **Stock Movement Tracking**: Complete audit trail for all inventory operations with timestamps, reasons, and references.

#### Important Notes

*   **Inventory Purchases**: Must be recorded through Purchase Orders in the inventory module, not as expenses.
*   **Operational Expenses**: Only true business operational costs should be recorded in the expense module.
*   **Backward Compatibility**: Existing data remains unaffected; validation applies only to new expense entries.

#### API Changes

*   **Enhanced Expense Management**: Expense creation/update endpoints now validate expense types
*   **New Stock Management Endpoints**: 
  *   `GET /api/stock/movements/{itemVariantId}` - Stock movement history
  *   `GET /api/stock/movements` - Movement reports by date range
  *   `GET /api/stock/low-stock-alerts` - Low stock alerts
  *   `POST /api/stock/adjust` - Manual stock adjustments
*   **New Sales Management Endpoints**:
  *   `POST /api/sales/{id}/return` - Process sales returns
  *   `POST /api/sales/{id}/cancel` - Cancel entire sales
  *   `GET /api/sales/profit-report` - Profit analysis reporting
*   **Enhanced Report Endpoints**: Return additional fields: `totalCOGS`, corrected `netProfit`, `grossMargin`, and `outstandingReceivable`
*   **Comprehensive API Documentation**: Enhanced Swagger documentation explains all new features and the distinction between different financial metrics

### COGS Calculation Methodology

The system uses **FIFO (First-In-First-Out)** method for Cost of Goods Sold calculation:

1. **Stock Entry**: Each stock addition records the actual cost per unit from purchase orders
2. **Sale Processing**: When items are sold, the system calculates COGS by consuming stock entries in chronological order (oldest first)
3. **Profit Calculation**: Gross Profit = Sales Revenue - COGS, with margin percentages automatically calculated
4. **Audit Trail**: Complete stock movement history maintains accurate cost tracking for compliance and analysis

This methodology ensures accurate profit reporting and provides valuable business insights for pricing and inventory management decisions.