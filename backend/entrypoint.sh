#!/bin/sh
set -e

echo "Starting database migrations..."
python manage.py migrate --noinput || echo "Migration failed or already applied"

echo "Collecting static files..."
python manage.py collectstatic --noinput || echo "Static collection failed"

echo "Starting Gunicorn..."
exec gunicorn bazarlink.wsgi:application --bind 0.0.0.0:$PORT --workers 2 --timeout 120
