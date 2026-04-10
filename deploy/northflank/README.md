# Northflank deploy

Este backend puede desplegarse en Northflank usando el `Dockerfile` raíz.

## Servicio

- Tipo: `service`
- Fuente: repositorio Git
- Build: `Dockerfile`
- Puerto HTTP interno: `8080`
- Health check: `/actuator/health`

## Base de datos

Crear un addon `PostgreSQL` en el mismo proyecto y enlazar sus secretos al servicio.

Northflank expone `JDBC_POSTGRES_URI`, que puedes mapear a `DB_URL`.

## Variables de entorno mínimas

```env
DB_URL=${JDBC_POSTGRES_URI}
DB_DRIVER_CLASS_NAME=org.postgresql.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
HIBERNATE_DDL_AUTO=update

JWT_SECRET=<secreto-largo-de-al-menos-64-caracteres>
CORS_ALLOWED_ORIGINS=https://tu-frontend.vercel.app

CLOUDINARY_CLOUD_NAME=<cloud-name>
CLOUDINARY_API_KEY=<api-key>
CLOUDINARY_API_SECRET=<api-secret>

APP_ADMIN_ORDERS_URL=https://tu-frontend.vercel.app/admin/orders
APP_BOOTSTRAP_ADMIN_ENABLED=true
APP_BOOTSTRAP_ADMIN_EMAIL=<correo-admin>
APP_BOOTSTRAP_ADMIN_PASSWORD=<password-admin>
APP_BOOTSTRAP_ADMIN_FULL_NAME=Administrador
APP_BOOTSTRAP_ADMIN_PHONE=<telefono-admin>
APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=false
```

## Notas

- `HIBERNATE_DDL_AUTO=update` sirve para el primer despliegue sobre una base vacía.
- Cuando el esquema ya exista y esté estable, conviene cambiarlo a `none`.
- Si usas más de un dominio de frontend, sepáralos con comas en `CORS_ALLOWED_ORIGINS`.
