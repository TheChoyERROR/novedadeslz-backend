# 🔗 Guía de Integración Frontend - Backend

## Novedades LZ E-commerce
**Backend:** Spring Boot 4.0.0 + Oracle + JWT
**Frontend:** React/Next.js + TypeScript

---

## ⚠️ IMPORTANTE: Cloudinary Integration

**El backend ahora usa Cloudinary para gestión de imágenes:**
- Los endpoints de productos (`POST /api/products` y `PUT /api/products/{id}`) ahora reciben `multipart/form-data` en lugar de JSON
- Las imágenes se suben como archivos binarios (File), no como URLs
- Validación automática: solo imágenes, máximo 5MB
- Para más detalles, consulta [CLOUDINARY_INTEGRATION.md](CLOUDINARY_INTEGRATION.md)

## 📋 TABLA DE CONTENIDOS

1. [Información del Backend](#1-información-del-backend)
2. [Endpoints Disponibles](#2-endpoints-disponibles)
3. [Autenticación JWT](#3-autenticación-jwt)
4. [Configuración del Frontend](#4-configuración-del-frontend)
5. [Axios Configuration](#5-axios-configuration)
6. [Ejemplos de Uso](#6-ejemplos-de-uso)
7. [Manejo de Errores](#7-manejo-de-errores)
8. [TypeScript Types](#8-typescript-types)

---

## 1. INFORMACIÓN DEL BACKEND

### 🌐 URL Base del API
```
http://localhost:8080
```

### 📡 Endpoints Base
```
/api/auth      - Autenticación (Login, Register)
/api/products  - Productos (CRUD)
/api/orders    - Pedidos (CRUD)
```

### 🔒 CORS Configurado
El backend acepta requests desde:
```
http://localhost:5173  (Vite)
http://localhost:3000  (Next.js)
```

**⚠️ IMPORTANTE:** Si usas otro puerto, actualiza `application.properties`:
```properties
cors.allowed-origins=http://localhost:TU_PUERTO
```

### 📦 Formato de Respuesta
Todas las respuestas siguen este formato:

```typescript
{
  "success": boolean,
  "message": string,
  "data": T | null
}
```

---

## 2. ENDPOINTS DISPONIBLES

### 🔐 Autenticación (Público)

#### POST `/api/auth/register`
Registrar nuevo usuario (ADMIN por defecto)

**Request:**
```json
{
  "email": "admin@novedadeslz.com",
  "password": "Admin123!",
  "fullName": "Administrador",
  "phone": "+51987654321"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Usuario registrado exitosamente",
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "type": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "email": "admin@novedadeslz.com",
      "fullName": "Administrador",
      "phone": "+51987654321",
      "role": "ADMIN"
    }
  }
}
```

#### POST `/api/auth/login`
Iniciar sesión

**Request:**
```json
{
  "email": "admin@novedadeslz.com",
  "password": "Admin123!"
}
```

**Response:** Igual que register

---

### 🛍️ Productos

#### GET `/api/products` (Público)
Obtener lista de productos con paginación

**Query Parameters:**
```
?page=0              - Número de página (default: 0)
&size=20             - Items por página (default: 20)
&sortBy=createdAt    - Campo para ordenar (default: createdAt)
&direction=DESC      - ASC o DESC (default: DESC)
&category=           - Filtrar por categoría (opcional)
&search=             - Buscar en nombre/descripción (opcional)
&active=true         - Solo productos activos (default: true)
```

**Ejemplo:**
```
GET /api/products?page=0&size=10&category=Electrónica&active=true
```

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Laptop HP",
        "description": "Laptop gaming",
        "price": 2500.00,
        "imageUrl": "https://...",
        "category": "Electrónica",
        "stock": 10,
        "active": true,
        "lowStock": false,
        "createdAt": "2025-12-18T10:00:00",
        "updatedAt": "2025-12-18T10:00:00"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 10
    },
    "totalElements": 25,
    "totalPages": 3,
    "last": false
  }
}
```

#### GET `/api/products/{id}` (Público)
Obtener un producto por ID

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Laptop HP",
    "description": "Laptop gaming",
    "price": 2500.00,
    "imageUrl": "https://...",
    "category": "Electrónica",
    "stock": 10,
    "active": true,
    "lowStock": false,
    "createdAt": "2025-12-18T10:00:00",
    "updatedAt": "2025-12-18T10:00:00"
  }
}
```

#### POST `/api/products` (Requiere ADMIN + JWT)
Crear nuevo producto con imagen

**⚠️ IMPORTANTE:** Este endpoint ahora usa `multipart/form-data` para subir imágenes binarias a Cloudinary.

**Headers:**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**Request (multipart/form-data):**
```
Part 1 - "product" (application/json):
{
  "name": "Mouse Gamer",
  "description": "Mouse RGB 16000 DPI",
  "price": 150.00,
  "category": "Accesorios",
  "stock": 50
}

Part 2 - "image" (file, OBLIGATORIO):
[Archivo binario de imagen - JPG/PNG/GIF/WEBP, máx 5MB]
```

**Response:**
```json
{
  "success": true,
  "message": "Producto creado exitosamente",
  "data": { /* ProductResponse */ }
}
```

#### PUT `/api/products/{id}` (Requiere ADMIN + JWT)
Actualizar producto existente con imagen opcional

**⚠️ IMPORTANTE:** Este endpoint ahora usa `multipart/form-data`. La imagen es OPCIONAL al actualizar.

**Headers:**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**Request (multipart/form-data):**
```
Part 1 - "product" (application/json, OBLIGATORIO):
{
  "name": "Mouse Gamer Pro",
  "description": "Mouse actualizado",
  "price": 180.00,
  "category": "Accesorios",
  "stock": 30
}

Part 2 - "image" (file, OPCIONAL):
[Archivo binario de imagen - Si se envía, reemplaza la imagen anterior]
```

#### DELETE `/api/products/{id}` (Requiere ADMIN + JWT)
Eliminar producto (soft delete)

**Headers:**
```
Authorization: Bearer {token}
```

**Response:**
```json
{
  "success": true,
  "message": "Producto eliminado exitosamente",
  "data": null
}
```

---

### 📦 Pedidos

#### POST `/api/orders` (Público)
Crear nuevo pedido

**Request:**
```json
{
  "customerName": "Juan Pérez",
  "customerPhone": "+51987654321",
  "customerEmail": "juan@example.com",
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
```

**Response:**
```json
{
  "success": true,
  "message": "Pedido creado exitosamente",
  "data": {
    "id": 1,
    "orderNumber": "ORD-20251218-0001",
    "customerName": "Juan Pérez",
    "customerPhone": "+51987654321",
    "customerEmail": "juan@example.com",
    "customerAddress": "Av. Principal 123",
    "customerCity": "Lima",
    "total": 5150.00,
    "status": "PENDING",
    "paymentMethod": "yape",
    "paymentProof": null,
    "operationNumber": null,
    "whatsappSent": false,
    "notes": null,
    "items": [
      {
        "id": 1,
        "productId": 1,
        "productName": "Laptop HP",
        "quantity": 2,
        "unitPrice": 2500.00,
        "subtotal": 5000.00
      },
      {
        "id": 2,
        "productId": 3,
        "productName": "Mouse Gamer",
        "quantity": 1,
        "unitPrice": 150.00,
        "subtotal": 150.00
      }
    ],
    "createdAt": "2025-12-18T10:30:00",
    "updatedAt": "2025-12-18T10:30:00"
  }
}
```

#### GET `/api/orders/{id}` (Público)
Consultar estado de un pedido

**Response:** Igual que POST

#### GET `/api/orders` (Requiere ADMIN + JWT)
Listar todos los pedidos (con filtros)

**Headers:**
```
Authorization: Bearer {token}
```

**Query Parameters:**
```
?page=0
&size=20
&sortBy=createdAt
&direction=DESC
&status=PENDING          - Filtrar por estado (opcional)
&customerPhone=          - Filtrar por teléfono (opcional)
&startDate=              - Fecha inicio (ISO 8601) (opcional)
&endDate=                - Fecha fin (ISO 8601) (opcional)
```

#### PUT `/api/orders/{id}/status` (Requiere ADMIN + JWT)
Actualizar estado de un pedido

**Headers:**
```
Authorization: Bearer {token}
```

**Request:**
```json
{
  "status": "CONFIRMED"
}
```

**Valores válidos para status:**
- `PENDING` - Pendiente de confirmación
- `CONFIRMED` - Confirmado (descuenta stock)
- `DELIVERED` - Entregado
- `CANCELLED` - Cancelado (devuelve stock si estaba confirmado)

#### DELETE `/api/orders/{id}` (Requiere ADMIN + JWT)
Eliminar pedido

---

## 3. AUTENTICACIÓN JWT

### 🔑 Cómo Funciona

1. **Login/Register** → Backend retorna JWT token
2. **Guardar token** → LocalStorage/SessionStorage/Cookie
3. **Cada request** → Incluir token en header `Authorization`
4. **Token expira** → 24 horas (86400 segundos)

### 📝 Headers Requeridos

```typescript
headers: {
  'Authorization': `Bearer ${token}`,
  'Content-Type': 'application/json'
}
```

### 🔐 Roles y Permisos

**ADMIN:**
- Todos los endpoints
- Crear/editar/eliminar productos
- Ver todos los pedidos
- Cambiar estado de pedidos

**Endpoints Públicos (sin autenticación):**
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/orders` (crear pedido)
- `GET /api/orders/{id}` (consultar pedido específico)

---

## 4. CONFIGURACIÓN DEL FRONTEND

### React + Vite

#### Instalar Dependencias
```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install

# Dependencias necesarias
npm install axios
npm install @tanstack/react-query
npm install zustand
npm install react-router-dom
npm install react-hook-form zod @hookform/resolvers
```

#### Estructura de Carpetas
```
src/
├── api/
│   ├── axios.config.ts       # Configuración Axios
│   ├── auth.api.ts           # API de autenticación
│   ├── products.api.ts       # API de productos
│   └── orders.api.ts         # API de pedidos
├── components/
│   ├── auth/
│   ├── products/
│   └── orders/
├── hooks/
│   ├── useAuth.ts
│   ├── useProducts.ts
│   └── useOrders.ts
├── store/
│   └── authStore.ts          # Zustand store
├── types/
│   └── api.types.ts          # TypeScript types
└── utils/
    └── constants.ts
```

### Next.js

```bash
npx create-next-app@latest frontend
cd frontend

# Dependencias necesarias
npm install axios
npm install @tanstack/react-query
npm install zustand
npm install react-hook-form zod @hookform/resolvers
```

---

## 5. AXIOS CONFIGURATION

### `src/api/axios.config.ts`

```typescript
import axios from 'axios';

// URL base del API
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

// Crear instancia de Axios
export const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor para agregar token a todas las requests
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Interceptor para manejar respuestas
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // Si el token expiró (401), redirect a login
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

### Variables de Entorno

**`.env`** (Vite)
```env
VITE_API_URL=http://localhost:8080
```

**`.env.local`** (Next.js)
```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

---

## 6. EJEMPLOS DE USO

### Auth API (`src/api/auth.api.ts`)

```typescript
import api from './axios.config';
import type { ApiResponse, AuthResponse, LoginRequest, RegisterRequest } from '@/types/api.types';

export const authApi = {
  // Login
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<ApiResponse<AuthResponse>>(
      '/api/auth/login',
      data
    );
    return response.data.data!;
  },

  // Register
  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await api.post<ApiResponse<AuthResponse>>(
      '/api/auth/register',
      data
    );
    return response.data.data!;
  },
};
```

### Products API (`src/api/products.api.ts`)

```typescript
import api from './axios.config';
import type {
  ApiResponse,
  ProductResponse,
  ProductRequest,
  PaginatedResponse
} from '@/types/api.types';

export const productsApi = {
  // Get all products
  getAll: async (params?: {
    page?: number;
    size?: number;
    category?: string;
    search?: string;
    active?: boolean;
  }): Promise<PaginatedResponse<ProductResponse>> => {
    const response = await api.get<ApiResponse<PaginatedResponse<ProductResponse>>>(
      '/api/products',
      { params }
    );
    return response.data.data!;
  },

  // Get product by ID
  getById: async (id: number): Promise<ProductResponse> => {
    const response = await api.get<ApiResponse<ProductResponse>>(
      `/api/products/${id}`
    );
    return response.data.data!;
  },

  // Create product with image (ADMIN only)
  create: async (data: ProductRequest, imageFile: File): Promise<ProductResponse> => {
    const formData = new FormData();

    // Add product data as JSON blob
    const productBlob = new Blob([JSON.stringify(data)], {
      type: 'application/json'
    });
    formData.append('product', productBlob);

    // Add image file
    formData.append('image', imageFile);

    const response = await api.post<ApiResponse<ProductResponse>>(
      '/api/products',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    );
    return response.data.data!;
  },

  // Update product with optional image (ADMIN only)
  update: async (id: number, data: ProductRequest, imageFile?: File): Promise<ProductResponse> => {
    const formData = new FormData();

    // Add product data as JSON blob
    const productBlob = new Blob([JSON.stringify(data)], {
      type: 'application/json'
    });
    formData.append('product', productBlob);

    // Add image file if provided
    if (imageFile) {
      formData.append('image', imageFile);
    }

    const response = await api.put<ApiResponse<ProductResponse>>(
      `/api/products/${id}`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    );
    return response.data.data!;
  },

  // Delete product (ADMIN only)
  delete: async (id: number): Promise<void> => {
    await api.delete(`/api/products/${id}`);
  },
};
```

### Orders API (`src/api/orders.api.ts`)

```typescript
import api from './axios.config';
import type {
  ApiResponse,
  OrderResponse,
  OrderRequest,
  PaginatedResponse
} from '@/types/api.types';

export const ordersApi = {
  // Create order (public)
  create: async (data: OrderRequest): Promise<OrderResponse> => {
    const response = await api.post<ApiResponse<OrderResponse>>(
      '/api/orders',
      data
    );
    return response.data.data!;
  },

  // Get order by ID (public)
  getById: async (id: number): Promise<OrderResponse> => {
    const response = await api.get<ApiResponse<OrderResponse>>(
      `/api/orders/${id}`
    );
    return response.data.data!;
  },

  // Get all orders (ADMIN only)
  getAll: async (params?: {
    page?: number;
    size?: number;
    status?: string;
    customerPhone?: string;
  }): Promise<PaginatedResponse<OrderResponse>> => {
    const response = await api.get<ApiResponse<PaginatedResponse<OrderResponse>>>(
      '/api/orders',
      { params }
    );
    return response.data.data!;
  },

  // Update order status (ADMIN only)
  updateStatus: async (id: number, status: string): Promise<OrderResponse> => {
    const response = await api.put<ApiResponse<OrderResponse>>(
      `/api/orders/${id}/status`,
      { status }
    );
    return response.data.data!;
  },

  // Delete order (ADMIN only)
  delete: async (id: number): Promise<void> => {
    await api.delete(`/api/orders/${id}`);
  },
};
```

### Zustand Store (`src/store/authStore.ts`)

```typescript
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '@/types/api.types';

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,
      login: (token, user) => {
        localStorage.setItem('token', token);
        set({ token, user, isAuthenticated: true });
      },
      logout: () => {
        localStorage.removeItem('token');
        set({ token: null, user: null, isAuthenticated: false });
      },
    }),
    {
      name: 'auth-storage',
    }
  )
);
```

### React Component Example

```typescript
import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { authApi } from '@/api/auth.api';
import { productsApi } from '@/api/products.api';
import { useAuthStore } from '@/store/authStore';

// Login Component
export function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const login = useAuthStore((state) => state.login);

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      login(data.token, data.user);
      // Redirect to dashboard
    },
    onError: (error) => {
      console.error('Login failed:', error);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    loginMutation.mutate({ email, password });
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="Email"
      />
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="Password"
      />
      <button type="submit" disabled={loginMutation.isPending}>
        {loginMutation.isPending ? 'Loading...' : 'Login'}
      </button>
    </form>
  );
}

// Products List Component
export function ProductsList() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['products'],
    queryFn: () => productsApi.getAll({ page: 0, size: 10 }),
  });

  if (isLoading) return <div>Loading...</div>;
  if (error) return <div>Error loading products</div>;

  return (
    <div>
      {data?.content.map((product) => (
        <div key={product.id}>
          <h3>{product.name}</h3>
          <p>{product.description}</p>
          <p>S/. {product.price}</p>
          <p>Stock: {product.stock}</p>
        </div>
      ))}
    </div>
  );
}
```

---

## 7. MANEJO DE ERRORES

### Errores Comunes

```typescript
// 400 - Bad Request (validación fallida)
{
  "success": false,
  "message": "Errores de validación",
  "data": {
    "email": "Email inválido",
    "password": "La contraseña debe tener al menos 6 caracteres"
  }
}

// 401 - Unauthorized (sin token o token inválido)
{
  "success": false,
  "message": "Credenciales inválidas",
  "data": null
}

// 403 - Forbidden (sin permisos)
{
  "success": false,
  "message": "No tienes permisos para acceder a este recurso",
  "data": null
}

// 404 - Not Found
{
  "success": false,
  "message": "Producto no encontrado con ID: 999",
  "data": null
}

// 409 - Conflict
{
  "success": false,
  "message": "El email ya está registrado",
  "data": null
}

// 500 - Internal Server Error
{
  "success": false,
  "message": "Error interno del servidor",
  "data": null
}
```

### Error Handler

```typescript
import axios from 'axios';

export function handleApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    // Error de respuesta del servidor
    if (error.response?.data) {
      const data = error.response.data;

      // Errores de validación
      if (data.data && typeof data.data === 'object') {
        return Object.values(data.data).join(', ');
      }

      // Mensaje de error general
      return data.message || 'Error desconocido';
    }

    // Error de red
    if (error.request) {
      return 'Error de conexión. Verifica tu internet.';
    }
  }

  return 'Error inesperado';
}
```

---

## 8. TYPESCRIPT TYPES

### `src/types/api.types.ts`

```typescript
// ============================================
// GENERIC API RESPONSE
// ============================================

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T | null;
}

export interface PaginatedResponse<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
  };
  totalElements: number;
  totalPages: number;
  last: boolean;
}

// ============================================
// AUTH
// ============================================

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  role: 'ADMIN' | 'USER';
}

export interface AuthResponse {
  token: string;
  type: string;
  expiresIn: number;
  user: User;
}

// ============================================
// PRODUCTS
// ============================================

export interface ProductRequest {
  name: string;
  description?: string;
  price: number;
  category: string;
  stock: number;
  // Nota: La imagen se envía por separado como File en FormData
}

export interface ProductResponse {
  id: number;
  name: string;
  description?: string;
  price: number;
  imageUrl?: string;
  category: string;
  stock: number;
  active: boolean;
  lowStock: boolean;
  createdAt: string;
  updatedAt: string;
}

// ============================================
// ORDERS
// ============================================

export interface OrderItemRequest {
  productId: number;
  quantity: number;
}

export interface OrderRequest {
  customerName: string;
  customerPhone: string;
  customerEmail?: string;
  customerAddress?: string;
  customerCity?: string;
  paymentMethod?: string;
  items: OrderItemRequest[];
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'DELIVERED' | 'CANCELLED';

export interface OrderResponse {
  id: number;
  orderNumber: string;
  customerName: string;
  customerPhone: string;
  customerEmail?: string;
  customerAddress?: string;
  customerCity?: string;
  total: number;
  status: OrderStatus;
  paymentMethod?: string;
  paymentProof?: string;
  operationNumber?: string;
  whatsappSent: boolean;
  notes?: string;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}
```

---

## 9. CHECKLIST DE INTEGRACIÓN

### ✅ Backend (Ya está listo)

- [x] CORS configurado para frontend
- [x] JWT funcionando
- [x] Endpoints documentados en Swagger
- [x] Manejo de errores global
- [x] Validaciones en DTOs

### ✅ Frontend (Por hacer)

- [ ] Instalar dependencias
- [ ] Configurar Axios con interceptors
- [ ] Crear tipos TypeScript
- [ ] Implementar auth store (Zustand)
- [ ] Crear APIs (auth, products, orders)
- [ ] Implementar login/register
- [ ] Proteger rutas privadas
- [ ] Implementar lista de productos
- [ ] Implementar carrito de compras
- [ ] Implementar checkout/pedidos
- [ ] Manejo de errores
- [ ] Loading states
- [ ] Mostrar mensajes de éxito/error

---

## 10. COMANDOS ÚTILES

### Backend (Spring Boot)

```bash
# Iniciar backend
./mvnw spring-boot:run

# Compilar
./mvnw clean compile

# Ver Swagger
http://localhost:8080/swagger-ui.html

# Ver API Docs JSON
http://localhost:8080/v3/api-docs
```

### Frontend (Vite)

```bash
# Instalar dependencias
npm install

# Desarrollo
npm run dev

# Build producción
npm run build

# Preview build
npm run preview
```

### Frontend (Next.js)

```bash
# Instalar dependencias
npm install

# Desarrollo
npm run dev

# Build producción
npm run build

# Iniciar producción
npm start
```

---

## 11. TESTING CON CURL

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@novedadeslz.com",
    "password": "Admin123!"
  }'
```

### Obtener Productos
```bash
curl http://localhost:8080/api/products?page=0&size=10
```

### Crear Producto con Imagen (con token)
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer TU_TOKEN_AQUI" \
  -F 'product={"name":"Producto Test","description":"Descripción","price":100.00,"category":"Test","stock":10};type=application/json' \
  -F 'image=@/ruta/a/tu/imagen.jpg'
```

### Actualizar Producto con Nueva Imagen (con token)
```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Authorization: Bearer TU_TOKEN_AQUI" \
  -F 'product={"name":"Producto Actualizado","description":"Nueva descripción","price":150.00,"category":"Test","stock":5};type=application/json' \
  -F 'image=@/ruta/a/nueva-imagen.jpg'
```

### Actualizar Producto SIN Cambiar Imagen (con token)
```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Authorization: Bearer TU_TOKEN_AQUI" \
  -F 'product={"name":"Producto Actualizado","description":"Nueva descripción","price":150.00,"category":"Test","stock":5};type=application/json'
```

### Crear Pedido
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Juan Pérez",
    "customerPhone": "+51987654321",
    "customerEmail": "juan@test.com",
    "items": [
      {
        "productId": 1,
        "quantity": 2
      }
    ]
  }'
```

---

## 12. TROUBLESHOOTING

### ❌ CORS Error
**Problema:** `Access to XMLHttpRequest has been blocked by CORS policy`

**Solución:**
1. Verifica que el backend esté corriendo
2. Verifica que la URL del frontend esté en `application.properties`:
   ```properties
   cors.allowed-origins=http://localhost:5173
   ```
3. Reinicia el backend

### ❌ 401 Unauthorized
**Problema:** Token inválido o expirado

**Solución:**
1. Verifica que el token se esté enviando en el header
2. Verifica que el token no haya expirado (24 horas)
3. Haz login de nuevo

### ❌ Network Error
**Problema:** No se puede conectar al backend

**Solución:**
1. Verifica que el backend esté corriendo en `http://localhost:8080`
2. Verifica la URL en `.env`
3. Verifica que no haya firewall bloqueando

### ❌ Validation Errors
**Problema:** `400 Bad Request` con errores de validación

**Solución:**
1. Revisa los errores en `response.data.data`
2. Verifica que los campos cumplan las validaciones:
   - Email válido
   - Password mínimo 6 caracteres
   - Teléfono formato peruano: `+51987654321`
   - Stock no negativo
   - Precio mayor a 0

---

## 📞 SOPORTE

Si encuentras algún problema:

1. Revisa los logs del backend
2. Revisa la consola del navegador (Network tab)
3. Verifica Swagger: `http://localhost:8080/swagger-ui.html`
4. Prueba con curl antes de probar con el frontend

---

**✅ Backend Listo:** Spring Boot + JWT + Oracle
**🚀 Frontend:** Pendiente de implementación
**📖 Documentación:** Swagger UI + Esta guía

¡Listo para integrar! 🎉
