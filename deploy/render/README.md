# Deploy Backend on Render with Oracle OCI

This backend should be deployed on Render as a Docker web service and connected to the Oracle Autonomous Database already created in OCI.

## Why Docker on Render

Render recommends Docker for JVM-based apps such as Java and Kotlin.

## 1. Create the Render service

- Create a new `Web Service`
- Connect the backend GitHub repository
- Runtime: `Docker`

Render docs:

- https://render.com/docs/docker
- https://render.com/docs/your-first-deploy

## 2. Basic service settings

- Branch: your deploy branch
- Region: choose the closest available region to your users
- Instance type: `Free` for the first deploy

## 3. Environment variables

Add these variables in Render:

```env
PORT=10000
# Obligatorio: sin esto el backend no arranca. Generar con `openssl rand -base64 64`.
JAVA_OPTS=-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError
DB_URL=jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.sa-bogota-1.oraclecloud.com))(connect_data=(service_name=gb0ec77624b055c_novedadeslz_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))
DB_USERNAME=ADMIN
DB_PASSWORD=CHANGE_ME
JWT_SECRET=CHANGE_ME_TO_A_LONG_RANDOM_SECRET
OCR_SPACE_API_KEY=
YAPE_RECIPIENT_NAME=Leslie Lopez
YAPE_RECIPIENT_PHONE=939662630
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
WHATSAPP_NOTIFICATIONS_ENABLED=true
WHATSAPP_PROVIDER=auto
WHATSAPP_ADMIN_PHONE=+51939662630
WHATSAPP_CALLMEBOT_API_KEY=
APP_PUBLIC_BASE_URL=https://YOUR_SERVICE.onrender.com
APP_ADMIN_ORDERS_URL=https://YOUR_FRONTEND_DOMAIN/admin/orders
CORS_ALLOWED_ORIGINS=https://YOUR_FRONTEND_DOMAIN
APP_BOOTSTRAP_ADMIN_ENABLED=false
APP_BOOTSTRAP_ADMIN_EMAIL=admin@novedadeslz.com
APP_BOOTSTRAP_ADMIN_PASSWORD=CHANGE_ME
APP_BOOTSTRAP_ADMIN_FULL_NAME=Administrador Novedades LZ
APP_BOOTSTRAP_ADMIN_PHONE=+51939662630
APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=false
LOG_LEVEL=INFO
RATE_LIMIT_ENABLED=true
DB_POOL_SIZE=10
```

## 3.1 Migraciones de base de datos

Ya no hay que ejecutar SQL a mano. Flyway aplica las migraciones de
`src/main/resources/db/migration` al arrancar el backend, y lleva el registro en la tabla
`flyway_schema_history`.

La primera vez que arranque esta version contra la base actual, Flyway:

1. Crea `flyway_schema_history` y registra la linea base en la version 0
2. Ejecuta V1, que detecta que las tablas ya existen y no hace nada
3. Ejecuta V2 y V3, que ya estaban aplicadas y quedan como no-op
4. Ejecuta V4, que agrega `orders.public_token` y lo rellena en los pedidos existentes
5. Ejecuta V5, que crea los indices de `orders`, `order_items` y `products`

Los pedidos que ya existen conservan sus datos y reciben su token en el paso 4.

Para agregar una migracion nueva, se crea un archivo `V<n>__descripcion.sql` en esa carpeta. No
hay que tocar los archivos ya aplicados: Flyway valida su checksum y el arranque falla si cambian.

`FLYWAY_ENABLED=false` desactiva todo esto si alguna vez hace falta arrancar sin migrar.


## 3.2 Notas de seguridad

- `JWT_SECRET` es obligatorio y debe tener al menos 64 bytes. El backend **no arranca** si falta,
  si es demasiado corto o si es el valor que estuvo versionado en el repositorio.
- `JAVA_OPTS` tiene un valor por defecto en el Dockerfile. Sin el, la JVM solo usa el 25% de la RAM
  del contenedor y una par de subidas concurrentes provocan OutOfMemoryError.
- El rate limiting es en memoria. Si algun dia se escala a mas de una instancia, hay que moverlo a
  un store compartido.

## 4. Oracle ACL

Your Oracle DB currently allows your home IP. Render will need access too.

After the Render service is created:

- Open the service details page
- Open `Connect`
- Open the `Outbound` tab
- Copy all listed IP addresses and ranges

Then add all of them to the Oracle Autonomous DB Access Control List.

Render docs:

- https://render.com/docs/static-outbound-ip-addresses

## 5. First health checks

Once the service is live, test:

- `https://YOUR_SERVICE.onrender.com/actuator/health`
- `https://YOUR_SERVICE.onrender.com/api/products`

## Notes

- The Render free web service sleeps after inactivity, so the first request can be slow.
- Use Cloudinary in production so product images do not depend on ephemeral disk storage.
