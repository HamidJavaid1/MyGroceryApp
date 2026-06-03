#!/bin/sh
set -e

echo "Environment variables:"
echo "PORT=$PORT"
echo "DEBUG=$DEBUG"
echo "ALLOWED_HOSTS=$ALLOWED_HOSTS"
echo "USE_SQLITE=$USE_SQLITE"
echo "POSTGRES_HOST=$POSTGRES_HOST"

echo "Waiting for database to be ready..."
if [ "$USE_SQLITE" = "false" ]; then
    echo "Using PostgreSQL at $POSTGRES_HOST:$POSTGRES_PORT"
    # Wait for PostgreSQL to be ready
    python -c "
import sys
import time
import os
from decouple import config
try:
    import psycopg2
    for i in range(30):
        try:
            conn = psycopg2.connect(
                host=config('POSTGRES_HOST'),
                port=config('POSTGRES_PORT', default='5432'),
                database=config('POSTGRES_DB', default='bazarlink'),
                user=config('POSTGRES_USER', default='bazarlink'),
                password=config('POSTGRES_PASSWORD', default='bazarlink')
            )
            conn.close()
            print('Database is ready!')
            sys.exit(0)
        except Exception as e:
            print(f'Database not ready yet, waiting... ({e})')
            time.sleep(2)
    print('Database connection failed after retries')
    sys.exit(1)
except ImportError:
    print('psycopg2 not installed, skipping database check'
"
fi

echo "Starting database migrations..."
python manage.py migrate --noinput || echo "Migration failed or already applied"

echo "Collecting static files..."
python manage.py collectstatic --noinput || echo "Static collection failed"

echo "Starting Gunicorn on port $PORT..."
exec gunicorn bazarlink.wsgi:application --bind 0.0.0.0:$PORT --workers 2 --timeout 120 --access-logfile - --error-logfile -
