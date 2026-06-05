import os
import django
from django.db import models

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'bazarlink.settings')
django.setup()

from apps.orders.models import Order
from apps.shops.models import Shop
from apps.users.models import User

print("=== Checking Orders ===")
print(f"Status values: {Order.Status.values}")
orders = Order.objects.all()
print(f"Total orders: {orders.count()}")

for o in orders[:5]:
    print(f"Order {o.id}: status={o.status}, total={o.total}, shop={o.shop.name}")

print("\n=== Checking Shopkeeper Analytics ===")
shopkeeper = User.objects.get(username='demo_shopkeeper')
shop = Shop.objects.filter(owner=shopkeeper).first()
print(f"Shop: {shop.name if shop else 'None'}")

if shop:
    delivered_orders = Order.objects.filter(shop=shop, status=Order.Status.DELIVERED)
    print(f"Delivered orders: {delivered_orders.count()}")
    for o in delivered_orders:
        print(f"  Order {o.id}: total={o.total}")
    
    total_revenue = delivered_orders.aggregate(total=models.Sum('total'))['total'] or 0
    print(f"Total revenue: {total_revenue}")
