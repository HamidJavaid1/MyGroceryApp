import requests

# Login as shopkeeper
login_response = requests.post(
    "http://localhost:8000/api/v1/auth/login/",
    json={"username": "demo_shopkeeper", "password": "DemoPass123!"}
)
print("Login response:", login_response.status_code)
if login_response.status_code == 200:
    token = login_response.json()["access"]
    print(f"Token: {token[:20]}...")
    
    # Get analytics
    analytics_response = requests.get(
        "http://localhost:8000/api/v1/analytics/shopkeeper_dashboard/",
        headers={"Authorization": f"Bearer {token}"}
    )
    print("\nAnalytics response:", analytics_response.status_code)
    print("Analytics text:", analytics_response.text)
    if analytics_response.status_code == 200:
        print("Analytics data:", analytics_response.json())
else:
    print("Login failed:", login_response.text)
