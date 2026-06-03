#!/bin/sh
set -e

echo "Environment variables:"
echo "PORT=$PORT"
echo "DEBUG=$DEBUG"
echo "ALLOWED_HOSTS=$ALLOWED_HOSTS"
echo "USE_SQLITE=$USE_SQLITE"
echo "POSTGRES_HOST=$POSTGRES_HOST"

echo "Starting database migrations..."
python manage.py migrate --noinput || echo "Migration failed or already applied"

echo "Collecting static files..."
python manage.py collectstatic --noinput || echo "Static collection failed"

echo "Starting Gunicorn on port $PORT..."
exec gunicorn bazarlink.wsgi:application --bind 0.0.0.0:$PORT --workers 2 --timeout 120 --access-logfile - --error-logfile -
