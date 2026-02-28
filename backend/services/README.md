# Services Overview

## `gateway-service`
- Entry point for frontend requests.
- Performs JWT validation and centralizes API access concerns.

## `auth-service`
- Username/password login.
- Issues JWT access token.
- Provides protected `/auth/me` for token verification.
