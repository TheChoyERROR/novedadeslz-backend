# Deploy Backend on OCI Compute

This project runs well on an OCI Always Free compute instance with Java 21.

## Recommended shape

- First choice: `VM.Standard.A1.Flex`
- Fallback: `VM.Standard.E2.1.Micro`

## 1. Create the VM

- Launch a public Linux instance in your home region.
- Use Oracle Linux 9 or Ubuntu 24.04.
- Add your SSH public key.
- Assign a public IP.

## 2. Open network access

- Keep SSH (`22`) restricted to your own IP.
- For a quick first deploy, open `8080` to `0.0.0.0/0`.
- For a proper production setup, expose `80/443` with a reverse proxy and keep the app bound to `8080`.

## 3. Connect to the VM

Oracle Linux:

```bash
ssh -i ~/.ssh/your_key opc@YOUR_PUBLIC_IP
```

Ubuntu:

```bash
ssh -i ~/.ssh/your_key ubuntu@YOUR_PUBLIC_IP
```

## 4. Install Java 21 and Git

Oracle Linux:

```bash
sudo dnf update -y
sudo dnf install -y java-21-openjdk git
java -version
```

Ubuntu:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git
java -version
```

## 5. Copy the app to the server

Option A: clone the repo on the VM

```bash
git clone YOUR_BACKEND_REPO_URL
cd novedadeslz-backend
./mvnw clean package -DskipTests
```

Option B: build locally and upload the jar

```bash
./mvnw clean package -DskipTests
scp -i ~/.ssh/your_key target/backend-0.0.1-SNAPSHOT.jar opc@YOUR_PUBLIC_IP:/tmp/novedadeslz-backend.jar
```

## 6. Prepare runtime folders

```bash
sudo mkdir -p /opt/novedadeslz/backend
sudo mkdir -p /var/log/novedadeslz
sudo cp /tmp/novedadeslz-backend.jar /opt/novedadeslz/backend/novedadeslz-backend.jar
```

If you build on the VM, copy the jar from `target/` instead:

```bash
sudo cp target/backend-0.0.1-SNAPSHOT.jar /opt/novedadeslz/backend/novedadeslz-backend.jar
```

## 7. Create the environment file

```bash
sudo tee /opt/novedadeslz/backend/backend.env > /dev/null <<'EOF'
SERVER_PORT=8080
DB_URL=jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.sa-bogota-1.oraclecloud.com))(connect_data=(service_name=gb0ec77624b055c_novedadeslz_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))
DB_USERNAME=ADMIN
DB_PASSWORD=CHANGE_ME
JWT_SECRET=CHANGE_ME_TO_A_LONG_RANDOM_SECRET
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
OCR_SPACE_API_KEY=
YAPE_RECIPIENT_NAME=Leslie Lopez
YAPE_RECIPIENT_PHONE=939662630
WHATSAPP_NOTIFICATIONS_ENABLED=true
WHATSAPP_PROVIDER=auto
WHATSAPP_ADMIN_PHONE=+51939662630
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
APP_ADMIN_ORDERS_URL=https://YOUR_FRONTEND_DOMAIN/admin/orders
CORS_ALLOWED_ORIGINS=https://YOUR_FRONTEND_DOMAIN
APP_BOOTSTRAP_ADMIN_ENABLED=false
APP_BOOTSTRAP_ADMIN_EMAIL=admin@novedadeslz.com
APP_BOOTSTRAP_ADMIN_PASSWORD=CHANGE_ME
APP_BOOTSTRAP_ADMIN_FULL_NAME=Administrador Novedades LZ
APP_BOOTSTRAP_ADMIN_PHONE=+51939662630
APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=false
EOF
```

## 8. Install the systemd service

Copy `deploy/oci/novedadeslz-backend.service` to the VM:

```bash
sudo cp deploy/oci/novedadeslz-backend.service /etc/systemd/system/novedadeslz-backend.service
sudo systemctl daemon-reload
sudo systemctl enable novedadeslz-backend
sudo systemctl start novedadeslz-backend
```

## 9. Check logs and health

```bash
sudo systemctl status novedadeslz-backend
sudo journalctl -u novedadeslz-backend -f
curl http://localhost:8080/actuator/health
curl http://YOUR_PUBLIC_IP:8080/api/products
```

## 10. Optional first-run admin reset

Only if you need to recreate the app admin password:

```bash
sudo sed -i 's/APP_BOOTSTRAP_ADMIN_ENABLED=false/APP_BOOTSTRAP_ADMIN_ENABLED=true/' /opt/novedadeslz/backend/backend.env
sudo sed -i 's/APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=false/APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=true/' /opt/novedadeslz/backend/backend.env
sudo systemctl restart novedadeslz-backend
```

After the admin is created or reset, disable it again:

```bash
sudo sed -i 's/APP_BOOTSTRAP_ADMIN_ENABLED=true/APP_BOOTSTRAP_ADMIN_ENABLED=false/' /opt/novedadeslz/backend/backend.env
sudo sed -i 's/APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=true/APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=false/' /opt/novedadeslz/backend/backend.env
sudo systemctl restart novedadeslz-backend
```

## Next step

Once the backend responds from the public IP, add a domain and HTTPS reverse proxy before wiring the production frontend.
