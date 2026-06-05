from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand

from apps.shops.models import Shop

User = get_user_model()


class Command(BaseCommand):
    help = "Create demo data for testing"

    def handle(self, *args, **kwargs):
        # Create demo users
        self.stdout.write("Creating demo users...")
        
        # Customer
        customer, created = User.objects.get_or_create(
            username="demo_customer",
            defaults={
                "email": "customer@example.com",
                "phone_number": "1234567890",
                "role": User.Role.CUSTOMER,
                "is_verified_provider": True,
            }
        )
        if created:
            customer.set_password("DemoPass123!")
            customer.save()
            self.stdout.write(self.style.SUCCESS(f"Created customer: {customer.username}"))
        else:
            self.stdout.write(self.style.WARNING(f"Customer already exists: {customer.username}"))

        # Shopkeeper
        shopkeeper, created = User.objects.get_or_create(
            username="demo_shopkeeper",
            defaults={
                "email": "shopkeeper@example.com",
                "phone_number": "2345678901",
                "role": User.Role.SHOPKEEPER,
                "is_verified_provider": True,
                "shop_name": "Demo Shop",
            }
        )
        if created:
            shopkeeper.set_password("DemoPass123!")
            shopkeeper.save()
            # Create shop for shopkeeper
            Shop.objects.get_or_create(
                owner=shopkeeper,
                defaults={
                    "name": "Demo Shop",
                    "kind": Shop.Kind.RETAIL,
                    "description": "A demo retail shop",
                    "address": "123 Main St",
                    "latitude": 24.9133,
                    "longitude": 67.0971,
                    "is_approved": True,
                }
            )
            self.stdout.write(self.style.SUCCESS(f"Created shopkeeper: {shopkeeper.username}"))
        else:
            self.stdout.write(self.style.WARNING(f"Shopkeeper already exists: {shopkeeper.username}"))

        # Wholesaler
        wholesaler, created = User.objects.get_or_create(
            username="demo_wholesaler",
            defaults={
                "email": "wholesaler@example.com",
                "phone_number": "3456789012",
                "role": User.Role.WHOLESALER,
                "is_verified_provider": True,
                "shop_name": "Demo Wholesale",
            }
        )
        if created:
            wholesaler.set_password("DemoPass123!")
            wholesaler.save()
            # Create shop for wholesaler
            Shop.objects.get_or_create(
                owner=wholesaler,
                defaults={
                    "name": "Demo Wholesale",
                    "kind": Shop.Kind.WHOLESALE,
                    "description": "A demo wholesale shop",
                    "address": "456 Market St",
                    "latitude": 24.9133,
                    "longitude": 67.0971,
                    "is_approved": True,
                }
            )
            self.stdout.write(self.style.SUCCESS(f"Created wholesaler: {wholesaler.username}"))
        else:
            self.stdout.write(self.style.WARNING(f"Wholesaler already exists: {wholesaler.username}"))

        # Admin
        admin, created = User.objects.get_or_create(
            username="admin",
            defaults={
                "email": "admin@example.com",
                "phone_number": "4567890123",
                "role": User.Role.ADMIN,
                "is_verified_provider": True,
                "is_staff": True,
                "is_superuser": True,
            }
        )
        if created:
            admin.set_password("DemoPass123!")
            admin.save()
            self.stdout.write(self.style.SUCCESS(f"Created admin: {admin.username}"))
        else:
            self.stdout.write(self.style.WARNING(f"Admin already exists: {admin.username}"))

        self.stdout.write(self.style.SUCCESS("Demo data creation complete!"))
        self.stdout.write("Login credentials:")
        self.stdout.write("  Customer: username='demo_customer', password='DemoPass123!'")
        self.stdout.write("  Shopkeeper: username='demo_shopkeeper', password='DemoPass123!'")
        self.stdout.write("  Wholesaler: username='demo_wholesaler', password='DemoPass123!'")
        self.stdout.write("  Admin: username='admin', password='DemoPass123!'")