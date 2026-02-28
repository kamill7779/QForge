# Services Overview

## `gateway-service`
- Spring Cloud Gateway entry point for frontend requests.
- Performs JWT validation and centralizes API access concerns.
- Routes `/api/auth/**` to `auth-service` through Nacos discovery.

## `auth-service`
- Username/password login.
- Issues JWT access token.
- Provides protected `/auth/me` for token verification.
