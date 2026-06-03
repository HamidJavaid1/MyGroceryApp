package com.bazarlink.shared.models;

public class User {
	public long id;
	public String username;
	public String first_name;
	public String last_name;
	public String email;
	public String role;
	public String shop_name;
	public Long shop_id;
	public String shop_kind;
	public boolean is_shop_approved;
	public String phone_number;
	public String avatar;
	public boolean notification_enabled;
	public boolean inventory_alerts_enabled;
	public boolean two_factor_enabled;
	public boolean biometric_access_enabled;
	public boolean is_verified_provider;
}
