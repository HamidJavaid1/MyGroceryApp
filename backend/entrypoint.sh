#!/bin/sh
set -e

# Run database migrations
python manage.py migrate --noinput

# Start the application
exec gunicorn bazarlink.wsgi:application --bind 0.0.0.0:8000
