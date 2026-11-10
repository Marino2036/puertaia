# PuertaIA Cloud — Render + Neon

## Render: variables de entorno
- `DATABASE_URL`: pega directamente la connection string de Neon, por ejemplo `postgresql://usuario:password@ep-...-pooler....neon.tech/neondb?sslmode=require`
- `APP_API_KEY`: clave secreta que copiarás en Android.
- `DEVICE_TOKEN`: token secreto que copiarás en `config.h` del ESP32.

El servidor convierte automáticamente la URL de Neon a JDBC y `schema.sql` crea las tablas al iniciar.

## Endpoints
- GET `/api/health`
- POST `/api/app/doors/{doorId}/open`
- GET `/api/app/commands/{id}`
- GET `/api/app/doors/{doorId}/status`
- GET `/api/device/{doorId}/commands/next`
- POST `/api/device/{doorId}/commands/{id}/complete`
- POST `/api/device/{doorId}/heartbeat`
- POST `/api/device/{doorId}/events`
