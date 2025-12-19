# 🚨 RESUMEN URGENTE: Cambios en API de Productos

**Para:** Equipo Frontend
**De:** Backend Team
**Fecha:** 19 Diciembre 2024
**Prioridad:** 🔴 ALTA - Breaking Changes

---

## ⚠️ QUÉ CAMBIÓ

Los endpoints de productos **YA NO aceptan JSON**. Ahora requieren **FormData con archivos binarios**.

---

## 📝 ANTES vs AHORA

### ❌ ANTES (YA NO FUNCIONA)

```javascript
// OBSOLETO ❌
await axios.post('/api/products', {
  name: "Producto",
  price: 100,
  imageUrl: "https://...",  // ❌ Ya no existe
  category: "Test",
  stock: 10
});
```

### ✅ AHORA (FORMA CORRECTA)

```javascript
// CORRECTO ✅
const formData = new FormData();

// 1. Datos del producto como JSON Blob
formData.append('product', new Blob([JSON.stringify({
  name: "Producto",
  price: 100,
  category: "Test",
  stock: 10
})], { type: 'application/json' }));

// 2. Archivo de imagen
formData.append('image', imageFile);  // File from input

// 3. Enviar
await axios.post('/api/products', formData, {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'multipart/form-data'
  }
});
```

---

## 🔴 ENDPOINTS AFECTADOS

### `POST /api/products`
- ✅ Requiere FormData
- ✅ Imagen **OBLIGATORIA**
- ❌ `imageUrl` eliminado

### `PUT /api/products/{id}`
- ✅ Requiere FormData
- ✅ Imagen **OPCIONAL**
- ❌ `imageUrl` eliminado

---

## 💡 CÓDIGO RÁPIDO

```tsx
// React Component
const [imageFile, setImageFile] = useState<File | null>(null);

const handleSubmit = async () => {
  const formData = new FormData();

  formData.append('product', new Blob([JSON.stringify({
    name, description, price, category, stock
  })], { type: 'application/json' }));

  formData.append('image', imageFile!);

  await axios.post('/api/products', formData, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'multipart/form-data'
    }
  });
};

// JSX
<input type="file" onChange={(e) => setImageFile(e.target.files![0])} />
```

---

## ✅ VALIDACIONES RECOMENDADAS

```javascript
if (!file.type.startsWith('image/')) {
  alert('Solo imágenes JPG/PNG/GIF/WEBP');
  return;
}

if (file.size > 5 * 1024 * 1024) {
  alert('Máximo 5MB');
  return;
}
```

---

## 📚 DOCUMENTACIÓN COMPLETA

- **Changelog detallado:** [CHANGELOG_CLOUDINARY.md](CHANGELOG_CLOUDINARY.md)
- **Guía técnica:** [CLOUDINARY_INTEGRATION.md](CLOUDINARY_INTEGRATION.md)
- **Integración frontend:** [FRONTEND_INTEGRATION_GUIDE.md](FRONTEND_INTEGRATION_GUIDE.md)

---

## 🆘 ¿DUDAS?

Contacta al equipo de backend.
