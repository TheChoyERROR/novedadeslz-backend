# 🔄 BREAKING CHANGES: Cloudinary Integration

**Fecha:** 19 de Diciembre, 2024
**Versión:** Backend v0.0.1-SNAPSHOT
**Autor:** Backend Team

---

## ⚠️ CAMBIOS CRÍTICOS EN LA API

El backend ahora usa **Cloudinary** para gestión de imágenes. Esto implica **BREAKING CHANGES** en los endpoints de productos.

---

## 📝 RESUMEN EJECUTIVO

### ❌ ANTES (Ya NO funciona)
```typescript
// ❌ OBSOLETO - Ya no aceptamos JSON con imageUrl
const response = await axios.post('/api/products', {
  name: "Producto",
  price: 100,
  imageUrl: "https://example.com/image.jpg",  // ❌ Ya no se acepta
  category: "Test",
  stock: 10
}, {
  headers: { 'Content-Type': 'application/json' }
});
```

### ✅ AHORA (Forma correcta)
```typescript
// ✅ CORRECTO - Enviar FormData con archivo binario
const formData = new FormData();

// 1. Agregar datos del producto como JSON Blob
const productBlob = new Blob([JSON.stringify({
  name: "Producto",
  price: 100,
  category: "Test",
  stock: 10
})], { type: 'application/json' });

formData.append('product', productBlob);

// 2. Agregar archivo de imagen
formData.append('image', imageFile);  // File from <input type="file">

// 3. Enviar con FormData
const response = await axios.post('/api/products', formData, {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'multipart/form-data'
  }
});
```

---

## 🔴 ENDPOINTS AFECTADOS

### 1️⃣ `POST /api/products` (CREAR PRODUCTO)

**CAMBIOS:**
- ❌ Ya NO acepta `application/json`
- ✅ Ahora requiere `multipart/form-data`
- ✅ Imagen es **OBLIGATORIA** (antes era opcional)
- ❌ Campo `imageUrl` eliminado del DTO
- ✅ Nueva parte `image` como archivo binario

**ANTES:**
```http
POST /api/products
Content-Type: application/json
Authorization: Bearer {token}

{
  "name": "Laptop",
  "description": "Laptop gaming",
  "price": 1500.00,
  "imageUrl": "https://...",
  "category": "Electrónica",
  "stock": 10
}
```

**AHORA:**
```http
POST /api/products
Content-Type: multipart/form-data
Authorization: Bearer {token}

--boundary
Content-Disposition: form-data; name="product"
Content-Type: application/json

{
  "name": "Laptop",
  "description": "Laptop gaming",
  "price": 1500.00,
  "category": "Electrónica",
  "stock": 10
}

--boundary
Content-Disposition: form-data; name="image"; filename="laptop.jpg"
Content-Type: image/jpeg

[BINARY IMAGE DATA]
--boundary--
```

---

### 2️⃣ `PUT /api/products/{id}` (ACTUALIZAR PRODUCTO)

**CAMBIOS:**
- ❌ Ya NO acepta `application/json`
- ✅ Ahora requiere `multipart/form-data`
- ✅ Imagen es **OPCIONAL** (solo si quieres cambiarla)
- ❌ Campo `imageUrl` eliminado del DTO

**COMPORTAMIENTO:**
- Si envías `image`: Se actualiza la imagen (se elimina la anterior de Cloudinary)
- Si NO envías `image`: La imagen actual NO se modifica

**ANTES:**
```http
PUT /api/products/1
Content-Type: application/json
Authorization: Bearer {token}

{
  "name": "Laptop Pro",
  "description": "Updated",
  "price": 1800.00,
  "imageUrl": "https://...",
  "category": "Electrónica",
  "stock": 5
}
```

**AHORA (con nueva imagen):**
```http
PUT /api/products/1
Content-Type: multipart/form-data
Authorization: Bearer {token}

Part 1: product (JSON)
Part 2: image (File, opcional)
```

**AHORA (sin cambiar imagen):**
```http
PUT /api/products/1
Content-Type: multipart/form-data
Authorization: Bearer {token}

Part 1: product (JSON)
Part 2: image (omitido)
```

---

## 💻 CÓDIGO DE EJEMPLO PARA FRONTEND

### React/Next.js Component

```tsx
import { useState } from 'react';
import axios from 'axios';

interface ProductFormData {
  name: string;
  description: string;
  price: number;
  category: string;
  stock: number;
}

const ProductForm = () => {
  const [formData, setFormData] = useState<ProductFormData>({
    name: '',
    description: '',
    price: 0,
    category: '',
    stock: 0
  });
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];

      // Validaciones del lado del cliente
      if (!file.type.startsWith('image/')) {
        alert('Por favor selecciona una imagen válida');
        return;
      }

      if (file.size > 5 * 1024 * 1024) {
        alert('La imagen no debe superar 5MB');
        return;
      }

      setImageFile(file);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!imageFile) {
      alert('La imagen es obligatoria');
      return;
    }

    setLoading(true);

    try {
      // Crear FormData
      const formDataToSend = new FormData();

      // Agregar producto como JSON Blob
      const productBlob = new Blob([JSON.stringify(formData)], {
        type: 'application/json'
      });
      formDataToSend.append('product', productBlob);

      // Agregar imagen
      formDataToSend.append('image', imageFile);

      // Enviar
      const token = localStorage.getItem('token');
      const response = await axios.post(
        'http://localhost:8080/api/products',
        formDataToSend,
        {
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'multipart/form-data'
          }
        }
      );

      console.log('Producto creado:', response.data);
      alert('Producto creado exitosamente!');

      // Reset form
      setFormData({ name: '', description: '', price: 0, category: '', stock: 0 });
      setImageFile(null);

    } catch (error: any) {
      console.error('Error:', error);
      alert(error.response?.data?.message || 'Error al crear producto');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="text"
        placeholder="Nombre"
        value={formData.name}
        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
        required
      />

      <textarea
        placeholder="Descripción"
        value={formData.description}
        onChange={(e) => setFormData({ ...formData, description: e.target.value })}
      />

      <input
        type="number"
        placeholder="Precio"
        value={formData.price}
        onChange={(e) => setFormData({ ...formData, price: parseFloat(e.target.value) })}
        step="0.01"
        required
      />

      <input
        type="text"
        placeholder="Categoría"
        value={formData.category}
        onChange={(e) => setFormData({ ...formData, category: e.target.value })}
        required
      />

      <input
        type="number"
        placeholder="Stock"
        value={formData.stock}
        onChange={(e) => setFormData({ ...formData, stock: parseInt(e.target.value) })}
        required
      />

      {/* INPUT DE IMAGEN - OBLIGATORIO */}
      <input
        type="file"
        accept="image/*"
        onChange={handleImageChange}
        required
      />

      {imageFile && (
        <p>Archivo seleccionado: {imageFile.name} ({(imageFile.size / 1024).toFixed(2)} KB)</p>
      )}

      <button type="submit" disabled={loading}>
        {loading ? 'Creando...' : 'Crear Producto'}
      </button>
    </form>
  );
};

export default ProductForm;
```

---

### Service Layer (Recomendado)

```typescript
// src/services/products.service.ts
import axios from 'axios';

const API_URL = 'http://localhost:8080/api/products';

export interface ProductRequest {
  name: string;
  description?: string;
  price: number;
  category: string;
  stock: number;
  // Nota: imageUrl ya NO existe
}

export interface ProductResponse {
  id: number;
  name: string;
  description: string;
  price: number;
  imageUrl: string;  // URL de Cloudinary (solo en response)
  category: string;
  stock: number;
  active: boolean;
  lowStock: boolean;
  createdAt: string;
  updatedAt: string;
}

class ProductsService {
  /**
   * Crear producto con imagen
   */
  async createProduct(
    productData: ProductRequest,
    imageFile: File,
    token: string
  ): Promise<ProductResponse> {
    const formData = new FormData();

    // Agregar producto como JSON Blob
    const productBlob = new Blob([JSON.stringify(productData)], {
      type: 'application/json'
    });
    formData.append('product', productBlob);

    // Agregar imagen
    formData.append('image', imageFile);

    const response = await axios.post(API_URL, formData, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'multipart/form-data'
      }
    });

    return response.data.data;
  }

  /**
   * Actualizar producto con imagen opcional
   */
  async updateProduct(
    id: number,
    productData: ProductRequest,
    imageFile: File | null,
    token: string
  ): Promise<ProductResponse> {
    const formData = new FormData();

    // Agregar producto como JSON Blob
    const productBlob = new Blob([JSON.stringify(productData)], {
      type: 'application/json'
    });
    formData.append('product', productBlob);

    // Agregar imagen solo si existe
    if (imageFile) {
      formData.append('image', imageFile);
    }

    const response = await axios.put(`${API_URL}/${id}`, formData, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'multipart/form-data'
      }
    });

    return response.data.data;
  }
}

export default new ProductsService();
```

---

## 🎨 VALIDACIONES DEL LADO DEL CLIENTE

**IMPORTANTE:** Implementa estas validaciones para mejorar UX:

```typescript
const validateImage = (file: File): { valid: boolean; error?: string } => {
  // 1. Validar tipo
  if (!file.type.startsWith('image/')) {
    return {
      valid: false,
      error: 'El archivo debe ser una imagen (JPG, PNG, GIF, WEBP)'
    };
  }

  // 2. Validar tamaño (5MB)
  const maxSize = 5 * 1024 * 1024;
  if (file.size > maxSize) {
    return {
      valid: false,
      error: 'La imagen no debe superar 5MB'
    };
  }

  // 3. Validar dimensiones (opcional pero recomendado)
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      if (img.width < 200 || img.height < 200) {
        resolve({
          valid: false,
          error: 'La imagen debe tener al menos 200x200 píxeles'
        });
      } else {
        resolve({ valid: true });
      }
    };
    img.src = URL.createObjectURL(file);
  });
};
```

---

## 📊 RESPUESTA DEL BACKEND

La respuesta **NO cambia**. Sigue siendo el mismo formato:

```json
{
  "success": true,
  "message": "Producto creado exitosamente",
  "data": {
    "id": 1,
    "name": "Laptop",
    "description": "Laptop gaming",
    "price": 1500.00,
    "imageUrl": "https://res.cloudinary.com/dyvsnuert/image/upload/v1734567890/novedadeslz/products/abc-123.jpg",
    "category": "Electrónica",
    "stock": 10,
    "active": true,
    "lowStock": false,
    "createdAt": "2024-12-19T00:00:00",
    "updatedAt": "2024-12-19T00:00:00"
  }
}
```

**NOTA:** El `imageUrl` ahora es una URL de Cloudinary, no la URL que el usuario ingresó.

---

## ⚡ VENTAJAS PARA EL FRONTEND

1. **No necesitas hosting de imágenes** - Cloudinary lo maneja
2. **CDN automático** - Las imágenes se cargan rápido desde cualquier ubicación
3. **Optimización automática** - Cloudinary optimiza calidad y formato
4. **Transformaciones on-the-fly** - Puedes generar thumbnails sin guardar múltiples versiones

### Ejemplo de Transformaciones:

```typescript
// URL original de Cloudinary
const imageUrl = "https://res.cloudinary.com/dyvsnuert/image/upload/v1234567890/novedadeslz/products/abc.jpg";

// Generar thumbnail 300x300 (sin llamar al backend)
const thumbnailUrl = imageUrl.replace('/upload/', '/upload/w_300,h_300,c_fill,q_auto,f_auto/');

// Usar en el componente
<img src={thumbnailUrl} alt="Thumbnail" />

// Imagen grande para detalle
const largeUrl = imageUrl.replace('/upload/', '/upload/w_1200,h_800,c_fill,q_auto,f_auto/');

<img src={largeUrl} alt="Large" />
```

---

## 🐛 MANEJO DE ERRORES

### Errores Comunes:

**1. Imagen no proporcionada en CREATE:**
```json
{
  "success": false,
  "message": "El archivo no puede estar vacío",
  "data": null
}
```

**2. Tipo de archivo inválido:**
```json
{
  "success": false,
  "message": "El archivo debe ser una imagen",
  "data": null
}
```

**3. Tamaño excedido:**
```json
{
  "success": false,
  "message": "La imagen no debe superar 5MB",
  "data": null
}
```

**4. Token inválido:**
```json
{
  "success": false,
  "message": "Token inválido o expirado",
  "data": null
}
```

---

## 📱 TESTING CON POSTMAN

### Crear Producto:

1. Método: **POST**
2. URL: `http://localhost:8080/api/products`
3. Headers:
   - `Authorization: Bearer {token}`
4. Body → **form-data**:
   - Key: `product` | Type: **Text** | Value:
     ```json
     {"name":"Test","description":"Test","price":100,"category":"Test","stock":10}
     ```
     **⚠️ IMPORTANTE:** En Postman, selecciona `Content-Type: application/json` en el dropdown
   - Key: `image` | Type: **File** | Value: Seleccionar imagen

---

## 🔗 DOCUMENTACIÓN ADICIONAL

- **Guía completa:** Ver [CLOUDINARY_INTEGRATION.md](CLOUDINARY_INTEGRATION.md)
- **Guía de integración frontend:** Ver [FRONTEND_INTEGRATION_GUIDE.md](FRONTEND_INTEGRATION_GUIDE.md)
- **Swagger UI:** http://localhost:8080/swagger-ui.html

---

## ✅ CHECKLIST DE MIGRACIÓN

Frontend debe actualizar:

- [ ] Componentes de creación de productos
- [ ] Componentes de actualización de productos
- [ ] Servicios/API layer
- [ ] Validaciones de imágenes
- [ ] TypeScript interfaces (eliminar `imageUrl` de `ProductRequest`)
- [ ] Tests unitarios
- [ ] Tests de integración
- [ ] Documentación interna

---

## 🆘 SOPORTE

Si tienes dudas o problemas, contacta al equipo de backend con:
- Código de error
- Request completo (sin el token)
- Screenshot del error

---

**Última actualización:** 19 de Diciembre, 2024
**Breaking Changes:** Sí
**Requiere acción inmediata:** Sí
