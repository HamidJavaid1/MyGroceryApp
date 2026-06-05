


# BazarLink — Grocery Marketplace App

# App Icon 

#<img width="1254" height="1254" alt="ChatGPT Image Jun 5, 2026, 03_37_28 PM" src="https://github.com/user-attachments/assets/09749ae3-0671-49e7-b1d0-f43cc877968f" />


> **Version:** 1.0.0 | **Platform:** Android | **Category:** Shopping / Grocery

BazarLink is a full-featured grocery marketplace that connects **customers**, **shopkeepers**, and **wholesalers** in one unified platform. Customers browse and order from nearby shops, shopkeepers manage their inventory and fulfill orders, and wholesalers post bulk supply offers with quotation-based pricing — all in real time.

---

## Table of Contents

- [App Overview](#app-overview)
- [Features by Role](#features-by-role)
- [Screenshots](#screenshots)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Backend Setup](#backend-setup)
- [Android Setup](#android-setup)
- [Firebase Integration](#firebase-integration)
- [Key API Endpoints](#key-api-endpoints)
- [WebSocket / Real-Time](#websocket--real-time)
- [Deployment](#deployment)
- [Privacy Policy](#privacy-policy)
- [App Permissions](#app-permissions)
- [Store Listing Details](#store-listing-details)
- [Contributing](#contributing)

---

## App Overview

| Field | Details |
|---|---|
| **App Name** | BazarLink |
| **Version Name** | 1.0.0 |
| **Version Code** | 1 |
| **Package ID** | `com.bazarlink.customer` |
| **Category** | Shopping / Grocery |
| **Minimum SDK** | Android 7.0 (API 24) |
| **Target SDK** | Android 14 (API 34) |
| **Platform** | Android |

---

## Features by Role

### Customer
- Browse product categories and search with filters (price, rating, category)
- View nearby shops using live location (haversine-based radius search)
- Place orders and track them in real time via WebSocket
- Receive push notifications on order status updates
- Google Sign-In and phone authentication support

### Shopkeeper
- Manage product listings, stock, and pricing
- Accept or reject incoming orders with status transitions
- View sales analytics and order history dashboard
- Receive FCM push notifications for new orders

### Wholesaler
- Post bulk supply requests and set minimum order quantities
- Receive and respond to quotations from shopkeepers
- Accept or decline quotation offers
- Access analytics overview for bulk transaction performance

---

## Screenshots

_Screenshots are included in the Play Store listing and in the `screenshots/` directory at the project root._


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

## Tech Stack

### Backend (`backend/`)
| Component | Technology |
|---|---|
| Framework | Django 4 + Django REST Framework |
| Authentication | Simple JWT |
| Database | PostgreSQL (Docker) / SQLite (local) |
| Real-Time | Django Channels + Redis |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Admin Panel | Jazzmin |
| API Docs | drf-spectacular (Swagger / OpenAPI) |
| Containerization | Docker + Docker Compose |
| Web Server | Nginx + Gunicorn |

### Android (`android/`)
| Component | Technology |
|---|---|
| Language | Java |
| HTTP Client | Retrofit 2 |
| Local Cache | Room (SQLite) |
| Authentication | Firebase Auth (Email, Google, Phone) |
| Notifications | Firebase Messaging (FCM) |
| Maps | Google Maps SDK |
| Image Loading | Glide |
| Animations | Lottie |
| UI | Material Design 3 |
| Charts | MPAndroidChart |

---

## Project Structure

```
BazarLink/
├── backend/                  # Django REST API
│   ├── apps/
│   │   ├── auth/             # JWT auth, registration
│   │   ├── users/            # User profiles, FCM tokens
│   │   ├── products/         # Product listings, categories
│   │   ├── shops/            # Shop management, location
│   │   ├── orders/           # Order lifecycle, transitions
│   │   ├── bulk_requests/    # Wholesale bulk requests
│   │   ├── quotations/       # Quotation flow
│   │   └── analytics/        # Dashboard analytics
│   ├── .env.example
│   └── requirements.txt
├── android/
│   ├── app/                  # Main Android app (role-based launcher)
│   └── shared/               # Shared: API client, Room, Firebase, models
├── deploy/
│   └── nginx/                # Nginx reverse proxy config
├── secrets/                  # Firebase Admin service account (gitignored)
├── scripts/
│   └── docker-dev.ps1        # Windows Docker helper
└── docker-compose.yml        # Full stack: Postgres, Redis, Django, Nginx
```

---

## Backend Setup

### Option A — Docker (Recommended)

1. Copy environment file:
   ```bash
   cp backend/.env.example backend/.env
   ```

2. Add Firebase Admin credentials:
   ```bash
   mkdir -p secrets
   cp path/to/firebase-service-account.json secrets/firebase-service-account.json
   ```

3. Start the full stack (API on **port 8000** for Android emulator):
   ```powershell
   # Windows
   .\scripts\docker-dev.ps1
   ```
   Or manually:
   ```bash
   docker compose up -d --build
   docker compose exec django python manage.py seed_dummy_data
   ```

4. Create a superuser for the admin panel:
   ```bash
   docker compose exec django python manage.py createsuperuser
   ```

5. Access local services:
   | Service | URL |
   |---|---|
   | Admin Panel | `http://localhost/admin/` |
   | Swagger Docs | `http://localhost/api/v1/docs/` |
   | OpenAPI Schema | `http://localhost/api/v1/schema/` |
   | Android Emulator Base URL | `http://10.0.2.2:8000/` |

### Option B — Windows Local (No Docker / No GDAL)

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env        # Make sure USE_SQLITE=True is set
python manage.py migrate
python manage.py seed_dummy_data
python manage.py runserver 0.0.0.0:8000
```

> Shop locations use plain latitude/longitude fields. Nearby shop queries use a Python haversine calculation — no GeoDjango or GDAL required.

### Demo Accounts (after `seed_dummy_data`)

All demo accounts use password `DemoPass123!`:

| Role | Username |
|---|---|
| Customer | `demo_customer` |
| Shopkeeper | `demo_shopkeeper` |
| Wholesaler | `demo_wholesaler` |

---

## Android Setup

1. Open the `android/` folder in **Android Studio**.
2. Run the `app` module on an emulator or physical device.
3. Set the API base URL in the app's build config or entry code:
   - Emulator → `http://10.0.2.2:8000/`
   - Physical device on same network → use your PC's local IP
4. The `shared/` module provides Retrofit, Room, Firebase, Google Maps, Glide, Lottie, Material 3, and chart dependencies automatically.

The single launcher app detects the user's role after login and routes them to the appropriate Customer, Shopkeeper, or Wholesaler experience.

---

## Firebase Integration

1. Create an Android app in [Firebase Console](https://console.firebase.google.com/) matching the application ID in `android/app/build.gradle` (default: `com.bazarlink.customer`).
2. Enable the following auth providers in Firebase Console:
   - Email/Password
   - Google Sign-In
   - Phone
3. Download `google-services.json` and place it at:
   ```
   android/app/google-services.json
   ```
4. Download the **Firebase Admin SDK** service account JSON and place it at:
   ```
   secrets/firebase-service-account.json
   ```

> If you change the application ID from `com.bazarlink.customer`, download a new `google-services.json` from the Firebase Console.

---

## Key API Endpoints

All endpoints are prefixed with `/api/v1/`.

### Authentication & Users
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/auth/register/` | Register a new user |
| `GET/PATCH` | `/users/me/` | Get or update current user profile |
| `POST` | `/users/fcm-token/` | Register FCM push token |

### Products & Shops
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/categories/` | List all product categories |
| `GET` | `/products/` | List products with filters (`category`, `min_price`, `max_price`, `rating`) |
| `GET` | `/shops/nearby/` | Find nearby shops (`lat`, `lng`, `radius_km`) |

### Orders
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/orders/` | Place a new order |
| `POST` | `/orders/{id}/transition/` | Advance order status (confirm, dispatch, complete, cancel) |

### Wholesale
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/bulk-requests/` | Create a wholesale bulk request |
| `POST` | `/quotations/` | Submit a quotation |
| `POST` | `/quotations/{id}/accept/` | Accept a quotation |

### Analytics
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/analytics/overview/` | Dashboard summary for shopkeepers/wholesalers |

---

## WebSocket / Real-Time

Live order tracking is available over WebSocket:

```
ws://<host>/ws/orders/{order_id}/
```

The Android app connects to this channel after placing an order and receives real-time status updates (e.g., `confirmed → dispatched → delivered`).

---

## Deployment

### VPS / Production

1. Point your domain to the VPS IP.
2. Update `ALLOWED_HOSTS` and set strong secrets in `backend/.env`.
3. Start the production stack:
   ```bash
   docker compose --env-file backend/.env up -d --build
   ```
4. The Nginx config supports **Let's Encrypt** HTTP-01 challenges at `/.well-known/acme-challenge/`. Add a certbot sidecar or run certbot on the host and mount certificates into `deploy/certbot/conf`.

---

## Privacy Policy

BazarLink collects only the data required to operate the marketplace:

- **Account information** (name, email, phone) for authentication and order communication.
- **Location data** (latitude/longitude) used only to show nearby shops; never stored continuously.
- **Device FCM token** used solely to deliver order status push notifications.
- **Order and transaction data** stored securely and never shared with third parties for advertising.

Users may request deletion of their account and all associated data by contacting support. Data is stored on encrypted servers and protected by industry-standard access controls.

---

## App Permissions

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | Discover shops near the user's current location |
| `ACCESS_COARSE_LOCATION` | Fallback location for shops nearby feature |
| `INTERNET` | API communication and real-time order tracking |
| `RECEIVE_BOOT_COMPLETED` | Restore notification listener on device restart |
| `POST_NOTIFICATIONS` (Android 13+) | Display order status push notifications |
| `CAMERA` (optional) | Profile photo upload |
| `READ_EXTERNAL_STORAGE` (optional) | Select product or profile images from gallery |

All permissions are requested at runtime. Location and camera permissions are optional and the app remains functional without them (with reduced features).

---

## Store Listing Details

| Field | Value |
|---|---|
| **App Name** | BazarLink — Grocery Marketplace |
| **Short Description** | Order groceries from nearby shops. Manage your store. Trade wholesale — all in one app. |
| **Category** | Shopping |
| **Content Rating** | Everyone |
| **Version Name** | 1.0.0 |
| **Version Code** | 1 |

### Full Description

BazarLink brings your local grocery market online. Whether you're a customer looking for fresh produce from the shop around the corner, a shopkeeper wanting to grow your business digitally, or a wholesaler managing bulk supply — BazarLink is built for you.

**For Customers:**
Find shops near you, browse categories, compare prices, and place orders in seconds. Track your order live from confirmation to delivery.

**For Shopkeepers:**
List your products, manage inventory, and receive orders with instant push notifications. View your sales analytics to grow smarter.

**For Wholesalers:**
Post bulk supply requests, receive quotations from buyers, and manage high-volume transactions through a streamlined workflow.

**Key Features:**
- Role-based experience in one app
- Real-time order tracking via WebSocket
- Nearby shop discovery using live GPS
- Secure login via Email, Google, or Phone
- Push notifications for every order update
- Fully offline-capable browsing with Room cache

---

## Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "feat: describe your change"`
4. Push and open a Pull Request.

Please follow the existing code style and include tests where applicable.

---

*BazarLink — Connecting markets, one order at a time.*

﻿# MyGroceryApp
