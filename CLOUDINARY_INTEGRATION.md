# Guía de Integración de Cloudinary

## 🎯 Introducción

Este proyecto ahora utiliza **Cloudinary** para la gestión profesional de imágenes de productos. Las imágenes se suben en formato binario (MultipartFile) y se almacenan en la nube de Cloudinary, obteniendo URLs optimizadas automáticamente.

## 📋 Características Implementadas

- ✅ Subida de imágenes en binario (no URLs)
- ✅ Validación de tipo de archivo (solo imágenes)
- ✅ Validación de tamaño máximo (5MB)
- ✅ Eliminación automática de imágenes al actualizar/eliminar productos
- ✅ Optimización automática de calidad
- ✅ Transformaciones de imagen (redimensionamiento, crop)
- ✅ CDN global para carga rápida
- ✅ URLs permanentes y seguras (HTTPS)

## 🔧 Configuración

### 1. Obtener Credenciales de Cloudinary

1. Ve a [https://console.cloudinary.com/](https://console.cloudinary.com/)
2. Crea una cuenta gratuita (plan gratuito incluye 25GB de almacenamiento)
3. En el Dashboard, encontrarás tus credenciales:
   - **Cloud Name**: `dxxxxx` (ejemplo)
   - **API Key**: `123456789012345` (ejemplo)
   - **API Secret**: `abcdefghijklmnopqrstuvwxyz` (ejemplo)

### 2. Configurar application.properties

Actualiza las credenciales en `/src/main/resources/application.properties`:

```properties
# Cloudinary (para gestión de imágenes de productos)
cloudinary.cloud-name=tu-cloud-name-aqui
cloudinary.api-key=tu-api-key-aqui
cloudinary.api-secret=tu-api-secret-aqui
```

### 3. Tamaño Máximo de Archivos

El tamaño máximo está configurado en **5MB** por imagen:

```properties
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB
spring.servlet.multipart.enabled=true
```

## 📝 Uso de los Endpoints

### Crear Producto con Imagen

**Endpoint:** `POST /api/products`

**Content-Type:** `multipart/form-data`

**Headers:**
```
Authorization: Bearer <tu-jwt-token>
```

**Partes del Request:**

1. **product** (JSON con `Content-Type: application/json`):
```json
{
  "name": "Producto de Prueba",
  "description": "Descripción del producto",
  "price": 99.99,
  "category": "Electrónica",
  "stock": 50
}
```

2. **image** (archivo binario - OBLIGATORIO):
   - Tipos permitidos: image/jpeg, image/png, image/gif, image/webp
   - Tamaño máximo: 5MB

**Ejemplo con CURL:**

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  -F 'product={"name":"Laptop Dell","description":"Laptop de alto rendimiento","price":1299.99,"category":"Electrónica","stock":10};type=application/json' \
  -F 'image=@/ruta/a/tu/imagen.jpg'
```

**Ejemplo con JavaScript/Fetch:**

```javascript
const formData = new FormData();

// Agregar los datos del producto como JSON
const productData = {
  name: "Laptop Dell",
  description: "Laptop de alto rendimiento",
  price: 1299.99,
  category: "Electrónica",
  stock: 10
};

const productBlob = new Blob([JSON.stringify(productData)], {
  type: 'application/json'
});
formData.append('product', productBlob);

// Agregar la imagen (obtenida de un input file)
const imageFile = document.getElementById('imageInput').files[0];
formData.append('image', imageFile);

// Enviar la petición
const response = await fetch('http://localhost:8080/api/products', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`
  },
  body: formData
});

const result = await response.json();
console.log(result);
```

**Ejemplo con Axios:**

```javascript
import axios from 'axios';

const createProduct = async (productData, imageFile, token) => {
  const formData = new FormData();

  // Agregar producto como JSON Blob
  const productBlob = new Blob([JSON.stringify(productData)], {
    type: 'application/json'
  });
  formData.append('product', productBlob);

  // Agregar imagen
  formData.append('image', imageFile);

  try {
    const response = await axios.post(
      'http://localhost:8080/api/products',
      formData,
      {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'multipart/form-data'
        }
      }
    );

    return response.data;
  } catch (error) {
    console.error('Error al crear producto:', error.response?.data);
    throw error;
  }
};

// Uso:
const productData = {
  name: "Laptop Dell",
  description: "Laptop de alto rendimiento",
  price: 1299.99,
  category: "Electrónica",
  stock: 10
};

const imageFile = document.getElementById('imageInput').files[0];
const token = localStorage.getItem('token');

const result = await createProduct(productData, imageFile, token);
console.log('Producto creado:', result);
```

### Actualizar Producto con Imagen

**Endpoint:** `PUT /api/products/{id}`

**Content-Type:** `multipart/form-data`

**Partes del Request:**

1. **product** (JSON - OBLIGATORIO)
2. **image** (archivo binario - OPCIONAL)

Si NO se envía la imagen, el producto se actualiza SIN cambiar la imagen existente.
Si SE envía la imagen, se elimina la imagen anterior de Cloudinary y se sube la nueva.

**Ejemplo con CURL:**

```bash
# Actualizar producto CON nueva imagen
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  -F 'product={"name":"Laptop Dell XPS","description":"Actualizado","price":1499.99,"category":"Electrónica","stock":5};type=application/json' \
  -F 'image=@/ruta/a/nueva-imagen.jpg'

# Actualizar producto SIN cambiar imagen
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  -F 'product={"name":"Laptop Dell XPS","description":"Actualizado","price":1499.99,"category":"Electrónica","stock":5};type=application/json'
```

**Ejemplo con Axios:**

```javascript
const updateProduct = async (productId, productData, imageFile, token) => {
  const formData = new FormData();

  // Agregar producto como JSON Blob
  const productBlob = new Blob([JSON.stringify(productData)], {
    type: 'application/json'
  });
  formData.append('product', productBlob);

  // Agregar imagen solo si se proporciona
  if (imageFile) {
    formData.append('image', imageFile);
  }

  const response = await axios.put(
    `http://localhost:8080/api/products/${productId}`,
    formData,
    {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'multipart/form-data'
      }
    }
  );

  return response.data;
};
```

### Eliminar Producto

**Endpoint:** `DELETE /api/products/{id}`

Al eliminar un producto (soft delete), la imagen se elimina automáticamente de Cloudinary.

```bash
curl -X DELETE http://localhost:8080/api/products/1 \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

## 🖼️ Transformaciones de Imagen

Cloudinary permite transformar imágenes on-the-fly. El `CloudinaryService` incluye el método `getTransformedImageUrl()` para esto:

### Ejemplo en Java

```java
@Autowired
private CloudinaryService cloudinaryService;

// Obtener URL con dimensiones específicas
String originalUrl = product.getImageUrl();
String thumbnailUrl = cloudinaryService.getTransformedImageUrl(originalUrl, 300, 300);
String largeUrl = cloudinaryService.getTransformedImageUrl(originalUrl, 1200, 800);
```

### Ejemplo de URLs Transformadas

**Original:**
```
https://res.cloudinary.com/demo/image/upload/v1234567890/novedadeslz/products/abc123.jpg
```

**Thumbnail (300x300):**
```
https://res.cloudinary.com/demo/image/upload/w_300,h_300,c_fill,q_auto,f_auto/v1234567890/novedadeslz/products/abc123.jpg
```

**Large (1200x800):**
```
https://res.cloudinary.com/demo/image/upload/w_1200,h_800,c_fill,q_auto,f_auto/v1234567890/novedadeslz/products/abc123.jpg
```

### Uso en Frontend

```javascript
// En tu componente de producto
const ProductCard = ({ product }) => {
  // URL original almacenada en la BD
  const originalUrl = product.imageUrl;

  // Generar thumbnail para la card
  const thumbnailUrl = getCloudinaryTransform(originalUrl, 300, 300);

  return (
    <img src={thumbnailUrl} alt={product.name} />
  );
};

// Función helper para transformaciones
const getCloudinaryTransform = (url, width, height) => {
  if (!url || !url.includes('cloudinary.com')) return url;

  const transformation = `w_${width},h_${height},c_fill,q_auto,f_auto`;
  const uploadIndex = url.indexOf('/upload/');

  if (uploadIndex !== -1) {
    return url.substring(0, uploadIndex + 8) + transformation + '/' +
           url.substring(uploadIndex + 8);
  }

  return url;
};
```

## 🔒 Seguridad

### Validaciones Implementadas

1. **Tipo de Archivo:**
   - Solo se permiten archivos con `Content-Type` que comience con `image/`
   - Tipos comunes: `image/jpeg`, `image/png`, `image/gif`, `image/webp`

2. **Tamaño de Archivo:**
   - Máximo: **5MB** por imagen
   - Configurado en `application.properties`

3. **Autorización:**
   - Solo usuarios con rol **ADMIN** pueden crear/actualizar/eliminar productos
   - JWT token requerido en el header `Authorization`

### Manejo de Errores

```java
// Si la imagen es inválida
{
  "success": false,
  "message": "El archivo debe ser una imagen",
  "data": null
}

// Si la imagen supera 5MB
{
  "success": false,
  "message": "La imagen no debe superar 5MB",
  "data": null
}

// Si falla la subida a Cloudinary
{
  "success": false,
  "message": "Error al subir la imagen: <detalle>",
  "data": null
}
```

## 📦 Estructura de Carpetas en Cloudinary

Las imágenes se organizan automáticamente:

```
novedadeslz/
  └── products/
      ├── <uuid-1>.jpg
      ├── <uuid-2>.png
      ├── <uuid-3>.webp
      └── ...
```

Cada imagen tiene un **UUID único** para evitar colisiones.

## 🧪 Pruebas con Postman

### 1. Crear Producto con Imagen

1. Método: **POST**
2. URL: `http://localhost:8080/api/products`
3. Headers:
   - `Authorization`: `Bearer <token>`
4. Body → **form-data**:
   - Key: `product`, Type: **Text**, Value:
     ```json
     {
       "name": "Test Product",
       "description": "Test description",
       "price": 99.99,
       "category": "Test",
       "stock": 10
     }
     ```
     *IMPORTANTE:* En Postman, selecciona el tipo de contenido como `application/json` en el dropdown al lado del campo
   - Key: `image`, Type: **File**, Value: Seleccionar archivo de imagen

### 2. Actualizar Producto con Nueva Imagen

1. Método: **PUT**
2. URL: `http://localhost:8080/api/products/1`
3. Headers:
   - `Authorization`: `Bearer <token>`
4. Body → **form-data**:
   - Key: `product`, Type: **Text**, Value: JSON del producto
   - Key: `image`, Type: **File**, Value: Nueva imagen (opcional)

## 📊 Respuesta Exitosa

```json
{
  "success": true,
  "message": "Producto creado exitosamente",
  "data": {
    "id": 1,
    "name": "Laptop Dell",
    "description": "Laptop de alto rendimiento",
    "price": 1299.99,
    "imageUrl": "https://res.cloudinary.com/demo/image/upload/v1234567890/novedadeslz/products/abc-123-def.jpg",
    "category": "Electrónica",
    "stock": 10,
    "active": true,
    "createdAt": "2024-12-19T00:00:00",
    "updatedAt": "2024-12-19T00:00:00",
    "lowStock": false
  }
}
```

## 🚀 Ventajas de Cloudinary

1. **Performance:**
   - CDN global con 300+ servidores
   - Carga rápida desde cualquier ubicación
   - Optimización automática de imágenes

2. **Transformaciones:**
   - Resize, crop, rotate on-the-fly
   - Conversión automática a WebP
   - Optimización de calidad

3. **Confiabilidad:**
   - 99.95% uptime SLA
   - Backup automático
   - URLs permanentes

4. **Escalabilidad:**
   - Plan gratuito: 25GB almacenamiento + 25GB bandwidth
   - Fácil upgrade cuando creces
   - Sin límite de imágenes

5. **Seguridad:**
   - HTTPS por defecto
   - Control de acceso granular
   - Protección contra hotlinking

## 🔍 Troubleshooting

### Error: "The POM for com.cloudinary:cloudinary-http44:jar:X.X.X is missing"

**Solución:** Asegúrate de usar la versión correcta en `pom.xml`:

```xml
<dependency>
    <groupId>com.cloudinary</groupId>
    <artifactId>cloudinary-http44</artifactId>
    <version>1.38.0</version>
</dependency>
```

### Error: "cloudinary.cloud-name is required"

**Solución:** Verifica que `application.properties` tiene las credenciales correctas:

```properties
cloudinary.cloud-name=tu-cloud-name
cloudinary.api-key=tu-api-key
cloudinary.api-secret=tu-api-secret
```

### Error: "Maximum upload size exceeded"

**Solución:** Ajusta el tamaño máximo en `application.properties`:

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

### Error: "The file must be an image"

**Solución:** Asegúrate de enviar un archivo con `Content-Type` que comience con `image/` (jpeg, png, gif, webp).

## 📚 Recursos Adicionales

- [Documentación oficial de Cloudinary](https://cloudinary.com/documentation)
- [Transformaciones de imagen](https://cloudinary.com/documentation/image_transformations)
- [Upload API](https://cloudinary.com/documentation/image_upload_api_reference)
- [Spring Boot con Cloudinary](https://cloudinary.com/documentation/java_integration)

## ✅ Checklist de Integración

- [x] Dependencia de Cloudinary agregada al `pom.xml`
- [x] `CloudinaryConfig` creado con credenciales
- [x] `CloudinaryService` implementado con upload/delete
- [x] `ProductController` actualizado para MultipartFile
- [x] `ProductService` integrado con CloudinaryService
- [x] Validaciones de tipo y tamaño implementadas
- [ ] Credenciales de Cloudinary configuradas en `application.properties`
- [ ] Probado endpoint de creación con Postman/CURL
- [ ] Probado endpoint de actualización con Postman/CURL
- [ ] Frontend actualizado para enviar archivos binarios
