from decimal import Decimal
from datetime import datetime, timedelta

from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand
from django.utils import timezone

from apps.orders.models import BulkRequest, Order, OrderItem, Quotation
from apps.products.models import Category, Product
from apps.shops.models import Shop

User = get_user_model()


class Command(BaseCommand):
    help = "Create demo data for testing"

    def handle(self, *args, **kwargs):
        # Create demo categories
        self.stdout.write("Creating demo categories...")
        categories_data = [
            {"name": "Fruits", "slug": "fruits"},
            {"name": "Vegetables", "slug": "vegetables"},
            {"name": "Dairy", "slug": "dairy"},
            {"name": "Meat", "slug": "meat"},
            {"name": "Bakery", "slug": "bakery"},
            {"name": "Beverages", "slug": "beverages"},
            {"name": "Snacks", "slug": "snacks"},
            {"name": "Grains", "slug": "grains"},
            {"name": "Spices", "slug": "spices"},
            {"name": "Frozen Foods", "slug": "frozen-foods"},
        ]
        for cat_data in categories_data:
            Category.objects.get_or_create(
                slug=cat_data["slug"],
                defaults={"name": cat_data["name"]}
            )
        self.stdout.write(self.style.SUCCESS(f"Created {len(categories_data)} categories"))

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

        # Create demo products and orders
        self.stdout.write("Creating demo products and orders...")
        self._create_demo_products_and_orders(shopkeeper, customer)
        self._create_demo_products_and_orders(wholesaler, customer)

        # Create demo bulk requests and quotations for wholesaler
        self.stdout.write("Creating demo bulk requests and quotations...")
        self._create_demo_bulk_requests(wholesaler, customer)

    def _create_demo_products_and_orders(self, shopkeeper, customer):
        shop = Shop.objects.filter(owner=shopkeeper).first()
        if not shop:
            return

        # Create demo products
        products_data = [
            {"name": "Apples", "price": 5.99, "stock_quantity": 100, "category": Category.objects.filter(name="Fruits").first()},
            {"name": "Carrots", "price": 3.99, "stock_quantity": 150, "category": Category.objects.filter(name="Vegetables").first()},
            {"name": "Milk", "price": 4.50, "stock_quantity": 80, "category": Category.objects.filter(name="Dairy").first()},
            {"name": "Chicken", "price": 12.99, "stock_quantity": 50, "category": Category.objects.filter(name="Meat").first()},
            {"name": "Bread", "price": 2.50, "stock_quantity": 200, "category": Category.objects.filter(name="Bakery").first()},
        ]

        products = []
        for prod_data in products_data:
            category = prod_data.pop("category")
            if not category:
                continue
            product, created = Product.objects.get_or_create(
                shop=shop,
                name=prod_data["name"],
                defaults={
                    **prod_data,
                    "category": category,
                    "description": f"Fresh {prod_data['name']} from our shop",
                    "unit": "kg",
                    "is_bulk_available": shop.kind == Shop.Kind.WHOLESALE,
                    "min_bulk_quantity": 10 if shop.kind == Shop.Kind.WHOLESALE else None,
                }
            )
            if created:
                products.append(product)
                self.stdout.write(self.style.SUCCESS(f"Created product: {product.name}"))
            else:
                products.append(product)

        # Create demo orders with delivered status for analytics
        for i in range(5):
            if not products:
                break
            product = products[i % len(products)]
            quantity = Decimal("2")
            subtotal = product.price * quantity
            delivery_fee = Decimal("2.00")
            total = subtotal + delivery_fee
            
            order = Order.objects.create(
                customer=customer,
                shop=shop,
                status=Order.Status.DELIVERED,
                address=f"{i+1} Test Street, City",
                payment_method="cash",
                payment_status=Order.PaymentStatus.PAID,
                subtotal=subtotal,
                delivery_fee=delivery_fee,
                total=total,
            )
            OrderItem.objects.create(
                order=order,
                product=product,
                quantity=quantity,
                unit_price=product.price,
            )
            self.stdout.write(self.style.SUCCESS(f"Created order #{order.id} for {product.name} - Total: ${total}"))

    def _create_demo_bulk_requests(self, wholesaler, customer):
        shop = Shop.objects.filter(owner=wholesaler, kind=Shop.Kind.WHOLESALE).first()
        if not shop:
            return

        # Get wholesaler products
        products = Product.objects.filter(shop=shop)[:3]
        if not products:
            return

        # Create demo bulk requests
        for i, product in enumerate(products):
            bulk_request = BulkRequest.objects.create(
                customer=customer,
                product=product,
                quantity=Decimal("50"),
                delivery_address=f"{i+1} Bulk Street, City",
                notes=f"Bulk request for {product.name}",
                status=BulkRequest.Status.OPEN if i < 2 else BulkRequest.Status.CLOSED,
            )
            self.stdout.write(self.style.SUCCESS(f"Created bulk request #{bulk_request.id} for {product.name}"))

            # Create quotation for open requests
            if bulk_request.status == BulkRequest.Status.OPEN:
                quotation = Quotation.objects.create(
                    bulk_request=bulk_request,
                    wholesaler=wholesaler,
                    price_per_unit=product.price * Decimal("0.9"),  # 10% discount
                    delivery_fee=Decimal("5.00"),
                    valid_until=timezone.now() + timedelta(days=7),
                    message=f"Special wholesale price for {product.name}",
                )
                self.stdout.write(self.style.SUCCESS(f"Created quotation #{quotation.id}"))