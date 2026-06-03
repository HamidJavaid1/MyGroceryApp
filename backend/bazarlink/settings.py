import sys
from datetime import timedelta
from pathlib import Path

from decouple import Csv, config

BASE_DIR = Path(__file__).resolve().parent.parent

# Local Windows dev: set USE_SQLITE=true in backend/.env (no Postgres/Redis/GDAL required).
# For production (Render), default to False to use PostgreSQL
USE_SQLITE = config("USE_SQLITE", default=False, cast=bool)

SECRET_KEY = config("SECRET_KEY", default="change-me-in-production")
DEBUG = config("DEBUG", default=USE_SQLITE, cast=bool)

ALLOWED_HOSTS = config(
    "ALLOWED_HOSTS",
    default="*",
    cast=Csv()
)

CSRF_TRUSTED_ORIGINS = config(
    "CSRF_TRUSTED_ORIGINS",
    default="https://mygroceryapp-4ryn.onrender.com,http://mygroceryapp-4ryn.onrender.com",
    cast=Csv()
)

INSTALLED_APPS = [
    "jazzmin",
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "rest_framework_simplejwt",
    "django_filters",
    "corsheaders",
    "drf_spectacular",
    "channels",
    "apps.users",
    "apps.shops",
    "apps.products",
    "apps.orders",
    "apps.notifications",
    "apps.analytics",
]

PAYMENT_GATEWAY_MODE = config("PAYMENT_GATEWAY_MODE", default="mock")
EASYPAISA_STORE_ID = config("EASYPAISA_STORE_ID", default="")
EASYPAISA_API_KEY = config("EASYPAISA_API_KEY", default="")
EASYPAISA_API_SECRET = config("EASYPAISA_API_SECRET", default="")
EASYPAISA_INITIATE_URL = config("EASYPAISA_INITIATE_URL", default="")
EASYPAISA_CONFIRM_URL = config("EASYPAISA_CONFIRM_URL", default="")
JAZZCASH_MERCHANT_ID = config("JAZZCASH_MERCHANT_ID", default="")
JAZZCASH_PASSWORD = config("JAZZCASH_PASSWORD", default="")
JAZZCASH_INTEGRITY_SALT = config("JAZZCASH_INTEGRITY_SALT", default="")
JAZZCASH_INITIATE_URL = config("JAZZCASH_INITIATE_URL", default="")
JAZZCASH_CONFIRM_URL = config("JAZZCASH_CONFIRM_URL", default="")

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    # "django.middleware.security.SecurityMiddleware",  # Disabled temporarily
    "whitenoise.middleware.WhiteNoiseMiddleware",
    # "django.contrib.sessions.middleware.SessionMiddleware",  # Disabled temporarily
    "django.middleware.common.CommonMiddleware",
    # "django.middleware.csrf.CsrfViewMiddleware",  # Disabled for JWT API
    # "django.contrib.auth.middleware.AuthenticationMiddleware",  # Disabled temporarily
    # "django.contrib.messages.middleware.MessageMiddleware",  # Disabled temporarily
    # "django.middleware.clickjacking.XFrameOptionsMiddleware",  # Disabled temporarily
]

ROOT_URLCONF = "bazarlink.urls"
ASGI_APPLICATION = "bazarlink.asgi.application"
WSGI_APPLICATION = "bazarlink.wsgi.application"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [BASE_DIR / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ]
        },
    }
]

if USE_SQLITE:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": BASE_DIR / "db.sqlite3",
        }
    }

    CHANNEL_LAYERS = {
        "default": {
            "BACKEND": "channels.layers.InMemoryChannelLayer"
        }
    }
else:
    try:
        DATABASES = {
            "default": {
                "ENGINE": "django.db.backends.postgresql",
                "NAME": config("POSTGRES_DB", default="bazarlink"),
                "USER": config("POSTGRES_USER", default="bazarlink"),
                "PASSWORD": config("POSTGRES_PASSWORD", default="bazarlink"),
                "HOST": config("POSTGRES_HOST", default="localhost"),
                "PORT": config("POSTGRES_PORT", default="5432"),
                "CONN_MAX_AGE": 600,
                "OPTIONS": {
                    "connect_timeout": 5,
                },
            }
        }
    except:
        # Fallback to SQLite if PostgreSQL configuration fails
        DATABASES = {
            "default": {
                "ENGINE": "django.db.backends.sqlite3",
                "NAME": BASE_DIR / "db.sqlite3",
            }
        }

    try:
        CHANNEL_LAYERS = {
            "default": {
                "BACKEND": "channels_redis.core.RedisChannelLayer",
                "CONFIG": {
                    "hosts": [config("REDIS_URL", default="redis://localhost:6379/0")]
                },
            }
        }
    except:
        CHANNEL_LAYERS = {
            "default": {
                "BACKEND": "channels.layers.InMemoryChannelLayer"
            }
        }

AUTH_USER_MODEL = "users.User"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
    "DEFAULT_RENDERER_CLASSES": (
        "rest_framework.renderers.JSONRenderer",
    ),
    "DEFAULT_PERMISSION_CLASSES": (
        "rest_framework.permissions.AllowAny",
    ),
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    "DEFAULT_FILTER_BACKENDS": (
        "django_filters.rest_framework.DjangoFilterBackend",
        "rest_framework.filters.SearchFilter",
        "rest_framework.filters.OrderingFilter",
    ),
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.PageNumberPagination",
    "PAGE_SIZE": 20,
}

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=30),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=30),
}

SPECTACULAR_SETTINGS = {
    "TITLE": "BazarLink API",
    "DESCRIPTION": "Versioned API for customer, shopkeeper, wholesaler, and admin clients.",
    "VERSION": "1.0.0",
    "SERVE_INCLUDE_SCHEMA": False,
}

CORS_ALLOWED_ORIGINS = config("CORS_ALLOWED_ORIGINS", default="https://mygroceryapp-4ryn.onrender.com,http://mygroceryapp-4ryn.onrender.com", cast=Csv())
CORS_ALLOW_ALL_ORIGINS = config("CORS_ALLOW_ALL_ORIGINS", default=True, cast=bool)

LANGUAGE_CODE = "en-us"
TIME_ZONE = config("TIME_ZONE", default="UTC")
USE_I18N = True
USE_TZ = True

# Disable APPEND_SLASH to prevent 400 errors on root URL
APPEND_SLASH = False

# Security settings for production - all disabled temporarily
SECURE_SSL_REDIRECT = False
SESSION_COOKIE_SECURE = False
CSRF_COOKIE_SECURE = False
SECURE_HSTS_SECONDS = 0
SECURE_HSTS_INCLUDE_SUBDOMAINS = False
SECURE_HSTS_PRELOAD = False
SECURE_BROWSER_XSS_FILTER = False
SECURE_CONTENT_TYPE_NOSNIFF = False
X_FRAME_OPTIONS = "SAMEORIGIN"


STATIC_URL = "/static/"
STATICFILES_DIRS = [BASE_DIR / "static"]
STATIC_ROOT = BASE_DIR / "staticfiles"
STATICFILES_STORAGE = "whitenoise.storage.CompressedManifestStaticFilesStorage"
WHITENOISE_MANIFEST_STRICT = False

MEDIA_URL = "/media/"
MEDIA_ROOT = BASE_DIR / "media"

USE_S3 = config("USE_S3", default=False, cast=bool)
if USE_S3:
    DEFAULT_FILE_STORAGE = "storages.backends.s3boto3.S3Boto3Storage"
    AWS_STORAGE_BUCKET_NAME = config("AWS_STORAGE_BUCKET_NAME")
    AWS_S3_REGION_NAME = config("AWS_S3_REGION_NAME", default=None)
    AWS_QUERYSTRING_AUTH = False

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

JAZZMIN_SETTINGS = {
    "site_title": "BazarLink Admin",
    "site_header": "BazarLink",
    "welcome_sign": "BazarLink marketplace operations",
    "show_ui_builder": False,
}

FIREBASE_CREDENTIALS_FILE = config("FIREBASE_CREDENTIALS_FILE", default="")