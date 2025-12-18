# 📘 DOCUMENTACIÓN COMPLETA - BACKEND SPRING BOOT PARA NOVEDADES LZ

## 📋 ÍNDICE

1. [Visión General del Proyecto](#1-visión-general-del-proyecto)
2. [Arquitectura del Sistema](#2-arquitectura-del-sistema)
3. [Tecnologías y Dependencias](#3-tecnologías-y-dependencias)
4. [Estructura del Proyecto](#4-estructura-del-proyecto)
5. [Modelo de Datos](#5-modelo-de-datos)
6. [Endpoints API REST](#6-endpoints-api-rest)
7. [Seguridad y Autenticación](#7-seguridad-y-autenticación)
8. [Lógica de Negocio](#8-lógica-de-negocio)
9. [Validaciones](#9-validaciones)
10. [Manejo de Errores](#10-manejo-de-errores)
11. [Configuración](#11-configuración)
12. [Scripts SQL](#12-scripts-sql)
13. [Testing](#13-testing)
14. [Deployment](#14-deployment)
15. [Integración con Frontend React](#15-integración-con-frontend-react)

---

## 1. VISIÓN GENERAL DEL PROYECTO

### 1.1 Descripción
Backend empresarial para **Novedades LZ**, un sistema e-commerce peruano con gestión de productos, pedidos y validación de pagos mediante Yape.

### 1.2 Objetivos
- ✅ API REST robusta y escalable
- ✅ Autenticación JWT segura
- ✅ Gestión transaccional de inventario
- ✅ Validación de pagos Yape con OCR
- ✅ Integración con servicios externos (Cloudinary, OCR.space)
- ✅ Documentación automática con Swagger

### 1.3 Stack Tecnológico
```
Backend Framework: Spring Boot 3.2.x
Lenguaje: Java 21+
Base de Datos: Oracle Database 21c XE
ORM: Hibernate (JPA)
Seguridad: Spring Security + JWT
Documentación: SpringDoc OpenAPI 3
Build Tool: Maven 3.9+
```

---

## 2. ARQUITECTURA DEL SISTEMA

### 2.1 Arquitectura en Capas

```
┌─────────────────────────────────────────────┐
│         FRONTEND (React + TypeScript)       │
└─────────────────┬───────────────────────────┘
                  │ HTTP/REST (JSON)
┌─────────────────▼───────────────────────────┐
│           CONTROLLER LAYER                  │
│  ├── ProductController                      │
│  ├── OrderController                        │
│  ├── AuthController                         │
│  └── PaymentController                      │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│            SERVICE LAYER                    │
│  ├── ProductService                         │
│  ├── OrderService                           │
│  ├── UserService                            │
│  ├── PaymentValidationService              │
│  └── OCRService                             │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│          REPOSITORY LAYER (JPA)             │
│  ├── ProductRepository                      │
│  ├── OrderRepository                        │
│  ├── OrderItemRepository                    │
│  └── UserRepository                         │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│         ORACLE DATABASE 21c                 │
│  ├── PRODUCTS                               │
│  ├── ORDERS                                 │
│  ├── ORDER_ITEMS                            │
│  └── USERS                                  │
└─────────────────────────────────────────────┘
```

### 2.2 Flujo de Datos

```
1. Request HTTP → Controller
2. Controller → DTO Validation
3. Controller → Service (Business Logic)
4. Service → Repository (Data Access)
5. Repository → Oracle Database
6. Database → Entity Mapping (JPA)
7. Entity → DTO → JSON Response
```

---

## 3. TECNOLOGÍAS Y DEPENDENCIAS

### 3.1 pom.xml Completo

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.novedadeslz</groupId>
    <artifactId>backend</artifactId>
    <version>1.0.0</version>
    <name>Novedades LZ Backend</name>
    <description>Backend API para sistema e-commerce Novedades LZ</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Bean Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Oracle JDBC Driver -->
        <dependency>
            <groupId>com.oracle.database.jdbc</groupId>
            <artifactId>ojdbc11</artifactId>
            <version>21.9.0.0</version>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.3</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.3</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.3</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- SpringDoc OpenAPI (Swagger) -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.3.0</version>
        </dependency>

        <!-- Apache HttpClient (para OCR.space API) -->
        <dependency>
            <groupId>org.apache.httpcomponents.client5</groupId>
            <artifactId>httpclient5</artifactId>
            <version>5.3.1</version>
        </dependency>

        <!-- Jackson para JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- ModelMapper (Entity <-> DTO) -->
        <dependency>
            <groupId>org.modelmapper</groupId>
            <artifactId>modelmapper</artifactId>
            <version>3.2.0</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- H2 Database (para tests) -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. ESTRUCTURA DEL PROYECTO

### 4.1 Árbol de Directorios

```
src/main/java/com/novedadeslz/backend/
│
├── BackendApplication.java                 # Clase principal
│
├── config/                                  # Configuraciones
│   ├── SecurityConfig.java                 # Spring Security + JWT
│   ├── CorsConfig.java                     # CORS para React
│   ├── ModelMapperConfig.java              # Entity-DTO mapping
│   ├── OpenApiConfig.java                  # Swagger UI
│   └── CloudinaryConfig.java               # Cliente Cloudinary
│
├── controller/                              # Controladores REST
│   ├── AuthController.java                 # Login, Register
│   ├── ProductController.java              # CRUD Productos
│   ├── OrderController.java                # CRUD Pedidos
│   └── PaymentController.java              # Validación Yape
│
├── service/                                 # Lógica de negocio
│   ├── UserService.java
│   ├── ProductService.java
│   ├── OrderService.java
│   ├── PaymentValidationService.java       # Validación OCR
│   ├── OCRService.java                     # OCR.space integration
│   ├── CloudinaryService.java              # Upload imágenes
│   └── StockService.java                   # Gestión inventario
│
├── repository/                              # Acceso a datos (JPA)
│   ├── UserRepository.java
│   ├── ProductRepository.java
│   ├── OrderRepository.java
│   └── OrderItemRepository.java
│
├── model/                                   # Entidades JPA
│   ├── User.java
│   ├── Product.java
│   ├── Order.java
│   └── OrderItem.java
│
├── dto/                                     # Data Transfer Objects
│   ├── request/
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── ProductRequest.java
│   │   ├── OrderRequest.java
│   │   └── PaymentValidationRequest.java
│   └── response/
│       ├── AuthResponse.java               # Token + User info
│       ├── ProductResponse.java
│       ├── OrderResponse.java
│       ├── PaymentValidationResponse.java
│       └── ApiResponse.java                # Generic response
│
├── security/                                # Componentes de seguridad
│   ├── JwtTokenProvider.java               # Generar/validar JWT
│   ├── JwtAuthenticationFilter.java        # Filtro por request
│   └── UserDetailsServiceImpl.java         # Cargar usuario
│
├── exception/                               # Manejo de errores
│   ├── GlobalExceptionHandler.java         # @ControllerAdvice
│   ├── ResourceNotFoundException.java
│   ├── BadRequestException.java
│   ├── UnauthorizedException.java
│   └── DuplicateResourceException.java
│
└── util/                                    # Utilidades
    ├── Constants.java                       # Constantes globales
    ├── DateUtils.java                       # Helpers de fecha
    └── ValidationUtils.java                 # Validaciones custom

src/main/resources/
├── application.properties                   # Configuración principal
├── application-dev.properties               # Perfil desarrollo
├── application-prod.properties              # Perfil producción
└── db/
    └── migration/
        ├── V1__create_users_table.sql
        ├── V2__create_products_table.sql
        ├── V3__create_orders_table.sql
        └── V4__create_order_items_table.sql
```

---

## 5. MODELO DE DATOS

### 5.1 Diagrama ER

```
┌─────────────────┐
│     USERS       │
├─────────────────┤
│ id (PK)         │
│ email (UNIQUE)  │
│ password_hash   │
│ full_name       │
│ phone           │
│ role            │
│ created_at      │
│ updated_at      │
└─────────────────┘

┌─────────────────┐
│    PRODUCTS     │
├─────────────────┤
│ id (PK)         │
│ name            │
│ description     │
│ price           │
│ image_url       │
│ category        │
│ stock           │
│ active          │
│ created_at      │
│ updated_at      │
└─────────────────┘

┌─────────────────┐          ┌──────────────────┐
│     ORDERS      │          │   ORDER_ITEMS    │
├─────────────────┤          ├──────────────────┤
│ id (PK)         │◄─────────┤ id (PK)          │
│ order_number    │        1:N order_id (FK)    │
│ customer_name   │          │ product_id (FK)  │────┐
│ customer_phone  │          │ product_name     │    │
│ customer_email  │          │ quantity         │    │
│ customer_address│          │ unit_price       │    │
│ customer_city   │          │ subtotal         │    │
│ total           │          └──────────────────┘    │
│ status          │                                  │
│ payment_method  │                                  │
│ payment_proof   │                                  │
│ operation_number│                                  │
│ whatsapp_sent   │          ┌─────────────────┐    │
│ notes           │          │    PRODUCTS     │◄───┘
│ created_at      │          │ (referencia)    │
│ updated_at      │          └─────────────────┘
└─────────────────┘
```

### 5.2 Entidades JPA

#### 5.2.1 User.java

```java
package com.novedadeslz.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "user_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.ADMIN;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Role {
        ADMIN,
        USER
    }
}
```

#### 5.2.2 Product.java

```java
package com.novedadeslz.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business logic
    public void decreaseStock(Integer quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException(
                "Stock insuficiente. Disponible: " + this.stock + ", Solicitado: " + quantity
            );
        }
        this.stock -= quantity;
    }

    public void increaseStock(Integer quantity) {
        this.stock += quantity;
    }

    public boolean isLowStock() {
        return this.stock <= 5;
    }
}
```

#### 5.2.3 Order.java

```java
package com.novedadeslz.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(columnNames = "operation_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)
    private Long id;

    @Column(name = "order_number", unique = true, nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "customer_email", length = 100)
    private String customerEmail;

    @Column(name = "customer_address", length = 300)
    private String customerAddress;

    @Column(name = "customer_city", length = 100)
    private String customerCity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "payment_proof", length = 500)
    private String paymentProof;

    @Column(name = "operation_number", unique = true, length = 50)
    private String operationNumber;

    @Column(name = "whatsapp_sent")
    private Boolean whatsappSent = false;

    @Column(columnDefinition = "CLOB")
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        DELIVERED,
        CANCELLED
    }

    // Helper methods
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public BigDecimal calculateTotal() {
        return items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

#### 5.2.4 OrderItem.java

```java
package com.novedadeslz.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")
    @SequenceGenerator(name = "order_item_seq", sequenceName = "order_item_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @PrePersist
    @PreUpdate
    public void calculateSubtotal() {
        if (unitPrice != null && quantity != null) {
            this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
```

---

## 6. ENDPOINTS API REST

### 6.1 Autenticación

#### POST /api/auth/register
```json
Request:
{
  "email": "admin@novedadeslz.com",
  "password": "Admin123!",
  "fullName": "Administrador LZ",
  "phone": "+51939662630"
}

Response (201):
{
  "success": true,
  "message": "Usuario registrado exitosamente",
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "type": "Bearer",
    "user": {
      "id": 1,
      "email": "admin@novedadeslz.com",
      "fullName": "Administrador LZ",
      "role": "ADMIN"
    }
  }
}
```

#### POST /api/auth/login
```json
Request:
{
  "email": "admin@novedadeslz.com",
  "password": "Admin123!"
}

Response (200):
{
  "success": true,
  "message": "Login exitoso",
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "type": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "email": "admin@novedadeslz.com",
      "fullName": "Administrador LZ",
      "role": "ADMIN"
    }
  }
}
```

#### GET /api/auth/me
```
Headers: Authorization: Bearer {token}

Response (200):
{
  "success": true,
  "data": {
    "id": 1,
    "email": "admin@novedadeslz.com",
    "fullName": "Administrador LZ",
    "role": "ADMIN",
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

---

### 6.2 Productos

#### GET /api/products
```
Query params:
  - category: String (opcional)
  - search: String (opcional)
  - active: Boolean (default: true)
  - sort: String (name, price, stock, createdAt)
  - direction: String (asc, desc)
  - page: int (default: 0)
  - size: int (default: 20)

Response (200):
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Producto 1",
        "description": "Descripción del producto",
        "price": 99.90,
        "imageUrl": "https://res.cloudinary.com/...",
        "category": "Electrónica",
        "stock": 10,
        "active": true,
        "createdAt": "2024-01-15T10:00:00",
        "lowStock": false
      }
    ],
    "totalElements": 25,
    "totalPages": 2,
    "currentPage": 0,
    "pageSize": 20
  }
}
```

#### GET /api/products/{id}
```
Response (200):
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Producto 1",
    "description": "Descripción detallada...",
    "price": 99.90,
    "imageUrl": "https://res.cloudinary.com/...",
    "category": "Electrónica",
    "stock": 10,
    "active": true,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  }
}
```

#### POST /api/products
```
Headers: Authorization: Bearer {token}

Request:
{
  "name": "Producto Nuevo",
  "description": "Descripción del producto",
  "price": 149.90,
  "imageUrl": "https://res.cloudinary.com/...",
  "category": "Hogar",
  "stock": 20
}

Response (201):
{
  "success": true,
  "message": "Producto creado exitosamente",
  "data": {
    "id": 26,
    "name": "Producto Nuevo",
    "price": 149.90,
    "stock": 20,
    "createdAt": "2024-01-20T15:30:00"
  }
}
```

#### PUT /api/products/{id}
```
Headers: Authorization: Bearer {token}

Request:
{
  "name": "Producto Actualizado",
  "price": 159.90,
  "stock": 25
}

Response (200):
{
  "success": true,
  "message": "Producto actualizado exitosamente",
  "data": { ... }
}
```

#### DELETE /api/products/{id}
```
Headers: Authorization: Bearer {token}

Response (200):
{
  "success": true,
  "message": "Producto eliminado exitosamente"
}
```

#### GET /api/products/stats
```
Headers: Authorization: Bearer {token}

Response (200):
{
  "success": true,
  "data": {
    "totalProducts": 25,
    "totalValue": 12450.50,
    "lowStockProducts": 3,
    "outOfStockProducts": 1,
    "categories": {
      "Electrónica": 8,
      "Hogar": 12,
      "Deportes": 5
    }
  }
}
```

---

### 6.3 Pedidos

#### POST /api/orders
```
Request:
{
  "customerName": "Juan Pérez",
  "customerPhone": "+51987654321",
  "customerEmail": "juan@email.com",
  "customerAddress": "Av. Principal 123",
  "customerCity": "Lima",
  "paymentMethod": "yape",
  "items": [
    {
      "productId": 1,
      "quantity": 2
    },
    {
      "productId": 3,
      "quantity": 1
    }
  ]
}

Response (201):
{
  "success": true,
  "message": "Pedido creado exitosamente",
  "data": {
    "id": 100,
    "orderNumber": "ORD-20240120-0001",
    "customerName": "Juan Pérez",
    "total": 299.70,
    "status": "PENDING",
    "items": [
      {
        "productName": "Producto 1",
        "quantity": 2,
        "unitPrice": 99.90,
        "subtotal": 199.80
      },
      {
        "productName": "Producto 3",
        "quantity": 1,
        "unitPrice": 99.90,
        "subtotal": 99.90
      }
    ],
    "createdAt": "2024-01-20T16:00:00"
  }
}
```

#### GET /api/orders
```
Headers: Authorization: Bearer {token}

Query params:
  - status: OrderStatus (opcional)
  - customerPhone: String (opcional)
  - startDate: ISO Date (opcional)
  - endDate: ISO Date (opcional)
  - page: int
  - size: int

Response (200):
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 100,
        "orderNumber": "ORD-20240120-0001",
        "customerName": "Juan Pérez",
        "customerPhone": "+51987654321",
        "total": 299.70,
        "status": "PENDING",
        "paymentMethod": "yape",
        "createdAt": "2024-01-20T16:00:00"
      }
    ],
    "totalElements": 50,
    "totalPages": 3
  }
}
```

#### GET /api/orders/{id}
```
Response (200):
{
  "success": true,
  "data": {
    "id": 100,
    "orderNumber": "ORD-20240120-0001",
    "customerName": "Juan Pérez",
    "customerPhone": "+51987654321",
    "customerEmail": "juan@email.com",
    "customerAddress": "Av. Principal 123",
    "customerCity": "Lima",
    "total": 299.70,
    "status": "PENDING",
    "paymentMethod": "yape",
    "paymentProof": "https://res.cloudinary.com/...",
    "operationNumber": "12345678",
    "whatsappSent": true,
    "items": [...],
    "createdAt": "2024-01-20T16:00:00"
  }
}
```

#### PUT /api/orders/{id}/status
```
Headers: Authorization: Bearer {token}

Request:
{
  "status": "CONFIRMED"
}

Response (200):
{
  "success": true,
  "message": "Estado del pedido actualizado a CONFIRMED",
  "data": {
    "id": 100,
    "status": "CONFIRMED",
    "updatedAt": "2024-01-20T17:00:00"
  }
}

Nota: Al cambiar a CONFIRMED, se descuenta el stock automáticamente
```

#### DELETE /api/orders/{id}
```
Headers: Authorization: Bearer {token}

Response (200):
{
  "success": true,
  "message": "Pedido eliminado exitosamente"
}
```

---

### 6.4 Validación de Pagos

#### POST /api/payments/validate-yape
```
Request (multipart/form-data):
{
  "orderId": 100,
  "imageFile": File (captura Yape),
  "expectedAmount": 299.70
}

Response (200):
{
  "success": true,
  "message": "Pago validado exitosamente",
  "data": {
    "valid": true,
    "operationNumber": "12345678",
    "amount": 299.70,
    "date": "2024-01-20",
    "paymentProofUrl": "https://res.cloudinary.com/...",
    "validations": {
      "amountMatch": true,
      "dateValid": true,
      "isYape": true,
      "operationUnique": true
    }
  }
}

Response (400) - Validación fallida:
{
  "success": false,
  "message": "Validación de pago fallida",
  "errors": [
    "El monto no coincide. Esperado: S/. 299.70, Detectado: S/. 250.00",
    "La fecha del pago no es de hoy"
  ]
}
```

#### POST /api/payments/check-operation
```
Request:
{
  "operationNumber": "12345678"
}

Response (200):
{
  "success": true,
  "data": {
    "exists": true,
    "orderId": 100,
    "usedAt": "2024-01-20T16:30:00"
  }
}
```

---

## 7. SEGURIDAD Y AUTENTICACIÓN

### 7.1 SecurityConfig.java

```java
package com.novedadeslz.backend.config;

import com.novedadeslz.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configure(http))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/{id}").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/validate-yape").permitAll()

                // Swagger
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                // Endpoints protegidos
                .requestMatchers("/api/products/**").hasRole("ADMIN")
                .requestMatchers("/api/orders/**").hasRole("ADMIN")
                .requestMatchers("/api/payments/admin/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 7.2 JwtTokenProvider.java

```java
package com.novedadeslz.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 horas en ms
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .setSubject(userPrincipal.getUsername())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
            .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(authToken);
            return true;
        } catch (SecurityException ex) {
            // Invalid JWT signature
        } catch (MalformedJwtException ex) {
            // Invalid JWT token
        } catch (ExpiredJwtException ex) {
            // Expired JWT token
        } catch (UnsupportedJwtException ex) {
            // Unsupported JWT token
        } catch (IllegalArgumentException ex) {
            // JWT claims string is empty
        }
        return false;
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}
```

### 7.3 JwtAuthenticationFilter.java

```java
package com.novedadeslz.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                    );

                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("No se pudo establecer autenticación de usuario", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
```

---

## 8. LÓGICA DE NEGOCIO

### 8.1 OrderService.java

```java
package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.exception.ResourceNotFoundException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.model.OrderItem;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.OrderRepository;
import com.novedadeslz.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        // Validar que hay items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("El pedido debe tener al menos un producto");
        }

        // Crear orden
        Order order = Order.builder()
            .orderNumber(generateOrderNumber())
            .customerName(request.getCustomerName())
            .customerPhone(request.getCustomerPhone())
            .customerEmail(request.getCustomerEmail())
            .customerAddress(request.getCustomerAddress())
            .customerCity(request.getCustomerCity())
            .paymentMethod(request.getPaymentMethod())
            .status(Order.OrderStatus.PENDING)
            .whatsappSent(false)
            .build();

        // Agregar items y calcular total
        BigDecimal total = BigDecimal.ZERO;

        for (var itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Producto no encontrado con ID: " + itemRequest.getProductId()
                ));

            // Validar stock disponible
            if (product.getStock() < itemRequest.getQuantity()) {
                throw new BadRequestException(
                    "Stock insuficiente para " + product.getName() +
                    ". Disponible: " + product.getStock()
                );
            }

            // Crear item
            OrderItem item = OrderItem.builder()
                .product(product)
                .productName(product.getName())
                .quantity(itemRequest.getQuantity())
                .unitPrice(product.getPrice())
                .build();

            item.calculateSubtotal();
            order.addItem(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotal(total);

        Order savedOrder = orderRepository.save(order);
        return modelMapper.map(savedOrder, OrderResponse.class);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        // Si se confirma el pedido, descontar stock
        if (newStatus == Order.OrderStatus.CONFIRMED &&
            oldStatus != Order.OrderStatus.CONFIRMED) {

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.decreaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        // Si se cancela un pedido confirmado, devolver stock
        if (newStatus == Order.OrderStatus.CANCELLED &&
            oldStatus == Order.OrderStatus.CONFIRMED) {

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        Order updatedOrder = orderRepository.save(order);
        return modelMapper.map(updatedOrder, OrderResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(
            Order.OrderStatus status,
            String customerPhone,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Page<Order> orders;

        if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else if (customerPhone != null) {
            orders = orderRepository.findByCustomerPhoneContaining(customerPhone, pageable);
        } else if (startDate != null && endDate != null) {
            orders = orderRepository.findByCreatedAtBetween(startDate, endDate, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orders.map(order -> modelMapper.map(order, OrderResponse.class));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return modelMapper.map(order, OrderResponse.class);
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        // Si el pedido está confirmado, devolver stock
        if (order.getStatus() == Order.OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        orderRepository.delete(order);
    }

    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = orderRepository.countByOrderNumberStartingWith("ORD-" + timestamp);

        return String.format("ORD-%s-%04d", timestamp, count + 1);
    }
}
```

### 8.2 PaymentValidationService.java

```java
package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.response.PaymentValidationResponse;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentValidationService {

    private final OCRService ocrService;
    private final CloudinaryService cloudinaryService;
    private final OrderRepository orderRepository;

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.50");

    @Transactional
    public PaymentValidationResponse validateYapePayment(
            Long orderId,
            MultipartFile imageFile,
            BigDecimal expectedAmount) {

        List<String> errors = new ArrayList<>();

        // 1. Procesar OCR
        String ocrText = ocrService.extractText(imageFile);
        log.info("Texto OCR extraído: {}", ocrText);

        // 2. Validar que es Yape
        if (!ocrText.toLowerCase().contains("yape")) {
            errors.add("La imagen no parece ser una captura de Yape");
        }

        // 3. Extraer monto
        BigDecimal detectedAmount = extractAmount(ocrText);
        if (detectedAmount == null) {
            errors.add("No se pudo detectar el monto en la imagen");
        } else {
            // Validar monto con tolerancia
            BigDecimal difference = detectedAmount.subtract(expectedAmount).abs();
            if (difference.compareTo(AMOUNT_TOLERANCE) > 0) {
                errors.add(String.format(
                    "El monto no coincide. Esperado: S/. %.2f, Detectado: S/. %.2f",
                    expectedAmount, detectedAmount
                ));
            }
        }

        // 4. Extraer número de operación
        String operationNumber = extractOperationNumber(ocrText);
        if (operationNumber == null) {
            errors.add("No se pudo detectar el número de operación");
        } else {
            // Verificar duplicados
            if (orderRepository.existsByOperationNumber(operationNumber)) {
                errors.add("Este número de operación ya fue usado en otro pedido");
            }
        }

        // 5. Validar fecha (debe ser hoy)
        LocalDate paymentDate = extractDate(ocrText);
        if (paymentDate == null) {
            errors.add("No se pudo detectar la fecha del pago");
        } else if (!paymentDate.equals(LocalDate.now())) {
            errors.add("La fecha del pago debe ser de hoy");
        }

        // Si hay errores, retornar validación fallida
        if (!errors.isEmpty()) {
            return PaymentValidationResponse.builder()
                .valid(false)
                .errors(errors)
                .build();
        }

        // 6. Subir imagen a Cloudinary
        String imageUrl = cloudinaryService.uploadImage(imageFile, "payment-proofs");

        // 7. Actualizar orden
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BadRequestException("Pedido no encontrado"));

        order.setPaymentProof(imageUrl);
        order.setOperationNumber(operationNumber);
        orderRepository.save(order);

        // 8. Retornar validación exitosa
        return PaymentValidationResponse.builder()
            .valid(true)
            .operationNumber(operationNumber)
            .amount(detectedAmount)
            .date(paymentDate)
            .paymentProofUrl(imageUrl)
            .build();
    }

    private BigDecimal extractAmount(String text) {
        // Patrón: S/ 123.45 o S/. 123.45
        Pattern pattern = Pattern.compile("S/\\.?\\s*(\\d{1,10}[,.]\\d{2})");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String amountStr = matcher.group(1).replace(",", ".");
            return new BigDecimal(amountStr);
        }

        return null;
    }

    private String extractOperationNumber(String text) {
        // Patrón: 8 dígitos consecutivos
        Pattern pattern = Pattern.compile("\\b(\\d{8})\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private LocalDate extractDate(String text) {
        // Patrón: DD mes YYYY (ej: "20 ene. 2024")
        Pattern pattern = Pattern.compile(
            "(\\d{1,2})\\s+(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)\\.?\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String day = matcher.group(1);
            String month = matcher.group(2);
            String year = matcher.group(3);

            // Convertir mes abreviado a número
            int monthNum = getMonthNumber(month);

            String dateStr = String.format("%s-%02d-%02d",
                year, monthNum, Integer.parseInt(day));

            return LocalDate.parse(dateStr);
        }

        return null;
    }

    private int getMonthNumber(String monthAbbr) {
        return switch (monthAbbr.toLowerCase()) {
            case "ene" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "abr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "ago" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dic" -> 12;
            default -> 0;
        };
    }
}
```

### 8.3 OCRService.java

```java
package com.novedadeslz.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class OCRService {

    @Value("${ocr.space.api-key}")
    private String ocrApiKey;

    private static final String OCR_API_URL = "https://api.ocr.space/parse/image";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractText(MultipartFile file) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(OCR_API_URL);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file.getInputStream(),
                org.apache.hc.core5.http.ContentType.APPLICATION_OCTET_STREAM,
                file.getOriginalFilename());
            builder.addTextBody("apikey", ocrApiKey);
            builder.addTextBody("language", "spa");
            builder.addTextBody("isOverlayRequired", "false");
            builder.addTextBody("OCREngine", "2");
            builder.addTextBody("scale", "true");
            builder.addTextBody("detectOrientation", "true");

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                HttpEntity responseEntity = response.getEntity();
                String jsonResponse = EntityUtils.toString(responseEntity);

                log.debug("OCR Response: {}", jsonResponse);

                JsonNode root = objectMapper.readTree(jsonResponse);

                if (root.has("ParsedResults") && root.get("ParsedResults").size() > 0) {
                    return root.get("ParsedResults").get(0)
                        .get("ParsedText").asText();
                }

                log.error("OCR no pudo procesar la imagen");
                return "";
            }
        } catch (Exception e) {
            log.error("Error al procesar OCR: ", e);
            return "";
        }
    }
}
```

---

## 9. VALIDACIONES

### 9.1 ProductRequest.java

```java
package com.novedadeslz.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
    private String name;

    @Size(max = 2000, message = "La descripción no puede exceder 2000 caracteres")
    private String description;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "Formato de precio inválido")
    private BigDecimal price;

    @Size(max = 500, message = "La URL de la imagen es muy larga")
    private String imageUrl;

    @NotBlank(message = "La categoría es obligatoria")
    @Size(max = 100, message = "La categoría no puede exceder 100 caracteres")
    private String category;

    @NotNull(message = "El stock es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;
}
```

### 9.2 OrderRequest.java

```java
package com.novedadeslz.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequest {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String customerName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^\\+?51?9\\d{8}$",
        message = "Formato de teléfono peruano inválido. Ej: +51987654321")
    private String customerPhone;

    @Email(message = "Email inválido")
    private String customerEmail;

    @Size(max = 300, message = "La dirección no puede exceder 300 caracteres")
    private String customerAddress;

    @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres")
    private String customerCity;

    @Pattern(regexp = "yape|plin|transfer|cash",
        message = "Método de pago no válido")
    private String paymentMethod;

    @NotEmpty(message = "El pedido debe contener al menos un producto")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull(message = "El ID del producto es obligatorio")
        private Long productId;

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        private Integer quantity;
    }
}
```

---

## 10. MANEJO DE ERRORES

### 10.1 GlobalExceptionHandler.java

```java
package com.novedadeslz.backend.exception;

import com.novedadeslz.backend.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            BadRequestException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(
            DuplicateResourceException ex) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("No tienes permisos para acceder a este recurso")
                .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("Credenciales inválidas")
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Errores de validación")
                .data(errors)
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex) {
        ex.printStackTrace();
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("Error interno del servidor")
                .build());
    }
}
```

---

## 11. CONFIGURACIÓN

### 11.1 application.properties

```properties
# Application
spring.application.name=novedades-lz-backend
server.port=8080

# Oracle Database
spring.datasource.url=jdbc:oracle:thin:@localhost:1521:XE
spring.datasource.username=novedadeslz
spring.datasource.password=yourpassword
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.OracleDialect
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true

# JWT
jwt.secret=TuClaveSecretaSuperSeguraDeAlMenos64CaracteresParaHS512Algorithm
jwt.expiration=86400000

# OCR.space API
ocr.space.api-key=K87953488688957

# Cloudinary
cloudinary.cloud-name=your-cloud-name
cloudinary.api-key=your-api-key
cloudinary.api-secret=your-api-secret

# File Upload
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB

# CORS
cors.allowed-origins=http://localhost:5173,http://localhost:3000

# Logging
logging.level.com.novedadeslz=DEBUG
logging.level.org.springframework.security=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
```

### 11.2 application-prod.properties

```properties
# Production Database
spring.datasource.url=jdbc:oracle:thin:@your-prod-host:1521:XE
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# JWT (usar variable de entorno)
jwt.secret=${JWT_SECRET}

# OCR
ocr.space.api-key=${OCR_API_KEY}

# Cloudinary
cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME}
cloudinary.api-key=${CLOUDINARY_API_KEY}
cloudinary.api-secret=${CLOUDINARY_API_SECRET}

# CORS
cors.allowed-origins=${FRONTEND_URL}

# Logging
logging.level.com.novedadeslz=INFO
logging.level.org.springframework.security=WARN
```

---

## 12. SCRIPTS SQL

### 12.1 V1__create_users_table.sql

```sql
-- Crear secuencia para usuarios
CREATE SEQUENCE user_seq START WITH 1 INCREMENT BY 1;

-- Tabla de usuarios
CREATE TABLE users (
    id NUMBER PRIMARY KEY,
    email VARCHAR2(100) NOT NULL UNIQUE,
    password_hash VARCHAR2(255) NOT NULL,
    full_name VARCHAR2(150) NOT NULL,
    phone VARCHAR2(20),
    role VARCHAR2(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índices
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Usuario admin por defecto (password: Admin123!)
-- Hash BCrypt de "Admin123!"
INSERT INTO users (id, email, password_hash, full_name, phone, role, active)
VALUES (
    user_seq.NEXTVAL,
    'admin@novedadeslz.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVqHBqZXmi',
    'Administrador LZ',
    '+51939662630',
    'ADMIN',
    1
);

COMMIT;
```

### 12.2 V2__create_products_table.sql

```sql
-- Crear secuencia para productos
CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 1;

-- Tabla de productos
CREATE TABLE products (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    description CLOB,
    price NUMBER(10,2) NOT NULL CHECK (price > 0),
    image_url VARCHAR2(500),
    category VARCHAR2(100),
    stock NUMBER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índices
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_name ON products(name);

-- Datos de ejemplo
INSERT INTO products (id, name, description, price, category, stock)
VALUES (
    product_seq.NEXTVAL,
    'Auriculares Bluetooth',
    'Auriculares inalámbricos con cancelación de ruido',
    129.90,
    'Electrónica',
    15
);

INSERT INTO products (id, name, description, price, category, stock)
VALUES (
    product_seq.NEXTVAL,
    'Botella Térmica',
    'Botella térmica de acero inoxidable 500ml',
    45.00,
    'Hogar',
    25
);

COMMIT;
```

### 12.3 V3__create_orders_table.sql

```sql
-- Crear secuencia para pedidos
CREATE SEQUENCE order_seq START WITH 1 INCREMENT BY 1;

-- Tabla de pedidos
CREATE TABLE orders (
    id NUMBER PRIMARY KEY,
    order_number VARCHAR2(50) NOT NULL UNIQUE,
    customer_name VARCHAR2(150) NOT NULL,
    customer_phone VARCHAR2(20) NOT NULL,
    customer_email VARCHAR2(100),
    customer_address VARCHAR2(300),
    customer_city VARCHAR2(100),
    total NUMBER(10,2) NOT NULL,
    status VARCHAR2(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED')),
    payment_method VARCHAR2(20),
    payment_proof VARCHAR2(500),
    operation_number VARCHAR2(50) UNIQUE,
    whatsapp_sent NUMBER(1) DEFAULT 0,
    notes CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índices
CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_customer_phone ON orders(customer_phone);
CREATE INDEX idx_orders_operation_number ON orders(operation_number);
CREATE INDEX idx_orders_created_at ON orders(created_at);

COMMIT;
```

### 12.4 V4__create_order_items_table.sql

```sql
-- Crear secuencia para items de pedido
CREATE SEQUENCE order_item_seq START WITH 1 INCREMENT BY 1;

-- Tabla de items de pedido
CREATE TABLE order_items (
    id NUMBER PRIMARY KEY,
    order_id NUMBER NOT NULL,
    product_id NUMBER NOT NULL,
    product_name VARCHAR2(200) NOT NULL,
    quantity NUMBER NOT NULL CHECK (quantity > 0),
    unit_price NUMBER(10,2) NOT NULL,
    subtotal NUMBER(10,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id)
        REFERENCES products(id)
);

-- Índices
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

COMMIT;
```

---

## 13. TESTING

### 13.1 ProductServiceTest.java

```java
package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.ProductRequest;
import com.novedadeslz.backend.dto.response.ProductResponse;
import com.novedadeslz.backend.exception.ResourceNotFoundException;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        product = Product.builder()
            .id(1L)
            .name("Test Product")
            .price(new BigDecimal("99.90"))
            .stock(10)
            .category("Test")
            .active(true)
            .build();

        productRequest = new ProductRequest();
        productRequest.setName("Test Product");
        productRequest.setPrice(new BigDecimal("99.90"));
        productRequest.setStock(10);
        productRequest.setCategory("Test");
    }

    @Test
    void createProduct_Success() {
        // Arrange
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(modelMapper.map(any(), eq(ProductResponse.class)))
            .thenReturn(new ProductResponse());

        // Act
        ProductResponse response = productService.createProduct(productRequest);

        // Assert
        assertThat(response).isNotNull();
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void getProductById_Success() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(modelMapper.map(product, ProductResponse.class))
            .thenReturn(new ProductResponse());

        // Act
        ProductResponse response = productService.getProductById(1L);

        // Assert
        assertThat(response).isNotNull();
        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    void getProductById_NotFound() {
        // Arrange
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.getProductById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Producto no encontrado");
    }

    @Test
    void decreaseStock_Success() {
        // Arrange
        product.setStock(10);

        // Act
        product.decreaseStock(5);

        // Assert
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void decreaseStock_InsufficientStock() {
        // Arrange
        product.setStock(3);

        // Act & Assert
        assertThatThrownBy(() -> product.decreaseStock(5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Stock insuficiente");
    }
}
```

---

## 14. DEPLOYMENT

### 14.1 Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 14.2 docker-compose.yml

```yaml
version: '3.8'

services:
  oracle:
    image: gvenzl/oracle-xe:21-slim
    environment:
      ORACLE_PASSWORD: yourpassword
      APP_USER: novedadeslz
      APP_USER_PASSWORD: yourpassword
    ports:
      - "1521:1521"
    volumes:
      - oracle-data:/opt/oracle/oradata
    healthcheck:
      test: ["CMD", "healthcheck.sh"]
      interval: 30s
      timeout: 10s
      retries: 5

  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:oracle:thin:@oracle:1521:XE
      SPRING_DATASOURCE_USERNAME: novedadeslz
      SPRING_DATASOURCE_PASSWORD: yourpassword
      JWT_SECRET: ${JWT_SECRET}
      OCR_API_KEY: ${OCR_API_KEY}
      CLOUDINARY_CLOUD_NAME: ${CLOUDINARY_CLOUD_NAME}
      CLOUDINARY_API_KEY: ${CLOUDINARY_API_KEY}
      CLOUDINARY_API_SECRET: ${CLOUDINARY_API_SECRET}
    depends_on:
      oracle:
        condition: service_healthy

volumes:
  oracle-data:
```

### 14.3 Deploy a Railway/Render

```bash
# 1. Crear cuenta en Railway.app o Render.com

# 2. Instalar CLI (Railway)
npm install -g @railway/cli

# 3. Login
railway login

# 4. Crear proyecto
railway init

# 5. Agregar variables de entorno
railway variables set JWT_SECRET="tu-secreto-jwt"
railway variables set OCR_API_KEY="tu-api-key"
railway variables set CLOUDINARY_CLOUD_NAME="tu-cloud-name"

# 6. Deploy
railway up
```

---

## 15. INTEGRACIÓN CON FRONTEND REACT

### 15.1 Configuración Axios

```typescript
// src/api/axiosConfig.ts
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor para agregar token JWT
axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Interceptor para manejo de errores
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default axiosInstance;
```

### 15.2 API Services

```typescript
// src/api/authService.ts
import axios from './axiosConfig';

export const authService = {
  async login(email: string, password: string) {
    const response = await axios.post('/auth/login', { email, password });
    const { token, user } = response.data.data;
    localStorage.setItem('token', token);
    return user;
  },

  async register(data: any) {
    const response = await axios.post('/auth/register', data);
    const { token, user } = response.data.data;
    localStorage.setItem('token', token);
    return user;
  },

  async getCurrentUser() {
    const response = await axios.get('/auth/me');
    return response.data.data;
  },

  logout() {
    localStorage.removeItem('token');
  },
};

// src/api/productService.ts
import axios from './axiosConfig';

export const productService = {
  async getAll(params?: any) {
    const response = await axios.get('/products', { params });
    return response.data.data;
  },

  async getById(id: number) {
    const response = await axios.get(`/products/${id}`);
    return response.data.data;
  },

  async create(product: any) {
    const response = await axios.post('/products', product);
    return response.data.data;
  },

  async update(id: number, product: any) {
    const response = await axios.put(`/products/${id}`, product);
    return response.data.data;
  },

  async delete(id: number) {
    await axios.delete(`/products/${id}`);
  },

  async getStats() {
    const response = await axios.get('/products/stats');
    return response.data.data;
  },
};

// src/api/orderService.ts
import axios from './axiosConfig';

export const orderService = {
  async create(order: any) {
    const response = await axios.post('/orders', order);
    return response.data.data;
  },

  async getAll(params?: any) {
    const response = await axios.get('/orders', { params });
    return response.data.data;
  },

  async getById(id: number) {
    const response = await axios.get(`/orders/${id}`);
    return response.data.data;
  },

  async updateStatus(id: number, status: string) {
    const response = await axios.put(`/orders/${id}/status`, { status });
    return response.data.data;
  },

  async delete(id: number) {
    await axios.delete(`/orders/${id}`);
  },
};

// src/api/paymentService.ts
import axios from './axiosConfig';

export const paymentService = {
  async validateYape(orderId: number, imageFile: File, expectedAmount: number) {
    const formData = new FormData();
    formData.append('orderId', orderId.toString());
    formData.append('imageFile', imageFile);
    formData.append('expectedAmount', expectedAmount.toString());

    const response = await axios.post('/payments/validate-yape', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });

    return response.data.data;
  },

  async checkOperation(operationNumber: string) {
    const response = await axios.post('/payments/check-operation', {
      operationNumber,
    });
    return response.data.data;
  },
};
```

### 15.3 Actualizar ProductsContext

```typescript
// Reemplazar Firebase por API REST
import { productService } from '../api/productService';

export const ProductsProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchProducts();
  }, []);

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const data = await productService.getAll({ active: true });
      setProducts(data.content);
    } catch (error) {
      console.error('Error fetching products:', error);
    } finally {
      setLoading(false);
    }
  };

  const addProduct = async (product: Omit<Product, 'id'>) => {
    try {
      const newProduct = await productService.create(product);
      setProducts(prev => [...prev, newProduct]);
      return newProduct;
    } catch (error) {
      console.error('Error adding product:', error);
      throw error;
    }
  };

  const updateProduct = async (id: number, updates: Partial<Product>) => {
    try {
      const updated = await productService.update(id, updates);
      setProducts(prev => prev.map(p => p.id === id ? updated : p));
    } catch (error) {
      console.error('Error updating product:', error);
      throw error;
    }
  };

  const deleteProduct = async (id: number) => {
    try {
      await productService.delete(id);
      setProducts(prev => prev.filter(p => p.id !== id));
    } catch (error) {
      console.error('Error deleting product:', error);
      throw error;
    }
  };

  return (
    <ProductsContext.Provider value={{
      products,
      loading,
      addProduct,
      updateProduct,
      deleteProduct,
      refreshProducts: fetchProducts,
    }}>
      {children}
    </ProductsContext.Provider>
  );
};
```

---

## 16. CHECKLIST DE IMPLEMENTACIÓN

### Fase 1: Setup Inicial
- [ ] Instalar Oracle Database 21c XE
- [ ] Crear usuario y esquema en Oracle
- [ ] Crear proyecto Spring Boot con Spring Initializr
- [ ] Configurar pom.xml con todas las dependencias
- [ ] Configurar application.properties

### Fase 2: Modelo y Base de Datos
- [ ] Crear entidades JPA (User, Product, Order, OrderItem)
- [ ] Ejecutar scripts SQL de creación de tablas
- [ ] Verificar conexión a Oracle
- [ ] Insertar datos de prueba

### Fase 3: Repositorios
- [ ] Crear interfaces Repository para cada entidad
- [ ] Agregar métodos de consulta personalizados

### Fase 4: DTOs
- [ ] Crear DTOs de Request
- [ ] Crear DTOs de Response
- [ ] Configurar ModelMapper

### Fase 5: Servicios
- [ ] Implementar UserService
- [ ] Implementar ProductService
- [ ] Implementar OrderService
- [ ] Implementar PaymentValidationService
- [ ] Implementar OCRService
- [ ] Implementar CloudinaryService

### Fase 6: Seguridad
- [ ] Configurar Spring Security
- [ ] Implementar JwtTokenProvider
- [ ] Implementar JwtAuthenticationFilter
- [ ] Implementar UserDetailsService

### Fase 7: Controladores
- [ ] Crear AuthController
- [ ] Crear ProductController
- [ ] Crear OrderController
- [ ] Crear PaymentController

### Fase 8: Manejo de Errores
- [ ] Crear excepciones personalizadas
- [ ] Implementar GlobalExceptionHandler

### Fase 9: Configuración Adicional
- [ ] Configurar CORS
- [ ] Configurar Swagger/OpenAPI
- [ ] Configurar upload de archivos

### Fase 10: Testing
- [ ] Escribir unit tests para servicios
- [ ] Escribir integration tests para controladores
- [ ] Testing con Postman/Insomnia

### Fase 11: Integración Frontend
- [ ] Configurar Axios en React
- [ ] Crear servicios API
- [ ] Actualizar contextos para usar API REST
- [ ] Probar integración completa

### Fase 12: Deployment
- [ ] Crear Dockerfile
- [ ] Configurar variables de entorno
- [ ] Deploy a Railway/Render/AWS
- [ ] Configurar dominio y SSL

---

## 17. RECURSOS ADICIONALES

### Documentación Oficial
- Spring Boot: https://spring.io/projects/spring-boot
- Spring Security: https://spring.io/projects/spring-security
- Oracle Database: https://www.oracle.com/database/technologies/appdev/xe.html
- JWT: https://jwt.io/

### Herramientas Recomendadas
- **IDE:** IntelliJ IDEA / Eclipse / VS Code
- **API Testing:** Postman / Insomnia
- **Database Client:** DBeaver / SQL Developer
- **Git:** GitHub / GitLab
- **CI/CD:** GitHub Actions / Jenkins

### Comandos Útiles

```bash
# Maven
mvn clean install          # Build completo
mvn spring-boot:run       # Ejecutar aplicación
mvn test                  # Ejecutar tests

# Oracle
sqlplus novedadeslz/yourpassword@//localhost:1521/XE

# Docker
docker-compose up -d      # Iniciar servicios
docker-compose logs -f    # Ver logs
docker-compose down       # Detener servicios
```

---

## CONCLUSIÓN

Este documento contiene toda la información necesaria para implementar el backend de **Novedades LZ** con Spring Boot y Oracle Database.

El sistema resultante será:
- ✅ Escalable y robusto
- ✅ Seguro con autenticación JWT
- ✅ Transaccional con Oracle
- ✅ RESTful y bien documentado
- ✅ Listo para producción

**Próximos Pasos Recomendados:**
1. Clonar este documento al nuevo proyecto
2. Seguir el checklist de implementación en orden
3. Probar cada módulo antes de continuar
4. Integrar con el frontend React existente

¡Éxito con la implementación! 🚀
