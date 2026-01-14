-- MySQL dump 10.13  Distrib 9.3.0, for macos15.2 (arm64)
--
-- Host: localhost    Database: vyaparsathi_db
-- ------------------------------------------------------
-- Server version	9.3.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `refresh_token`
--

DROP TABLE IF EXISTS `refresh_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `expiry_date` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT '0',
  `shop_id` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_refresh_token_shop_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=236 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `refresh_token`
--

LOCK TABLES `refresh_token` WRITE;
/*!40000 ALTER TABLE `refresh_token` DISABLE KEYS */;
INSERT INTO `refresh_token` VALUES (37,'a8a978a6-cb43-41b1-8ca8-823c933964cc','Uma shankar','2025-09-10 17:26:16','2025-09-03 17:26:16',0,1,'2025-10-06 01:52:17'),(56,'e047c4af-5d84-428d-84a6-a92b8b6095eb','Birendra Shaw','2025-09-12 21:35:57','2025-09-05 21:35:57',0,1,'2025-10-06 01:52:17'),(235,'9713e8b1-5890-4e56-8152-6e3904797018','krshaw.birendra@gmail.com','2025-10-17 09:14:17','2025-10-10 14:44:17',0,1,'2025-10-10 14:44:17');
/*!40000 ALTER TABLE `refresh_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `delivery_status_history`
--

DROP TABLE IF EXISTS `delivery_status_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_id` bigint NOT NULL,
  `status` enum('PENDING','PACKED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED') NOT NULL,
  `changed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `changed_by` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_delivery` (`delivery_id`),
  KEY `idx_delivery_status_history_id` (`shop_id`),
  CONSTRAINT `fk_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `deliveries` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `delivery_status_history`
--

LOCK TABLES `delivery_status_history` WRITE;
/*!40000 ALTER TABLE `delivery_status_history` DISABLE KEYS */;
INSERT INTO `delivery_status_history` VALUES (1,1,'PACKED','2025-09-01 20:33:36','system','2025-10-04 22:37:31','2025-10-06 01:10:31',1),(2,1,'OUT_FOR_DELIVERY','2025-09-04 01:35:47','Admin','2025-10-04 22:37:31','2025-10-06 01:10:31',1),(3,1,'DELIVERED','2025-09-04 01:36:49','Admin','2025-10-04 22:37:31','2025-10-06 01:10:31',1),(4,2,'PACKED','2025-09-04 01:41:27','system','2025-10-04 22:37:31','2025-10-06 01:10:31',1),(5,2,'PENDING','2025-09-04 20:27:58','Admin','2025-10-04 22:37:31','2025-10-06 01:10:31',1),(6,2,'OUT_FOR_DELIVERY','2025-09-04 20:28:20','Admin','2025-10-04 22:37:31','2025-10-06 01:10:31',1),(7,3,'PACKED','2025-09-11 22:53:53','system','2025-10-04 22:37:31','2025-10-06 01:10:31',1),(8,3,'OUT_FOR_DELIVERY','2025-09-11 22:55:06','Admin','2025-10-04 22:37:31','2025-10-06 01:10:31',1);
/*!40000 ALTER TABLE `delivery_status_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `receiving_ticket_attachment`
--

DROP TABLE IF EXISTS `receiving_ticket_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receiving_ticket_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiving_ticket_id` bigint NOT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_type` varchar(255) DEFAULT NULL,
  `file_path` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_ticket_attachment_ticket_id` (`receiving_ticket_id`),
  KEY `idx_receiving_ticket_attachment_shop_id` (`shop_id`),
  CONSTRAINT `receiving_ticket_attachment_ibfk_1` FOREIGN KEY (`receiving_ticket_id`) REFERENCES `receiving_ticket` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `receiving_ticket_attachment`
--

LOCK TABLES `receiving_ticket_attachment` WRITE;
/*!40000 ALTER TABLE `receiving_ticket_attachment` DISABLE KEYS */;
/*!40000 ALTER TABLE `receiving_ticket_attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `change_log`
--

DROP TABLE IF EXISTS `change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(255) NOT NULL,
  `entity_id` bigint NOT NULL,
  `operation` varchar(255) NOT NULL,
  `payload_json` text,
  `device_id` varchar(255) NOT NULL,
  `seq_no` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_change_entity` (`entity_type`,`entity_id`),
  KEY `idx_change_log_shop_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `change_log`
--

LOCK TABLES `change_log` WRITE;
/*!40000 ALTER TABLE `change_log` DISABLE KEYS */;
INSERT INTO `change_log` VALUES (1,'SALE',1,'CREATE','{\"id\":1,\"invoiceNo\":\"1001-202509-001\",\"date\":\"2025-09-03T17:54:14.32903\",\"shop\":{\"id\":1,\"name\":\"Birendra Traders & Co.\",\"ownerName\":\"Birendra Shaw\",\"address\":\"Noida Sector 62, Near Metro Station, Opp Fortis Hospital\",\"state\":\"Uttar Pradesh\",\"gstin\":\"09AAACH7409R1ZZ\",\"code\":\"1001\",\"locale\":\"en\",\"createdAt\":\"2025-08-25T01:37:47\"},\"customer\":{\"id\":1,\"name\":\"Pappu Kumar\",\"phone\":\"9878678767\",\"email\":\"pappu.yadav@gmail.com\",\"addressLine1\":\"Noida Sector 52, Near Kids Zone School\",\"addressLine2\":\"\",\"city\":\"Noida\",\"state\":\"Uttar Pradesh\",\"postalCode\":\"201309\",\"country\":\"India\",\"gstNumber\":\"10AAACB1534F1ZL\",\"panNumber\":\"KNAB0710H\",\"notes\":\"\",\"creditBalance\":24950.00,\"createdAt\":\"2025-08-25T01:40:21\",\"updatedAt\":\"2025-09-01T20:33:37\"},\"totalAmount\":24950,\"cogs\":21250.00,\"roundOff\":0,\"syncedFlag\":false,\"saleItems\":[{\"id\":1,\"itemVariant\":{\"id\":1,\"sku\":\"ITEM-USPOLO.-M-GREY-50000000\",\"unit\":\"PIECE\",\"pricePerUnit\":499.00,\"hsn\":\"10000000\",\"gstRate\":5,\"photoPath\":\"/media/item-photos/fc1752b7-ad51-4c60-8cb2-710ff1b495b6.webp\",\"color\":\"Grey\",\"size\":\"M\",\"design\":\"Solid\",\"fit\":\"Regular Fit\",\"lowStockThreshold\":25.00},\"qty\":50,\"unitPrice\":499,\"costPerUnit\":425.0000,\"taxableValue\":0,\"gstType\":\"GST_0\",\"cgstAmt\":0,\"sgstAmt\":0,\"igstAmt\":0}],\"paymentStatus\":\"PENDING\"}','LOCAL_DEVICE',1,'2025-09-03 17:54:15','2025-10-05 16:43:57',1),(2,'SALE',2,'CREATE','{\"id\":2,\"invoiceNo\":\"1001-202509-002\",\"date\":\"2025-09-03T17:58:57.10682\",\"shop\":{\"id\":1,\"name\":\"Birendra Traders & Co.\",\"ownerName\":\"Birendra Shaw\",\"address\":\"Noida Sector 62, Near Metro Station, Opp Fortis Hospital\",\"state\":\"Uttar Pradesh\",\"gstin\":\"09AAACH7409R1ZZ\",\"code\":\"1001\",\"locale\":\"en\",\"createdAt\":\"2025-08-25T01:37:47\"},\"customer\":{\"id\":2,\"name\":\"Rahul Sharma\",\"phone\":\"9877676565\",\"email\":\"rahul.sharma@gmail.com\",\"addressLine1\":\"Janakpur Road Pupri\",\"addressLine2\":\"\",\"city\":\"Sitamarhi\",\"state\":\"Bihar\",\"postalCode\":\"843320\",\"country\":\"India\",\"gstNumber\":\"\",\"panNumber\":\"KHTP1234S\",\"notes\":\"\",\"creditBalance\":2000.00,\"createdAt\":\"2025-08-25T01:41:48\",\"updatedAt\":\"2025-09-03T17:58:57.209496\"},\"totalAmount\":4990,\"cogs\":4250.00,\"roundOff\":0,\"syncedFlag\":false,\"saleItems\":[{\"id\":2,\"itemVariant\":{\"id\":1,\"sku\":\"ITEM-USPOLO.-M-GREY-50000000\",\"unit\":\"PIECE\",\"pricePerUnit\":499.00,\"hsn\":\"10000000\",\"gstRate\":5,\"photoPath\":\"/media/item-photos/fc1752b7-ad51-4c60-8cb2-710ff1b495b6.webp\",\"color\":\"Grey\",\"size\":\"M\",\"design\":\"Solid\",\"fit\":\"Regular Fit\",\"lowStockThreshold\":25.00},\"qty\":10,\"unitPrice\":499,\"costPerUnit\":425.0000,\"taxableValue\":0,\"gstType\":\"GST_0\",\"cgstAmt\":0,\"sgstAmt\":0,\"igstAmt\":0}],\"paymentStatus\":\"PARTIALLY_PAID\"}','LOCAL_DEVICE',2,'2025-09-03 17:58:57','2025-10-05 16:43:57',1),(3,'SALE',3,'CREATE','{\"id\":3,\"invoiceNo\":\"1001-202509-003\",\"date\":\"2025-09-04T01:41:27.165774\",\"shop\":{\"id\":1,\"name\":\"Birendra Traders & Co.\",\"ownerName\":\"Birendra Shaw\",\"address\":\"Noida Sector 62, Near Metro Station, Opp Fortis Hospital\",\"state\":\"Uttar Pradesh\",\"gstin\":\"09AAACH7409R1ZZ\",\"code\":\"1001\",\"locale\":\"en\",\"createdAt\":\"2025-08-25T01:37:47\"},\"customer\":{\"id\":2,\"name\":\"Rahul Sharma\",\"phone\":\"9877676565\",\"email\":\"rahul.sharma@gmail.com\",\"addressLine1\":\"Janakpur Road Pupri\",\"addressLine2\":\"\",\"city\":\"Sitamarhi\",\"state\":\"Bihar\",\"postalCode\":\"843320\",\"country\":\"India\",\"gstNumber\":\"\",\"panNumber\":\"KHTP1234S\",\"notes\":\"\",\"creditBalance\":19465.00,\"createdAt\":\"2025-08-25T01:41:48\",\"updatedAt\":\"2025-09-03T17:58:57\"},\"totalAmount\":17465,\"cogs\":14875.00,\"roundOff\":0,\"syncedFlag\":false,\"saleItems\":[{\"id\":3,\"itemVariant\":{\"id\":1,\"sku\":\"ITEM-USPOLO.-M-GREY-50000000\",\"unit\":\"PIECE\",\"pricePerUnit\":499.00,\"hsn\":\"10000000\",\"gstRate\":5,\"photoPath\":\"/media/item-photos/fc1752b7-ad51-4c60-8cb2-710ff1b495b6.webp\",\"color\":\"Grey\",\"size\":\"M\",\"design\":\"Solid\",\"fit\":\"Regular Fit\",\"lowStockThreshold\":25.00},\"qty\":35,\"unitPrice\":499,\"costPerUnit\":425.0000,\"taxableValue\":0,\"gstType\":\"GST_0\",\"cgstAmt\":0,\"sgstAmt\":0,\"igstAmt\":0}],\"paymentStatus\":\"PENDING\"}','LOCAL_DEVICE',3,'2025-09-04 01:41:27','2025-10-05 16:43:57',1),(4,'SALE',4,'CREATE','{\"id\":4,\"invoiceNo\":\"1001-202509-004\",\"date\":\"2025-09-05T00:58:20.901294\",\"shop\":{\"id\":1,\"name\":\"Birendra Traders & Co.\",\"ownerName\":\"Birendra Shaw\",\"address\":\"Noida Sector 62, Near Metro Station, Opp Fortis Hospital\",\"state\":\"Uttar Pradesh\",\"gstin\":\"09AAACH7409R1ZZ\",\"code\":\"1001\",\"locale\":\"en\",\"createdAt\":\"2025-08-25T01:37:47\"},\"customer\":{\"id\":1,\"name\":\"Pappu Kumar\",\"phone\":\"9878678767\",\"email\":\"pappu.yadav@gmail.com\",\"addressLine1\":\"Noida Sector 52, Near Kids Zone School\",\"addressLine2\":\"\",\"city\":\"Noida\",\"state\":\"Uttar Pradesh\",\"postalCode\":\"201309\",\"country\":\"India\",\"gstNumber\":\"10AAACB1534F1ZL\",\"panNumber\":\"KNAB0710H\",\"notes\":\"\",\"creditBalance\":25500.00,\"createdAt\":\"2025-08-25T01:40:21\",\"updatedAt\":\"2025-09-03T17:54:17\"},\"totalAmount\":550,\"roundOff\":0,\"syncedFlag\":false,\"saleItems\":[{\"id\":4,\"itemVariant\":{\"id\":1,\"sku\":\"ITEM-USPOLO.-M-GREY-50000000\",\"unit\":\"PIECE\",\"pricePerUnit\":550.00,\"hsn\":\"10000004\",\"gstRate\":5,\"photoPath\":null,\"color\":\"Grey\",\"size\":\"M\",\"design\":\"Solid\",\"fit\":\"Regular Fit\",\"lowStockThreshold\":25.00},\"qty\":1,\"unitPrice\":550,\"taxableValue\":0,\"gstType\":\"GST_0\",\"cgstAmt\":0,\"sgstAmt\":0,\"igstAmt\":0}],\"paymentStatus\":\"PENDING\"}','LOCAL_DEVICE',4,'2025-09-05 00:58:21','2025-10-05 16:43:57',1),(5,'SALE',5,'CREATE','{\"id\":5,\"invoiceNo\":\"1001-202509-005\",\"date\":\"2025-09-05T13:35:14.43357\",\"shop\":{\"id\":1,\"name\":\"Birendra Traders & Co.\",\"ownerName\":\"Birendra Shaw\",\"address\":\"Noida Sector 62, Near Metro Station, Opp Fortis Hospital\",\"state\":\"Uttar Pradesh\",\"gstin\":\"09AAACH7409R1ZZ\",\"code\":\"1001\",\"locale\":\"en\",\"createdAt\":\"2025-08-25T01:37:47\"},\"customer\":{\"id\":2,\"name\":\"Rahul Sharma\",\"phone\":\"9877676565\",\"email\":\"rahul.sharma@gmail.com\",\"addressLine1\":\"Janakpur Road Pupri\",\"addressLine2\":\"\",\"city\":\"Sitamarhi\",\"state\":\"Bihar\",\"postalCode\":\"843320\",\"country\":\"India\",\"gstNumber\":\"\",\"panNumber\":\"KHTP1234S\",\"notes\":\"\",\"creditBalance\":33430.00,\"createdAt\":\"2025-08-25T01:41:48\",\"updatedAt\":\"2025-09-04T01:41:27\"},\"totalAmount\":13965,\"roundOff\":0,\"syncedFlag\":false,\"saleItems\":[{\"id\":5,\"itemVariant\":{\"id\":3,\"sku\":\"ITEM-KANCHIVARAM-FREE SIZE-PINK-50000000\",\"unit\":\"PIECE\",\"pricePerUnit\":399.00,\"hsn\":\"10000000\",\"gstRate\":null,\"photoPath\":null,\"color\":\"Pink\",\"size\":\"Free Size\",\"design\":\"Embroidered\",\"fit\":\"Regular Fit\",\"lowStockThreshold\":10.00},\"qty\":35,\"unitPrice\":399,\"taxableValue\":0,\"gstType\":\"GST_0\",\"cgstAmt\":0,\"sgstAmt\":0,\"igstAmt\":0}],\"paymentStatus\":\"PENDING\"}','LOCAL_DEVICE',5,'2025-09-05 13:35:14','2025-10-05 16:43:57',1),(6,'SALE',6,'CREATE','{\"id\":6,\"invoiceNo\":\"1001-202509-006\",\"date\":\"2025-09-11T22:53:52.782427\",\"shop\":{\"id\":1,\"name\":\"Birendra Traders & Co.\",\"ownerName\":\"Birendra Shaw\",\"address\":\"Noida Sector 62, Near Metro Station, Opp Fortis Hospital\",\"state\":\"Uttar Pradesh\",\"gstin\":\"09AAACH7409R1ZZ\",\"code\":\"1001\",\"locale\":\"en\",\"createdAt\":\"2025-08-25T01:37:47\"},\"customer\":{\"id\":1,\"name\":\"Pappu Kumar\",\"phone\":\"9878678767\",\"email\":\"pappu.yadav@gmail.com\",\"addressLine1\":\"Noida Sector 52, Near Kids Zone School\",\"addressLine2\":\"\",\"city\":\"Noida\",\"state\":\"Uttar Pradesh\",\"postalCode\":\"201309\",\"country\":\"India\",\"gstNumber\":\"10AAACB1534F1ZL\",\"panNumber\":\"KNAB0710H\",\"notes\":\"\",\"creditBalance\":24035.00,\"createdAt\":\"2025-08-25T01:40:21\",\"updatedAt\":\"2025-09-09T22:32:40\"},\"totalAmount\":4485,\"roundOff\":0,\"syncedFlag\":false,\"saleItems\":[{\"id\":6,\"itemVariant\":{\"id\":11,\"sku\":\"ITEM-LOUISPHILIPPE-9 YEARS-BLACK-50000000\",\"unit\":\"PIECE\",\"pricePerUnit\":299.00,\"hsn\":\"10000006\",\"gstRate\":5,\"photoPath\":null,\"color\":\"Black\",\"size\":\"9 Years\",\"design\":\"Solid\",\"fit\":\"Regular Fit\",\"lowStockThreshold\":10.00},\"qty\":15,\"unitPrice\":299,\"taxableValue\":0,\"gstType\":\"GST_0\",\"cgstAmt\":0,\"sgstAmt\":0,\"igstAmt\":0}],\"paymentStatus\":\"PENDING\"}','LOCAL_DEVICE',6,'2025-09-11 22:53:53','2025-10-05 16:43:57',1),(7,'SALE',7,'CREATE','{\"id\":7,\"invoiceNo\":\"1001-202509-007\",\"date\":\"2025-09-21T15:33:29.055324\",\"shop\":{\"id\":1,\"name\":\"Birendra Traders & Co.\",\"ownerName\":\"Birendra Shaw\",\"address\":\"Noida Sector 62, Near Metro Station, Opp Fortis Hospital\",\"state\":\"Uttar Pradesh\",\"gstin\":\"09AAACH7409R1ZZ\",\"code\":\"1001\",\"locale\":\"en\",\"createdAt\":\"2025-08-25T01:37:47\"},\"customer\":{\"id\":1,\"name\":\"Pappu Kumar\",\"phone\":\"9878678767\",\"email\":\"pappu.yadav@gmail.com\",\"addressLine1\":\"Noida Sector 52, Near Kids Zone School\",\"addressLine2\":\"\",\"city\":\"Noida\",\"state\":\"Uttar Pradesh\",\"postalCode\":\"201309\",\"country\":\"India\",\"gstNumber\":\"10AAACB1534F1ZL\",\"panNumber\":\"KNAB0710H\",\"notes\":\"\",\"creditBalance\":26025.00,\"createdAt\":\"2025-08-25T01:40:21\",\"updatedAt\":\"2025-09-11T22:53:53\"},\"totalAmount\":1990,\"roundOff\":0,\"syncedFlag\":false,\"saleItems\":[{\"id\":7,\"itemVariant\":{\"id\":12,\"sku\":\"ITEM-TEST-S-BLACK-50000000\",\"unit\":\"PIECE\",\"pricePerUnit\":199.00,\"hsn\":\"10000007\",\"gstRate\":null,\"photoPath\":null,\"color\":\"Black\",\"size\":\"S\",\"design\":\"Solid\",\"fit\":\"Regular Fit\",\"lowStockThreshold\":10.00},\"qty\":10,\"unitPrice\":199,\"taxableValue\":0,\"gstType\":\"GST_0\",\"cgstAmt\":0,\"sgstAmt\":0,\"igstAmt\":0}],\"paymentStatus\":\"PENDING\"}','LOCAL_DEVICE',7,'2025-09-21 15:33:29','2025-10-05 16:43:57',1);
/*!40000 ALTER TABLE `change_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customer_ledger`
--

DROP TABLE IF EXISTS `customer_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `type` varchar(255) NOT NULL,
  `description` text,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_customer_ledger_customer_id` (`customer_id`),
  KEY `idx_customer_ledger_shop_id` (`shop_id`),
  CONSTRAINT `customer_ledger_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customer_ledger`
--

LOCK TABLES `customer_ledger` WRITE;
/*!40000 ALTER TABLE `customer_ledger` DISABLE KEYS */;
INSERT INTO `customer_ledger` VALUES (1,1,24950.00,'CREDIT','Sale #1001-202509-001','2025-09-03 17:54:15','2025-10-05 16:45:36',1),(2,2,4990.00,'CREDIT','Sale #1001-202509-002','2025-09-03 17:58:57','2025-10-05 16:45:36',1),(3,2,2000.00,'DEBIT','Payment for Sale #1001-202509-002 (CASH)','2025-09-03 17:58:57','2025-10-05 16:45:36',1),(4,2,990.00,'DEBIT','Payment for Sale #1001-202509-002 (UPI)','2025-09-03 17:58:57','2025-10-05 16:45:36',1),(5,2,17465.00,'CREDIT','Sale #1001-202509-003','2025-09-04 01:41:27','2025-10-05 16:45:36',1),(6,1,550.00,'CREDIT','Sale #1001-202509-004','2025-09-05 00:58:21','2025-10-05 16:45:36',1),(7,2,13965.00,'CREDIT','Sale #1001-202509-005','2025-09-05 13:35:14','2025-10-05 16:45:36',1),(8,2,500.00,'DEBIT','Due Payment for Sale #2 (CASH)','2025-09-09 22:31:18','2025-10-05 16:45:36',1),(9,1,950.00,'DEBIT','Due Payment for Sale #1 (CASH)','2025-09-09 22:32:22','2025-10-05 16:45:36',1),(10,1,5000.00,'DEBIT','Due Payment for Sale #1 (UPI)','2025-09-09 22:32:40','2025-10-05 16:45:36',1),(11,2,197.00,'DEBIT','Due Payment for Sale #2 (CASH)','2025-09-09 22:43:26','2025-10-05 16:45:36',1),(12,2,5000.00,'DEBIT','Due Payment for Sale #3 (CASH)','2025-09-09 23:31:15','2025-10-05 16:45:36',1),(13,2,500.00,'DEBIT','Due Payment for Sale #5 (CASH)','2025-09-09 23:31:49','2025-10-05 16:45:36',1),(14,2,4500.00,'DEBIT','Due Payment for Sale #5 (CASH)','2025-09-09 23:32:08','2025-10-05 16:45:36',1),(15,1,4485.00,'CREDIT','Sale #1001-202509-006','2025-09-11 22:53:53','2025-10-05 16:45:36',1),(16,1,1990.00,'CREDIT','Sale #1001-202509-007','2025-09-21 15:33:29','2025-10-05 16:45:36',1);
/*!40000 ALTER TABLE `customer_ledger` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sale_item`
--

DROP TABLE IF EXISTS `sale_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sale_id` bigint NOT NULL,
  `item_variant_id` bigint NOT NULL,
  `qty` decimal(10,2) NOT NULL,
  `unit_price` decimal(10,2) NOT NULL,
  `taxable_value` decimal(10,2) NOT NULL,
  `gst_type` varchar(255) NOT NULL,
  `cgst_amt` decimal(10,2) NOT NULL,
  `sgst_amt` decimal(10,2) NOT NULL,
  `igst_amt` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sale_item_sale_id` (`sale_id`),
  KEY `idx_sale_item_variant_id` (`item_variant_id`),
  KEY `idx_sale_item_shop_id` (`shop_id`),
  CONSTRAINT `sale_item_ibfk_1` FOREIGN KEY (`sale_id`) REFERENCES `sale` (`id`),
  CONSTRAINT `sale_item_ibfk_2` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sale_item`
--

LOCK TABLES `sale_item` WRITE;
/*!40000 ALTER TABLE `sale_item` DISABLE KEYS */;
INSERT INTO `sale_item` VALUES (1,1,1,50.00,499.00,0.00,'GST_0',0.00,0.00,0.00,'2025-10-04 22:57:30','2025-10-06 01:29:09',1),(2,2,1,10.00,499.00,0.00,'GST_0',0.00,0.00,0.00,'2025-10-04 22:57:30','2025-10-06 01:29:09',1),(3,3,1,35.00,499.00,0.00,'GST_0',0.00,0.00,0.00,'2025-10-04 22:57:30','2025-10-06 01:29:09',1),(4,4,1,1.00,550.00,0.00,'GST_0',0.00,0.00,0.00,'2025-10-04 22:57:30','2025-10-06 01:29:09',1),(5,5,3,35.00,399.00,0.00,'GST_0',0.00,0.00,0.00,'2025-10-04 22:57:30','2025-10-06 01:29:09',1),(6,6,11,15.00,299.00,0.00,'GST_0',0.00,0.00,0.00,'2025-10-04 22:57:30','2025-10-06 01:29:09',1),(7,7,12,10.00,199.00,0.00,'GST_0',0.00,0.00,0.00,'2025-10-04 22:57:30','2025-10-06 01:29:09',1);
/*!40000 ALTER TABLE `sale_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `supplier`
--

DROP TABLE IF EXISTS `supplier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `contact_person` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `gstin` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_supplier_phone` (`phone`),
  KEY `idx_supplier_shop_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `supplier`
--

LOCK TABLES `supplier` WRITE;
/*!40000 ALTER TABLE `supplier` DISABLE KEYS */;
INSERT INTO `supplier` VALUES (1,'Salim Khan','Md. Ishmail ','7843456789','salim.khan@gmail.com','Bara Bazar, Kolkata, West Bengal','09AAACH7409R1ZZ','2025-10-04 22:58:31','2025-10-06 01:30:19',1),(2,'Rakesh Singh','Rakesh Singh','1234657898','rakesh.singh@gmail.com','Patna, Near Gandhi Maidan','09AAACH7409R1YY','2025-10-04 22:58:31','2025-10-06 01:30:19',1),(3,'Rahul Verma','Rahul Verma','9508156284','rahul.verma@gmail.com','Rasoolpur, Nawada, Noida Sector 62','09AAACH7409R1ZX','2025-10-10 14:45:01','2025-10-10 14:46:10',2);
/*!40000 ALTER TABLE `supplier` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `username` varchar(255) NOT NULL,
  `pin_hash` varchar(255) NOT NULL,
  `role` varchar(255) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `shop_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `UK_users_email` (`email`),
  KEY `idx_users_shop_id` (`shop_id`),
  CONSTRAINT `FK_users_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'Birendra','Shaw','krshaw.birendra@gmail.com','krshaw.birendra@gmail.com','$2a$10$oiHdWyafzgwxHHiyY5Fpk..gM1pTCLjqXNBFOlxb6sWKk2.AshmPe','OWNER',1,1,'2025-09-06 00:26:10','2025-09-06 04:11:34'),(2,'Uma','Shankar','uma.shankar@gmail.com','uma.shankar@gmail.com','$2a$10$GVVRWEhXlzspxpGjI4d2COPR6Rh4rELYZvo6wFq7aNv09AW9781xG','STAFF',1,1,'2025-09-06 00:26:10','2025-09-06 04:51:51');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `item_variant`
--

DROP TABLE IF EXISTS `item_variant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_variant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sku` varchar(255) NOT NULL,
  `unit` varchar(255) NOT NULL,
  `price_per_unit` decimal(10,2) NOT NULL,
  `hsn` varchar(255) DEFAULT NULL,
  `gst_rate` int DEFAULT NULL,
  `photo_path` varchar(255) DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `color` varchar(255) DEFAULT NULL,
  `size` varchar(255) DEFAULT NULL,
  `design` varchar(255) DEFAULT NULL,
  `low_stock_threshold` decimal(10,2) DEFAULT NULL,
  `fit` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `sku` (`sku`),
  KEY `idx_item_variant_item_id` (`item_id`),
  KEY `idx_item_variant_shop_id` (`shop_id`),
  CONSTRAINT `item_variant_ibfk_1` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `item_variant`
--

LOCK TABLES `item_variant` WRITE;
/*!40000 ALTER TABLE `item_variant` DISABLE KEYS */;
INSERT INTO `item_variant` VALUES (1,'ITEM-USPOLO.-M-GREY-50000000','PIECE',550.00,'10000004',5,NULL,1,'Grey','M','Solid',25.00,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1),(2,'ITEM-USPOLO.-S-GREY-50000001','PIECE',450.00,'10000005',5,NULL,1,'Grey','S','Solid',25.00,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1),(3,'ITEM-KANCHIVARAM-FREE SIZE-PINK-50000000','PIECE',399.00,'10000000',NULL,NULL,2,'Pink','Free Size','Embroidered',10.00,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1),(4,'ITEM-KANCHIVARAM-FREE SIZE-GREY-50000001','PIECE',499.00,'10000001',NULL,NULL,2,'Grey','Free Size','Embroidered',8.00,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1),(5,'ITEM-TEST-XS-BLACK-50000034','PIECE',444.00,'10000002',NULL,NULL,19,'Black','XS','Solid',NULL,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1),(6,'ITEM-TEST-S-BLACK-50000035','PIECE',444.00,'10000003',NULL,NULL,19,'Black','S','Striped',NULL,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1),(11,'ITEM-LOUISPHILIPPE-9 YEARS-BLACK-50000000','PIECE',299.00,'10000006',5,NULL,22,'Black','9 Years','Solid',10.00,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1),(12,'ITEM-TEST-S-BLACK-50000000','PIECE',199.00,'10000007',NULL,NULL,23,'Black','S','Solid',10.00,'Regular Fit','2025-10-04 22:39:17','2025-10-06 01:13:18',1);
/*!40000 ALTER TABLE `item_variant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) DEFAULT NULL,
  `action` varchar(255) DEFAULT NULL,
  `entity` varchar(255) DEFAULT NULL,
  `entity_id` varchar(255) DEFAULT NULL,
  `details` varchar(2000) DEFAULT NULL,
  `timestamp` datetime DEFAULT NULL,
  `shop_id` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_entity` (`entity`,`entity_id`),
  KEY `idx_audit_log_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `deliveries`
--

DROP TABLE IF EXISTS `deliveries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deliveries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sale_id` bigint NOT NULL,
  `delivery_address` text,
  `delivery_charge` decimal(12,2) DEFAULT NULL,
  `delivery_paid_by` enum('CUSTOMER','SHOP') NOT NULL,
  `delivery_status` enum('PENDING','PACKED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `delivery_person_id` bigint DEFAULT NULL,
  `delivery_notes` text,
  `delivered_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `invoice_number` varchar(255) NOT NULL,
  `customer_name` varchar(255) NOT NULL,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_delivery_person` (`delivery_person_id`),
  KEY `idx_deliveries_shop_id` (`shop_id`),
  CONSTRAINT `fk_delivery_person` FOREIGN KEY (`delivery_person_id`) REFERENCES `delivery_persons` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `deliveries`
--

LOCK TABLES `deliveries` WRITE;
/*!40000 ALTER TABLE `deliveries` DISABLE KEYS */;
INSERT INTO `deliveries` VALUES (1,28,'Nanpur, Sitamarhi',500.00,'CUSTOMER','DELIVERED',2,'Near Hanuman Temple','2025-09-04 01:36:49','2025-09-01 20:33:36','2025-10-06 01:07:44','NA','NA',1),(2,3,'Sirsi Bazar',600.00,'CUSTOMER','OUT_FOR_DELIVERY',NULL,'Customer has to pay the delivery charge ',NULL,'2025-09-04 01:41:27','2025-10-06 01:07:44','1001-202509-003','Rahul Sharma',1),(3,6,'Gurugram',500.00,'CUSTOMER','OUT_FOR_DELIVERY',1,'delivery charge needs to be paid by customer',NULL,'2025-09-11 22:53:53','2025-10-06 01:07:44','1001-202509-006','Pappu Kumar',1);
/*!40000 ALTER TABLE `deliveries` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_order_item`
--

DROP TABLE IF EXISTS `purchase_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_order_id` bigint NOT NULL,
  `item_variant_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `unit_cost` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_purchase_order_item_order_id` (`purchase_order_id`),
  KEY `idx_purchase_order_item_variant_id` (`item_variant_id`),
  KEY `idx_purchase_order_item_shop_id` (`shop_id`),
  CONSTRAINT `purchase_order_item_ibfk_1` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
  CONSTRAINT `purchase_order_item_ibfk_2` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_order_item`
--

LOCK TABLES `purchase_order_item` WRITE;
/*!40000 ALTER TABLE `purchase_order_item` DISABLE KEYS */;
INSERT INTO `purchase_order_item` VALUES (1,1,4,100,299.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1),(2,2,2,100,499.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1),(5,3,1,100,450.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1),(6,3,11,100,250.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1),(7,4,1,100,420.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1),(8,4,4,50,500.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1),(9,5,2,10,100.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1),(10,6,12,20,150.00,'2025-10-04 22:41:23','2025-10-06 01:17:37',1);
/*!40000 ALTER TABLE `purchase_order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `expense`
--

DROP TABLE IF EXISTS `expense`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expense` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_id` bigint NOT NULL,
  `type` varchar(255) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `date` datetime NOT NULL,
  `notes` text,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_expense_shop_id` (`shop_id`),
  KEY `idx_expense_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `expense`
--

LOCK TABLES `expense` WRITE;
/*!40000 ALTER TABLE `expense` DISABLE KEYS */;
/*!40000 ALTER TABLE `expense` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_movement`
--

DROP TABLE IF EXISTS `stock_movement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_movement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_variant_id` bigint NOT NULL,
  `movement_type` enum('ADD','DEDUCT','ADJUST') NOT NULL,
  `quantity` decimal(10,2) NOT NULL,
  `cost_per_unit` decimal(10,2) DEFAULT NULL,
  `batch` varchar(255) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `reference` varchar(255) DEFAULT NULL,
  `timestamp` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_movement_item_variant_id` (`item_variant_id`),
  KEY `idx_stock_movement_shop_id` (`shop_id`),
  CONSTRAINT `stock_movement_ibfk_1` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_movement`
--

LOCK TABLES `stock_movement` WRITE;
/*!40000 ALTER TABLE `stock_movement` DISABLE KEYS */;
INSERT INTO `stock_movement` VALUES (1,1,'ADD',100.00,425.00,'1001-USP','Manual Stock Addition','Manual Entry','2025-09-03 16:55:28','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(2,1,'DEDUCT',-50.00,0.00,NULL,'Sale Transaction','Sale - Processing','2025-09-03 17:54:13','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(3,1,'DEDUCT',-10.00,0.00,NULL,'Sale Transaction','Sale - Processing','2025-09-03 17:58:57','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(4,1,'DEDUCT',-35.00,0.00,NULL,'Sale Transaction','Sale - Processing','2025-09-04 01:41:27','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(5,1,'ADD',250.00,500.00,'04-09-2025','Manual Stock Addition','Manual Entry','2025-09-04 11:52:01','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(8,1,'DEDUCT',-1.00,NULL,NULL,'Sale Transaction','Sale #1001-202509-004','2025-09-05 00:58:20','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(9,2,'ADD',20.00,410.00,'05082025','Manual Stock Addition','Manual Entry','2025-09-05 13:05:31','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(10,3,'ADD',0.00,350.00,'082025','Manual Stock Addition','Manual Entry','2025-09-05 13:11:01','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(11,3,'ADD',50.00,345.00,NULL,'Manual Stock Addition','Manual Entry','2025-09-05 13:34:20','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(12,3,'DEDUCT',-35.00,NULL,NULL,'Sale Transaction','Sale #1001-202509-005','2025-09-05 13:35:14','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(13,4,'ADD',100.00,299.00,NULL,'Purchase Order Receiving','1','2025-09-05 23:12:34','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(14,2,'ADD',100.00,499.00,NULL,'Purchase Order Receiving','2','2025-09-06 04:56:32','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(15,11,'ADD',20.00,250.00,'11092025-1','Manual Stock Addition','Manual Entry','2025-09-11 22:51:08','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(16,11,'DEDUCT',-15.00,NULL,NULL,'Sale Transaction','Sale #1001-202509-006','2025-09-11 22:53:52','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(17,1,'ADD',100.00,450.00,NULL,'Purchase Order Receiving','3','2025-09-11 23:19:51','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(18,11,'ADD',100.00,250.00,NULL,'Purchase Order Receiving','3','2025-09-11 23:19:51','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(19,1,'ADD',20.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 11:04:10','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(20,1,'ADD',20.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 11:05:15','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(21,1,'ADD',20.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 12:25:18','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(22,1,'ADD',10.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 12:30:43','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(35,1,'ADD',10.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:35:20','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(36,4,'ADD',20.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:35:20','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(37,1,'ADD',10.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:41:18','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(38,4,'ADD',20.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:41:18','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(39,1,'ADD',10.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:41:24','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(40,4,'ADD',20.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:41:24','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(41,1,'ADD',10.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:42:34','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(42,4,'ADD',20.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:42:34','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(43,1,'ADD',10.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:52:41','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(44,4,'ADD',40.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 22:52:41','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(45,4,'ADD',100.00,299.00,NULL,'Purchase Order Receiving','1','2025-09-12 23:10:55','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(46,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 23:22:07','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(47,4,'ADD',40.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 23:22:07','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(48,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 23:26:58','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(49,4,'ADD',40.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 23:26:58','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(50,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-12 23:27:26','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(51,4,'ADD',10.00,500.00,NULL,'Purchase Order Receiving','4','2025-09-12 23:27:26','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(52,1,'ADD',5.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-13 23:38:15','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(53,1,'ADD',10.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-13 23:39:59','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(54,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-13 23:40:28','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(55,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-13 23:41:35','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(56,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-13 23:48:44','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(57,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-14 00:15:46','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(58,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-14 00:28:53','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(59,1,'ADD',50.00,420.00,NULL,'Purchase Order Receiving','4','2025-09-14 00:30:06','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(60,1,'DEDUCT',20.00,420.00,NULL,'Purchase Order Receiving Adjustment','4','2025-09-14 01:26:44','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(61,12,'ADD',15.00,NULL,NULL,'Manual Stock Addition','Manual Entry','2025-09-21 15:32:37','2025-10-04 22:58:03','2025-10-06 01:29:45',1),(62,12,'DEDUCT',-10.00,NULL,NULL,'Sale Transaction','Sale #1001-202509-007','2025-09-21 15:33:29','2025-10-04 22:58:03','2025-10-06 01:29:45',1);
/*!40000 ALTER TABLE `stock_movement` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customer`
--

DROP TABLE IF EXISTS `customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `phone` varchar(15) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(255) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  `postal_code` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `gst_number` varchar(255) DEFAULT NULL,
  `pan_number` varchar(255) DEFAULT NULL,
  `notes` text,
  `credit_balance` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone` (`phone`),
  KEY `idx_customer_phone` (`phone`),
  KEY `idx_customer_shop_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customer`
--

LOCK TABLES `customer` WRITE;
/*!40000 ALTER TABLE `customer` DISABLE KEYS */;
INSERT INTO `customer` VALUES (1,'Pappu Kumar','9878678767','pappu.yadav@gmail.com','Noida Sector 52, Near Kids Zone School','','Noida','Uttar Pradesh','201309','India','10AAACB1534F1ZL','KNAB0710H','',26025.00,'2025-08-25 01:40:21','2025-09-21 15:33:29',2),(2,'Rahul Sharma','9877676565','rahul.sharma@gmail.com','Janakpur Road Pupri','','Sitamarhi','Bihar','843320','India','','KHTP1234S','',22733.00,'2025-08-25 01:41:48','2025-09-09 23:32:08',1),(3,'Raju Kumar','9878987898','raju.kumar@gmail.com','Sirsi, Chauk','Near Durga Mata Mandir','Sitamarhi','Bihar','843333','India','18AAACB1534F3Z3','DYOP1009T','',0.00,'2025-08-25 21:24:10','2025-09-01 19:39:34',1),(4,'Rajiv Kumar','1234432345',NULL,'Hazipur','Near Hanuman Temple','Hazipur','Bihar','844503','India','','','',0.00,'2025-08-31 20:07:34','2025-08-31 20:28:25',1),(5,'Chakor Sahu','4567645679',NULL,'Nagpur','','Mumbai','Maharastra','','','','','',0.00,'2025-09-02 22:54:05','2025-09-02 23:05:24',1);
/*!40000 ALTER TABLE `customer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reset_token`
--

DROP TABLE IF EXISTS `reset_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reset_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `expiry` datetime NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `shop_id` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_reset_token_shop_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reset_token`
--

LOCK TABLES `reset_token` WRITE;
/*!40000 ALTER TABLE `reset_token` DISABLE KEYS */;
INSERT INTO `reset_token` VALUES (1,'1f974230-98c2-45d0-8b11-b0356f98532e','Birendra Shaw','2025-09-06 02:29:21',0,1,'2025-10-06 01:52:48','2025-10-06 01:53:34');
/*!40000 ALTER TABLE `reset_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_category_parent` (`parent_id`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'MEN / FORMAL / Shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(2,'MEN / FORMAL / Trousers',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(3,'MEN / FORMAL / Suits',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(4,'MEN / FORMAL / Blazers',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(5,'MEN / FORMAL / Nehru Jackets',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(6,'MEN / CASUAL / T-Shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(7,'MEN / CASUAL / Shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(8,'MEN / CASUAL / Jeans',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(9,'MEN / CASUAL / Chinos',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(10,'MEN / CASUAL / Shorts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(11,'MEN / CASUAL / Joggers',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(12,'MEN / CASUAL / Polo T-shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(13,'MEN / ETHNIC / Kurta Pajama',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(14,'MEN / ETHNIC / Sherwani',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(15,'MEN / ETHNIC / Dhoti Kurta',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(16,'MEN / ETHNIC / Bandhgala',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(17,'MEN / ACTIVEWEAR / T-Shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(18,'MEN / ACTIVEWEAR / Shorts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(19,'MEN / ACTIVEWEAR / Tracksuits',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(20,'MEN / OUTERWEAR / Jackets',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(21,'MEN / OUTERWEAR / Sweaters',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(22,'MEN / OUTERWEAR / Hoodies',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(23,'WOMEN / ETHNIC / Sarees',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(24,'WOMEN / ETHNIC / Lehengas',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(25,'WOMEN / ETHNIC / Salwar Kameez',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(26,'WOMEN / ETHNIC / Kurtis',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(27,'WOMEN / ETHNIC / Anarkali Suits',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(28,'WOMEN / WESTERN / Dresses',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(29,'WOMEN / WESTERN / Tops',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(30,'WOMEN / WESTERN / Shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(31,'WOMEN / WESTERN / Jeans',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(32,'WOMEN / WESTERN / Skirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(33,'WOMEN / FUSION / Indo-Western Dresses',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(34,'WOMEN / LINGERIE & NIGHTWEAR / Bras',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(35,'WOMEN / LINGERIE & NIGHTWEAR / Panties',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(36,'WOMEN / LINGERIE & NIGHTWEAR / Sleepwear',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(37,'KIDS / BOYS / T-shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(38,'KIDS / BOYS / Shirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(39,'KIDS / BOYS / Jeans',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(40,'KIDS / BOYS / Shorts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(41,'KIDS / GIRLS / Dresses',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(42,'KIDS / GIRLS / Tops',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(43,'KIDS / GIRLS / Skirts',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(44,'KIDS / GIRLS / Frocks',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(45,'KIDS / INFANT / Rompers',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34'),(46,'KIDS / INFANT / Onesies',NULL,'2025-10-04 22:32:34','2025-10-04 22:32:34');
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `delivery_persons`
--

DROP TABLE IF EXISTS `delivery_persons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_persons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `phone` varchar(50) DEFAULT NULL,
  `notes` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_delivery_persons_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `delivery_persons`
--

LOCK TABLES `delivery_persons` WRITE;
/*!40000 ALTER TABLE `delivery_persons` DISABLE KEYS */;
INSERT INTO `delivery_persons` VALUES (1,'Pankaj Kumar','9998889998','3 wheeler tempo','2025-10-04 22:36:59','2025-10-06 01:08:27',1),(2,'Pankaj Kumar','9998889998','3 wheeler tempo','2025-10-04 22:36:59','2025-10-06 01:08:27',1);
/*!40000 ALTER TABLE `delivery_persons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sale`
--

DROP TABLE IF EXISTS `sale`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_no` varchar(255) NOT NULL,
  `date` datetime NOT NULL,
  `shop_id` bigint NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `payment_status` varchar(32) DEFAULT 'PENDING',
  `round_off` decimal(10,2) DEFAULT NULL,
  `synced_flag` tinyint(1) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_no` (`invoice_no`),
  KEY `customer_id` (`customer_id`),
  KEY `idx_sale_shop_customer` (`shop_id`,`customer_id`),
  KEY `idx_sale_shop_id` (`shop_id`),
  CONSTRAINT `sale_ibfk_1` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `sale_ibfk_2` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sale`
--

LOCK TABLES `sale` WRITE;
/*!40000 ALTER TABLE `sale` DISABLE KEYS */;
INSERT INTO `sale` VALUES (1,'1001-202509-001','2025-09-03 17:54:14',1,1,24950.00,'PARTIALLY_PAID',0.00,0,'2025-10-04 22:57:14','2025-10-04 22:57:14'),(2,'1001-202509-002','2025-09-03 17:58:57',1,2,4990.00,'PARTIALLY_PAID',0.00,0,'2025-10-04 22:57:14','2025-10-04 22:57:14'),(3,'1001-202509-003','2025-09-04 01:41:27',1,2,17465.00,'PARTIALLY_PAID',0.00,0,'2025-10-04 22:57:14','2025-10-04 22:57:14'),(4,'1001-202509-004','2025-09-05 00:58:21',1,1,550.00,'PENDING',0.00,0,'2025-10-04 22:57:14','2025-10-04 22:57:14'),(5,'1001-202509-005','2025-09-05 13:35:14',1,2,13965.00,'PARTIALLY_PAID',0.00,0,'2025-10-04 22:57:14','2025-10-04 22:57:14'),(6,'1001-202509-006','2025-09-11 22:53:53',1,1,4485.00,'PENDING',0.00,0,'2025-10-04 22:57:14','2025-10-04 22:57:14'),(7,'1001-202509-007','2025-09-21 15:33:29',1,1,1990.00,'PENDING',0.00,0,'2025-10-04 22:57:14','2025-10-04 22:57:14');
/*!40000 ALTER TABLE `sale` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop`
--

DROP TABLE IF EXISTS `shop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `owner_name` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `state` varchar(255) NOT NULL,
  `gstin` varchar(255) DEFAULT NULL,
  `code` varchar(255) NOT NULL,
  `locale` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop`
--

LOCK TABLES `shop` WRITE;
/*!40000 ALTER TABLE `shop` DISABLE KEYS */;
INSERT INTO `shop` VALUES (1,'Birendra Traders & Co.','Birendra Shaw','Noida Sector 62, Near Metro Station, Opp Fortis Hospital','Uttar Pradesh','09AAACH7409R1ZZ','1001','en','2025-08-25 01:37:47');
/*!40000 ALTER TABLE `shop` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_entry`
--

DROP TABLE IF EXISTS `stock_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_variant_id` bigint NOT NULL,
  `quantity` decimal(10,2) DEFAULT NULL,
  `cost_per_unit` decimal(10,2) NOT NULL,
  `shop_id` bigint DEFAULT NULL,
  `batch` varchar(255) DEFAULT NULL,
  `last_updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_entry_item_variant_id` (`item_variant_id`),
  KEY `idx_stock_entry_shop_id` (`shop_id`),
  CONSTRAINT `fk_stock_entry_shop_id` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `stock_entry_ibfk_1` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_entry`
--

LOCK TABLES `stock_entry` WRITE;
/*!40000 ALTER TABLE `stock_entry` DISABLE KEYS */;
INSERT INTO `stock_entry` VALUES (1,1,5.00,425.00,NULL,'1001-USP','2025-09-03 16:55:28'),(2,1,250.00,500.00,1,'04-09-2025','2025-09-04 11:52:01');
/*!40000 ALTER TABLE `stock_entry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `receiving_item`
--

DROP TABLE IF EXISTS `receiving_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receiving_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiving_id` bigint NOT NULL,
  `po_item_id` bigint NOT NULL,
  `expected_qty` int NOT NULL DEFAULT '0',
  `status` varchar(255) DEFAULT NULL,
  `received_qty` int NOT NULL DEFAULT '0',
  `damaged_qty` int DEFAULT NULL,
  `damage_reason` varchar(255) DEFAULT NULL,
  `notes` text,
  `put_away_status` varchar(255) DEFAULT NULL,
  `putaway_qty` int DEFAULT NULL,
  `rejected_qty` int DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_item_receiving_id` (`receiving_id`),
  KEY `idx_receiving_item_po_item_id` (`po_item_id`),
  KEY `idx_receiving_item_shop_id` (`shop_id`),
  CONSTRAINT `receiving_item_ibfk_1` FOREIGN KEY (`receiving_id`) REFERENCES `receiving` (`id`),
  CONSTRAINT `receiving_item_ibfk_2` FOREIGN KEY (`po_item_id`) REFERENCES `purchase_order_item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `receiving_item`
--

LOCK TABLES `receiving_item` WRITE;
/*!40000 ALTER TABLE `receiving_item` DISABLE KEYS */;
INSERT INTO `receiving_item` VALUES (1,1,1,100,'RECEIVED',100,0,NULL,'All items checked and received','PENDING',0,0,NULL,'2025-10-04 22:42:25','2025-10-06 01:25:30',1),(2,2,2,100,'RECEIVED',100,0,NULL,NULL,NULL,NULL,NULL,NULL,'2025-10-04 22:42:25','2025-10-06 01:25:30',1),(3,3,5,100,'RECEIVED',100,0,NULL,NULL,NULL,NULL,NULL,NULL,'2025-10-04 22:42:25','2025-10-06 01:25:30',1),(4,3,6,100,'RECEIVED',100,0,NULL,NULL,NULL,NULL,NULL,NULL,'2025-10-04 22:42:25','2025-10-06 01:25:30',1),(5,4,7,100,'PARTIALLY_RECEIVED',30,0,'','','PENDING',0,0,'','2025-10-04 22:42:25','2025-10-06 01:25:30',1),(6,4,8,50,'PENDING',0,0,'','','PENDING',0,0,'','2025-10-04 22:42:25','2025-10-06 01:25:30',1),(7,16,9,10,'PENDING',0,0,NULL,NULL,NULL,NULL,NULL,NULL,'2025-10-04 22:42:25','2025-10-06 01:25:30',1),(8,17,10,20,'PENDING',0,0,NULL,NULL,NULL,NULL,NULL,NULL,'2025-10-04 22:42:25','2025-10-06 01:25:30',1);
/*!40000 ALTER TABLE `receiving_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `invoices`
--

DROP TABLE IF EXISTS `invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_number` varchar(100) NOT NULL,
  `sale_id` bigint NOT NULL,
  `invoice_date` datetime DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `customer_phone` varchar(50) DEFAULT NULL,
  `total_amount` decimal(15,2) DEFAULT NULL,
  `paid_amount` decimal(15,2) DEFAULT NULL,
  `due_amount` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_number` (`invoice_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `invoices`
--

LOCK TABLES `invoices` WRITE;
/*!40000 ALTER TABLE `invoices` DISABLE KEYS */;
/*!40000 ALTER TABLE `invoices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `receiving_ticket`
--

DROP TABLE IF EXISTS `receiving_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receiving_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiving_id` bigint NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `description` text,
  `status` varchar(255) DEFAULT NULL,
  `raised_at` datetime DEFAULT NULL,
  `raised_by` varchar(255) DEFAULT NULL,
  `last_updated_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_ticket_receiving_id` (`receiving_id`),
  KEY `idx_receiving_ticket_shop_id` (`shop_id`),
  CONSTRAINT `receiving_ticket_ibfk_1` FOREIGN KEY (`receiving_id`) REFERENCES `receiving` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `receiving_ticket`
--

LOCK TABLES `receiving_ticket` WRITE;
/*!40000 ALTER TABLE `receiving_ticket` DISABLE KEYS */;
/*!40000 ALTER TABLE `receiving_ticket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_order`
--

DROP TABLE IF EXISTS `purchase_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `po_number` varchar(255) NOT NULL,
  `supplier_id` bigint NOT NULL,
  `order_date` datetime NOT NULL,
  `expected_delivery_date` datetime DEFAULT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `payment_status` varchar(32) DEFAULT 'PENDING',
  `status` varchar(255) NOT NULL,
  `notes` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `po_number` (`po_number`),
  UNIQUE KEY `uc_purchase_order_po_number` (`po_number`),
  KEY `idx_purchase_order_supplier_id` (`supplier_id`),
  KEY `idx_purchase_order_shop_id` (`shop_id`),
  CONSTRAINT `purchase_order_ibfk_1` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_order`
--

LOCK TABLES `purchase_order` WRITE;
/*!40000 ALTER TABLE `purchase_order` DISABLE KEYS */;
INSERT INTO `purchase_order` VALUES (1,'KOL123456',1,'2025-09-05 00:00:00','2025-09-06 00:00:00',29900.00,'PENDING','RECEIVED','','2025-10-04 22:41:00','2025-10-06 01:17:09',1),(2,'DEL1234567',2,'2025-09-06 00:00:00','2025-09-13 00:00:00',49900.00,'PENDING','RECEIVED','','2025-10-04 22:41:00','2025-10-06 01:17:09',1),(3,'ABC1234',1,'2025-09-11 00:00:00','2025-09-13 00:00:00',70000.00,'PENDING','RECEIVED','Order generated','2025-10-04 22:41:00','2025-10-06 01:17:09',1),(4,'DEL100',2,'2025-09-12 00:00:00','2025-09-13 00:00:00',67000.00,'PENDING','PARTIALLY_RECEIVED','','2025-10-04 22:41:00','2025-10-06 01:17:09',1),(5,'ABC123456789',1,'2025-09-16 00:00:00','2025-09-18 00:00:00',1000.00,'PENDING','SUBMITTED','','2025-10-04 22:41:00','2025-10-06 01:17:09',1),(6,'0121234567',1,'2025-09-21 00:00:00','2025-09-25 00:00:00',3000.00,'PENDING','SUBMITTED','','2025-10-04 22:41:00','2025-10-06 01:17:09',1);
/*!40000 ALTER TABLE `purchase_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `item`
--

DROP TABLE IF EXISTS `item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` text,
  `brand_name` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `fabric` varchar(255) DEFAULT NULL,
  `season` varchar(255) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `fk_item_category` (`category_id`),
  KEY `idx_item_shop_id` (`shop_id`),
  CONSTRAINT `fk_item_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `item`
--

LOCK TABLES `item` WRITE;
/*!40000 ALTER TABLE `item` DISABLE KEYS */;
INSERT INTO `item` VALUES (1,'Men\'s Jeans','Men\'s Cotton Jeans US Polo.','US Polo.','2025-09-03 16:52:52','Cotton','All-Season',8,'2025-10-06 01:12:33',1),(2,'Saree','','Kanchivaram','2025-09-04 00:05:32','Silk','All-Season',23,'2025-10-06 01:12:33',1),(19,'Formal Shirt','','Louis Philippe','2025-09-04 00:13:37','Cotton','All-Season',1,'2025-10-06 01:12:33',1),(20,'Birendra Shaw','','','2025-09-04 00:22:11','','',1,'2025-10-06 01:12:33',1),(21,'Birendra ','','','2025-09-04 00:23:58','','',1,'2025-10-06 01:12:33',1),(22,'Kids Cloth','dreeses for baby girl','Louis Philippe','2025-09-11 22:50:02','Cotton','All-Season',41,'2025-10-06 01:12:33',1),(23,'Test','Testing','Test','2025-09-21 15:32:04','Cotton','',1,'2025-10-06 01:12:33',1);
/*!40000 ALTER TABLE `item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `receiving`
--

DROP TABLE IF EXISTS `receiving`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receiving` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `received_at` datetime DEFAULT NULL,
  `received_by` varchar(255) DEFAULT NULL,
  `notes` text,
  `last_updated_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_purchase_order_id` (`purchase_order_id`),
  KEY `idx_receiving_shop_id` (`shop_id`),
  CONSTRAINT `fk_receiving_shop_id` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `receiving_ibfk_1` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `receiving`
--

LOCK TABLES `receiving` WRITE;
/*!40000 ALTER TABLE `receiving` DISABLE KEYS */;
INSERT INTO `receiving` VALUES (1,1,1,'COMPLETED','2025-09-12 23:10:56','current-user','Auto-created from PO event','2025-09-12 23:10:56','2025-10-04 22:42:02','2025-10-04 22:42:02'),(2,1,2,'COMPLETED','2025-09-06 04:56:32',NULL,'Manually created receiving record.',NULL,'2025-10-04 22:42:02','2025-10-04 22:42:02'),(3,1,3,'COMPLETED','2025-09-11 23:19:51','system-user','Auto-created from PO event',NULL,'2025-10-04 22:42:02','2025-10-04 22:42:02'),(4,1,4,'PARTIALLY_RECEIVED','2025-09-14 00:30:07','current-user',NULL,'2025-09-14 00:30:07','2025-10-04 22:42:02','2025-10-04 22:42:02'),(16,1,5,'PENDING','2025-09-17 20:39:07','krshaw.birendra@gmail.com','Manually created receiving record.','2025-09-17 20:39:07','2025-10-04 22:42:02','2025-10-04 22:42:02'),(17,1,6,'PENDING','2025-09-21 15:37:42','krshaw.birendra@gmail.com','Manually created receiving record.','2025-09-21 15:37:42','2025-10-04 22:42:02','2025-10-04 22:42:02');
/*!40000 ALTER TABLE `receiving` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `invoice_items`
--

DROP TABLE IF EXISTS `invoice_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint DEFAULT NULL,
  `item_name` varchar(255) DEFAULT NULL,
  `sku` varchar(100) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `price` decimal(15,2) DEFAULT NULL,
  `total` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_invoice` (`invoice_id`),
  CONSTRAINT `fk_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `invoice_items`
--

LOCK TABLES `invoice_items` WRITE;
/*!40000 ALTER TABLE `invoice_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `invoice_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment`
--

DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transaction_id` varchar(255) DEFAULT NULL,
  `source_id` bigint DEFAULT NULL,
  `source_type` varchar(255) DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `amount` decimal(10,2) DEFAULT NULL,
  `payment_date` datetime DEFAULT NULL,
  `payment_method` varchar(255) DEFAULT NULL,
  `reference` varchar(255) DEFAULT NULL,
  `notes` text,
  `status` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_transaction_id` (`transaction_id`),
  KEY `idx_payment_source` (`source_type`,`source_id`),
  KEY `idx_payment_supplier` (`supplier_id`),
  KEY `idx_payment_customer` (`customer_id`),
  KEY `idx_payment_shop_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment`
--

LOCK TABLES `payment` WRITE;
/*!40000 ALTER TABLE `payment` DISABLE KEYS */;
INSERT INTO `payment` VALUES (1,NULL,2,'SALE',NULL,2,2000.00,'2025-09-03 17:58:57','CASH','','','PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(2,'10001234',2,'SALE',NULL,2,990.00,'2025-09-03 17:58:57','UPI','','','PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(3,NULL,2,'SALE',NULL,2,500.00,'2025-09-09 22:31:18','CASH',NULL,'500 cash','PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(4,NULL,1,'SALE',NULL,1,950.00,'2025-09-09 22:32:22','CASH',NULL,NULL,'PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(5,'appu12345',1,'SALE',NULL,1,5000.00,'2025-09-09 22:32:40','UPI',NULL,NULL,'PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(6,NULL,2,'SALE',NULL,2,197.00,'2025-09-09 22:43:26','CASH',NULL,NULL,'PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(7,NULL,3,'SALE',NULL,2,5000.00,'2025-09-09 23:31:15','CASH',NULL,NULL,'PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(8,NULL,5,'SALE',NULL,2,500.00,'2025-09-09 23:31:49','CASH',NULL,NULL,'PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1),(9,NULL,5,'SALE',NULL,2,4500.00,'2025-09-09 23:32:08','CASH',NULL,NULL,'PARTIALLY_PAID','2025-10-04 22:40:22','2025-10-06 01:15:44',1);
/*!40000 ALTER TABLE `payment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(255) DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `recipient` varchar(255) DEFAULT NULL,
  `read` tinyint(1) DEFAULT NULL,
  `link` varchar(255) DEFAULT NULL,
  `timestamp` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notification`
--

LOCK TABLES `notification` WRITE;
/*!40000 ALTER TABLE `notification` DISABLE KEYS */;
/*!40000 ALTER TABLE `notification` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-10-14 15:17:50
