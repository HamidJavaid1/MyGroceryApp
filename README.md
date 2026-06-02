
<img width="328" height="739" alt="image" src="https://github.com/user-attachments/assets/19fa9591-b2c9-4979-9fac-cb735a256786" />
<img width="328" height="742" alt="image" src="https://github.com/user-attachments/assets/ada2467b-22f2-4bd3-a3c8-40c5665cb153" />
<img width="327" height="731" alt="image" src="https://github.com/user-attachments/assets/0ad65568-d2ca-400e-bcf3-ab2437e40d47" />
<img width="329" height="738" alt="image" src="https://github.com/user-attachments/assets/6d0da277-36a7-44b4-ab19-e2c052b568cb" />
<img width="313" height="723" alt="image" src="https://github.com/user-attachments/assets/daf13097-b204-4603-a740-a1f79168bcef" />
<img width="319" height="578" alt="image" src="https://github.com/user-attachments/assets/f53d6e80-f957-4f4c-aa35-491216211925" />
<img width="325" height="733" alt="image" src="https://github.com/user-attachments/assets/0e4cfa0a-31c1-4820-b7c3-20e849b732fd" />
<img width="318" height="725" alt="image" src="https://github.com/user-attachments/assets/5d13a0ac-9d72-4321-977f-88e41ccc2e25" />
<img width="328" height="723" alt="image" src="https://github.com/user-attachments/assets/b42e3bde-c57a-4216-92e1-1ebb42d52bb9" />
<img width="325" height="731" alt="image" src="https://github.com/user-attachments/assets/6be81499-019b-49aa-b38b-7800420a3cda" />

 <img width="329" height="712" alt="image" src="https://github.com/user-attachments/assets/0bc462f7-4f82-4bfd-849b-f19eb5243b88" />
<img width="323" height="735" alt="image" src="https://github.com/user-attachments/assets/d19f2233-2163-4434-a401-300decaa99e9" />
<img width="322" height="726" alt="image" src="https://github.com/user-attachments/assets/f669b05a-3422-4bd5-9031-db0586b577a4" />
<img width="328" height="725" alt="image" src="https://github.com/user-attachments/assets/bad421da-05a6-4c10-971f-e3f70b2fd056" />
<img width="332" height="733" alt="image" src="https://github.com/user-attachments/assets/f11e3c4a-a21a-4f1b-8ff4-ff1d5740953b" />
<img width="329" height="734" alt="image" src="https://github.com/user-attachments/assets/402a25e6-9674-438d-98bd-b01a2ca24cac" />
<img width="330" height="728" alt="image" src="https://github.com/user-attachments/assets/4f45cd06-7ef3-455e-8ab4-42b9ebc2dd84" />
<img width="325" height="717" alt="image" src="https://github.com/user-attachments/assets/9dd6e721-43f2-4b3f-b6b3-75fd16421721" />

# BazarLink

BazarLink is a grocery marketplace monorepo with a Django REST backend and one Android Java client that uses role-based access for customers, shopkeepers, and wholesalers.

## Structure

- `backend/` - Django 4, DRF, Simple JWT, PostgreSQL/SQLite, Channels, FCM, Jazzmin admin, drf-spectacular.
- `android/app/` - Single Android app with role-based customer, shopkeeper, and wholesaler access.
- `android/shared/` - Shared Java API, Firebase messaging, Room cache, and model code.
- `deploy/nginx/` - Nginx reverse proxy config.
- `docker-compose.yml` - PostgreSQL/PostGIS, Redis, Django/Gunicorn, Nginx stack.

## Backend Setup

1. Copy environment values:

   ```bash
   cp backend/.env.example backend/.env
   ```

2. Add Firebase Admin credentials:

   ```bash
   mkdir -p secrets
   cp path/to/firebase-service-account.json secrets/firebase-service-account.json
   ```

3. Copy Docker env (Postgres user/password must match in one file):

   ```bash
   cp backend/.env.example backend/.env
   ```

4. Start the stack (API on port **8000** for the Android emulator):

   ```powershell
   # Windows
   .\scripts\docker-dev.ps1
   ```

   Or manually:

   ```bash
   docker compose up -d --build
   docker compose exec django python manage.py seed_dummy_data
   ```

   The Android app calls **`http://10.0.2.2:8000/`** (emulator → your PC). Keep Docker running while testing.

5. Create an admin user:

   ```bash
   docker compose exec django python manage.py createsuperuser
   ```

   Demo logins (after `seed_dummy_data`) use password `DemoPass123!`:

   - `demo_customer`
   - `demo_shopkeeper`
   - `demo_wholesaler`

6. Open in a browser on your PC:

   - Admin: `http://localhost/admin/`
   - Swagger: `http://localhost/api/v1/docs/`
   - Schema: `http://localhost/api/v1/schema/`

## Windows local backend (no GDAL / Docker)

1. From `backend/`:

   ```powershell
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   copy .env.example .env
   ```

   Ensure `.env` contains `USE_SQLITE=True` (default on Windows).

2. Initialize the database and demo data:

   ```powershell
   python manage.py migrate
   python manage.py seed_dummy_data
   python manage.py runserver 0.0.0.0:8000
   ```

3. Point the Android emulator at `http://10.0.2.2:8000/`.

Shop locations use latitude/longitude fields (no GeoDjango). Nearby shops are filtered with a haversine distance calculation in Python.

## Android Setup

Open `android/` in Android Studio and run the `app` module. The app shows the customer, shopkeeper, or wholesaler experience from one launcher based on the user's role.

Set the API base URL in the app entry code or a build config before release. The shared module already includes Retrofit 2, Room, Firebase Auth/Messaging, Google Sign-In, Google Maps, Glide, Lottie, Material 3, and chart dependencies.

## Firebase Integration

1. Create one Android app in Firebase for the application id used by `android/app/build.gradle`.
   The current development build keeps `com.bazarlink.customer` so the existing Firebase JSON continues to work.
2. If you change the application id, download a new `google-services.json`.
3. Place the file in:
   - `android/app/google-services.json`
4. Enable Email/Password, Google Sign-In, and Phone auth in Firebase Console.
5. Download a Firebase Admin service-account JSON and mount it at `secrets/firebase-service-account.json`.

Older Firebase app ids may still exist in the project from the previous split-app setup:
   - `com.bazarlink.customer`
   - `com.bazarlink.shopkeeper`
   - `com.bazarlink.wholesaler`

## Key API Endpoints

All endpoints are under `/api/v1/`.

- `POST /auth/register/`
- `GET/PATCH /users/me/`
- `POST /users/fcm-token/`
- `GET /categories/`
- `GET /products/?category=&min_price=&max_price=&rating=`
- `GET /shops/nearby/?lat=&lng=&radius_km=`
- `POST /orders/`
- `POST /orders/{id}/transition/`
- `POST /bulk-requests/`
- `POST /quotations/`
- `POST /quotations/{id}/accept/`
- `GET /analytics/overview/`

WebSocket order tracking is available at `/ws/orders/{order_id}/`.

## VPS Deployment Notes

Point your domain to the VPS, update `ALLOWED_HOSTS`, set strong database and Django secrets, then run:

```bash
docker compose --env-file backend/.env up -d --build
```

The Nginx config is ready for Let's Encrypt HTTP-01 challenges under `/.well-known/acme-challenge/`. Add a certbot sidecar or run certbot on the host and mount certificates into `deploy/certbot/conf`.
﻿# MyGroceryApp
